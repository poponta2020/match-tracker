package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineChatReservation;
import com.karuta.matchtracker.entity.LineChatReservation.ReservationStatus;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.repository.LineChatReservationRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LINEチャット予約キューの生成・リコンサイルを担うサービス（line-chat-reserve-broadcast タスク2）。
 *
 * <p>{@link LineChatReservationScheduler} から呼ばれ、決定論テストのため {@code today}/{@code now} を引数で受ける。
 *
 * <ul>
 *   <li>{@link #generateReservations}: 前日20:00バッチ。有効グループ×翌日の当該団体セッションについて
 *       本文（{@link CardDivisionTextService#buildTextForSession}）＋送信予定時刻
 *       （{@link CardDivisionScheduleResolver#resolveScheduledSendAt}）で PENDING を冪等生成する。</li>
 *   <li>{@link #reconcile}: 15分毎。RESERVING 滞留の昇格、セッション削除/内容変更の検知（取消→再予約に正規化）、
 *       取消後の未来セッションの再作成を行う。</li>
 * </ul>
 *
 * <p><b>補完チェックは設けない</b>（要件）。予約が存在しないセッションは既存のフォールバックpush経路が拾う。
 * 再作成は「取消（CANCELLED）行があるのに active 行が無い」＝取消→再予約のケースに限定する
 * （20:00バッチの先食い・未予約セッションの backfill を避ける）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineChatReservationService {

    private final LineChatReservationRepository reservationRepository;
    private final LineBroadcastGroupRepository groupRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final CardDivisionTextService cardDivisionTextService;
    private final CardDivisionScheduleResolver scheduleResolver;
    private final LineNotificationService lineNotificationService;

    /** RESERVING がこの分数を超えて滞留したら MANUAL_REVIEW_REQUIRED へ昇格する。 */
    static final int RESERVING_STALE_MINUTES = 30;

    /**
     * 予約を登録／再試行してよい安全マージン（送信予定時刻の何分前までなら間に合うか）。
     * 管理画面の手動再試行（タスク5）と同値にすること。
     */
    public static final int RESERVE_MARGIN_MINUTES = 30;

    // ------------------------------------------------------------------
    // 20:00 バッチ
    // ------------------------------------------------------------------

    /**
     * {@code targetDate}（＝翌日）の当該団体セッションについて PENDING 予約を冪等生成する。
     * @return 新規作成した予約数（既存 active 行がある分は 0）
     */
    @Transactional
    public int generateReservations(LocalDate targetDate, LocalDateTime now) {
        List<LineBroadcastGroup> groups = groupRepository.findByEnabledTrue();
        if (groups.isEmpty()) {
            return 0;
        }
        List<PracticeSession> sessions = practiceSessionRepository.findAllBySessionDate(targetDate);
        if (sessions.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (LineBroadcastGroup group : groups) {
            for (PracticeSession session : sessions) {
                if (!group.getOrganizationId().equals(session.getOrganizationId())) {
                    continue;
                }
                String text = cardDivisionTextService.buildTextForSession(session);
                LocalDateTime sendAt = scheduleResolver.resolveScheduledSendAt(session);
                created += reservationRepository.tryInsertPendingReservation(
                        group.getId(), session.getId(), text, sendAt, now);
            }
        }
        if (created > 0) {
            log.info("Chat reservation batch created {} reservations for {}", created, targetDate);
        }
        return created;
    }

    // ------------------------------------------------------------------
    // 15分毎リコンサイル
    // ------------------------------------------------------------------

    @Transactional
    public void reconcile(LocalDate today, LocalDateTime now) {
        promoteStaleReserving(now);
        detectChanges(now);
        recreateAfterCancellation(today, now);
    }

    /** RESERVING が滞留した予約を MANUAL_REVIEW_REQUIRED へ昇格し、管理者へアラートする。 */
    void promoteStaleReserving(LocalDateTime now) {
        LocalDateTime cutoff = now.minusMinutes(RESERVING_STALE_MINUTES);
        for (LineChatReservation r : reservationRepository.findByStatusAndUpdatedAtBefore(
                ReservationStatus.RESERVING, cutoff)) {
            r.setStatus(ReservationStatus.MANUAL_REVIEW_REQUIRED);
            r.setErrorCode("RESERVING_TIMEOUT");
            r.setErrorMessage("RESERVING が" + RESERVING_STALE_MINUTES + "分を超えて滞留したため要確認へ昇格");
            reservationRepository.save(r);
            alert(r.getBroadcastGroupId(),
                    "[チャット予約] 処理中のまま滞留したため要確認にしました（session=" + r.getSessionId() + "）");
        }
    }

    /**
     * 未来の active 予約について、セッション削除・団体不一致・内容変更を検知して正規化する。
     * 過去分（送信済み）は触らない。編集は「取消→再予約」に正規化する。
     */
    void detectChanges(LocalDateTime now) {
        for (LineChatReservation r : reservationRepository.findByStatusNotAndScheduledSendAtAfter(
                ReservationStatus.CANCELLED, now)) {
            ReservationStatus st = r.getStatus();
            // 人手・処理中・取消中・dry-run は自動で触らない
            if (st != ReservationStatus.PENDING && st != ReservationStatus.RESERVED
                    && st != ReservationStatus.FAILED) {
                continue;
            }

            Optional<PracticeSession> sessionOpt = practiceSessionRepository.findById(r.getSessionId());
            Optional<LineBroadcastGroup> groupOpt = groupRepository.findById(r.getBroadcastGroupId());
            // グループが無効化(enabled=false)された場合も配信対象外＝予約を残さない。
            // 無効化後もチャット予約はLINE側に残り予定送信され得るため（push経路と異なり disabled でも発火する）取り消す。
            boolean groupActive = groupOpt.isPresent() && Boolean.TRUE.equals(groupOpt.get().getEnabled());
            boolean valid = sessionOpt.isPresent() && groupActive
                    && groupOpt.get().getOrganizationId().equals(sessionOpt.get().getOrganizationId());

            if (!valid) {
                // セッション削除・グループ消失/無効化・団体不一致 → 取消
                if (st == ReservationStatus.RESERVED) {
                    // LINE側に予約が残っている可能性 → ワーカーに削除させる
                    r.setStatus(ReservationStatus.CANCEL_PENDING);
                } else {
                    // PENDING/FAILED は LINE側に何もない → 直接取消
                    r.setStatus(ReservationStatus.CANCELLED);
                }
                r.setErrorCode(sessionOpt.isEmpty() ? "SESSION_REMOVED"
                        : (!groupActive ? "GROUP_INACTIVE" : "ORG_MISMATCH"));
                r.setErrorMessage("対象セッション削除・配信グループ無効化・団体不一致のため取消");
                reservationRepository.save(r);
                continue;
            }

            PracticeSession session = sessionOpt.get();
            String newText = cardDivisionTextService.buildTextForSession(session);
            LocalDateTime newSendAt = scheduleResolver.resolveScheduledSendAt(session);
            boolean changed = !newText.equals(r.getMessageText())
                    || !newSendAt.equals(r.getScheduledSendAt());
            if (!changed) {
                continue;
            }

            if (st == ReservationStatus.RESERVED) {
                // 既にLINE側へ登録済み → 取消（ワーカーが削除）→ 再作成が新内容で予約し直す
                r.setStatus(ReservationStatus.CANCEL_PENDING);
                r.setErrorCode("CONTENT_CHANGED");
                r.setErrorMessage("本文/送信予定時刻が変更されたため取消→再予約");
            } else {
                // PENDING/FAILED はまだLINE側に無い → その場で内容を更新
                r.setMessageText(newText);
                r.setScheduledSendAt(newSendAt);
            }
            reservationRepository.save(r);
        }
    }

    /**
     * 取消（CANCELLED）行があるのに active 行が無い (グループ, セッション) について、
     * セッションが存命かつ送信まで余裕があれば PENDING を再作成する（取消→再予約）。
     * near horizon（今日・明日）のセッションに限定する。
     */
    void recreateAfterCancellation(LocalDate today, LocalDateTime now) {
        List<LineBroadcastGroup> groups = groupRepository.findByEnabledTrue();
        if (groups.isEmpty()) {
            return;
        }
        for (LocalDate date : List.of(today, today.plusDays(1))) {
            List<PracticeSession> sessions = practiceSessionRepository.findAllBySessionDate(date);
            for (LineBroadcastGroup group : groups) {
                for (PracticeSession session : sessions) {
                    if (!group.getOrganizationId().equals(session.getOrganizationId())) {
                        continue;
                    }
                    Long groupId = group.getId();
                    Long sessionId = session.getId();
                    // active 行があるなら再作成不要
                    if (reservationRepository.findFirstByBroadcastGroupIdAndSessionIdAndStatusNot(
                            groupId, sessionId, ReservationStatus.CANCELLED).isPresent()) {
                        continue;
                    }
                    // 取消履歴が無い＝一度も作られていない → 再作成しない（フォールバックpushが拾う）
                    if (!reservationRepository.existsByBroadcastGroupIdAndSessionIdAndStatus(
                            groupId, sessionId, ReservationStatus.CANCELLED)) {
                        continue;
                    }
                    LocalDateTime sendAt = scheduleResolver.resolveScheduledSendAt(session);
                    if (now.isBefore(sendAt.minusMinutes(RESERVE_MARGIN_MINUTES))) {
                        String text = cardDivisionTextService.buildTextForSession(session);
                        reservationRepository.tryInsertPendingReservation(groupId, sessionId, text, sendAt, now);
                        log.info("Chat reservation recreated after cancellation: group={}, session={}",
                                groupId, sessionId);
                    }
                }
            }
        }
    }

    private void alert(Long groupId, String message) {
        Long orgId = groupRepository.findById(groupId)
                .map(LineBroadcastGroup::getOrganizationId)
                .orElse(null);
        if (orgId != null) {
            lineNotificationService.sendChatReserveAlert(orgId, message);
        }
    }

    // ------------------------------------------------------------------
    // ワーカーAPI（タスク3）
    // ------------------------------------------------------------------

    /** ワーカーが処理すべき予約（PENDING・CANCEL_PENDING）を送信予定の昇順で返す。 */
    @Transactional(readOnly = true)
    public List<LineChatReservation> getWorkerTasks() {
        return reservationRepository.findByStatusInOrderByScheduledSendAtAsc(
                List.of(ReservationStatus.PENDING, ReservationStatus.CANCEL_PENDING));
    }

    /**
     * ワーカーの結果報告を適用する。遷移を検証し、不正なら {@code 409 Conflict}、対象が無ければ {@code 404}。
     * RESERVING（＝処理開始の宣言）で試行回数を加算する。
     */
    @Transactional
    public LineChatReservation applyWorkerResult(Long id, ReservationStatus newStatus,
                                                 String errorCode, String errorMessage) {
        LineChatReservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "予約が見つかりません"));
        if (!isValidWorkerTransition(r.getStatus(), newStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "不正な状態遷移: " + r.getStatus() + " -> " + newStatus);
        }
        if (newStatus == ReservationStatus.RESERVING) {
            r.setAttemptCount(r.getAttemptCount() + 1);
        }
        r.setStatus(newStatus);
        r.setErrorCode(errorCode);
        r.setErrorMessage(errorMessage);
        return reservationRepository.save(r);
    }

    /**
     * ワーカーが起こしてよい状態遷移か（純関数・AC-3）。
     * <pre>
     *   PENDING       → RESERVING
     *   RESERVING     → RESERVED | FAILED | MANUAL_REVIEW_REQUIRED | DRY_RUN_SUCCEEDED
     *   CANCEL_PENDING→ CANCELLED | MANUAL_REVIEW_REQUIRED
     * </pre>
     * それ以外（RESERVED/FAILED/…からの遷移、reserved→reserving 等の逆行）は不可。
     * FAILED→PENDING は管理画面の手動再試行（タスク5）でのみ許可され、ワーカー経路では起こさない。
     */
    static boolean isValidWorkerTransition(ReservationStatus from, ReservationStatus to) {
        return switch (from) {
            case PENDING -> to == ReservationStatus.RESERVING;
            case RESERVING -> to == ReservationStatus.RESERVED
                    || to == ReservationStatus.FAILED
                    || to == ReservationStatus.MANUAL_REVIEW_REQUIRED
                    || to == ReservationStatus.DRY_RUN_SUCCEEDED;
            case CANCEL_PENDING -> to == ReservationStatus.CANCELLED
                    || to == ReservationStatus.MANUAL_REVIEW_REQUIRED;
            default -> false;
        };
    }
}
