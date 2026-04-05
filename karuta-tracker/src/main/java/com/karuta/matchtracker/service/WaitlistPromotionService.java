package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * キャンセル・繰り上げサービス
 *
 * 当選者のキャンセル→キャンセル待ち1番への通知→承諾/辞退の処理を行う。
 * 当日キャンセルの場合は繰り上げフローを開始しない。
 */
@Service
@Slf4j
public class WaitlistPromotionService {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PlayerRepository playerRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final NotificationService notificationService;
    private final LineNotificationService lineNotificationService;
    private final DensukeSyncService densukeSyncService;

    public WaitlistPromotionService(
            PracticeParticipantRepository practiceParticipantRepository,
            PracticeSessionRepository practiceSessionRepository,
            PlayerRepository playerRepository,
            LotteryDeadlineHelper lotteryDeadlineHelper,
            NotificationService notificationService,
            LineNotificationService lineNotificationService,
            @Lazy DensukeSyncService densukeSyncService) {
        this.practiceParticipantRepository = practiceParticipantRepository;
        this.practiceSessionRepository = practiceSessionRepository;
        this.playerRepository = playerRepository;
        this.lotteryDeadlineHelper = lotteryDeadlineHelper;
        this.notificationService = notificationService;
        this.lineNotificationService = lineNotificationService;
        this.densukeSyncService = densukeSyncService;
    }

    /**
     * WON → WAITLISTED（最後尾）に降格し、空いたWON枠の繰り上げフローを発動する。
     * 伝助で○→△に変更された場合（3-B2）に使用。
     *
     * @param participantId 参加者レコードID
     */
    @Transactional
    public void demoteToWaitlist(Long participantId) {
        AdminWaitlistNotificationData notifData = demoteToWaitlistInternal(participantId);

        PracticeParticipant participant = practiceParticipantRepository.findById(participantId).orElse(null);
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        sendBatchedAdminWaitlistNotifications(List.of(notifData), session);
        densukeSyncService.triggerWriteAsync();
    }

    /**
     * WON → WAITLISTED（最後尾）に降格する（通知抑制版）。
     * 呼び出し元で複数件分をまとめて sendBatchedAdminWaitlistNotifications に渡す用途。
     *
     * @param participantId 参加者レコードID
     * @return 通知データ
     */
    @Transactional
    public AdminWaitlistNotificationData demoteToWaitlistSuppressed(Long participantId) {
        AdminWaitlistNotificationData notifData = demoteToWaitlistInternal(participantId);
        densukeSyncService.triggerWriteAsync();
        return notifData;
    }

    /**
     * 降格処理の内部実装。通知送信は行わず、通知データを返す。
     */
    private AdminWaitlistNotificationData demoteToWaitlistInternal(Long participantId) {
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.WON) {
            throw new IllegalStateException("WON状態のみ降格できます（現在: " + participant.getStatus() + "）");
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        // 最後尾のキャンセル待ち番号を取得
        int maxNumber = practiceParticipantRepository
                .findMaxWaitlistNumber(participant.getSessionId(), participant.getMatchNumber())
                .orElse(0);

        // WON → WAITLISTED（最後尾）
        participant.setStatus(ParticipantStatus.WAITLISTED);
        participant.setDirty(true);
        participant.setWaitlistNumber(maxNumber + 1);
        practiceParticipantRepository.save(participant);

        log.info("Demoted player {} from WON to WAITLISTED #{} for session {} match {}",
                participant.getPlayerId(), maxNumber + 1, participant.getSessionId(), participant.getMatchNumber());

        // WON枠が空いたので繰り上げフローを発動（降格した本人は除外）
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate(), participant.getPlayerId());

        return AdminWaitlistNotificationData.builder()
                .triggerAction("降格")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.orElse(null))
                .build();
    }

    /**
     * 当選者が参加をキャンセルする（理由なし・後方互換）
     *
     * @param participantId 参加者レコードID
     * @return キャンセル後のステータス
     */
    @Transactional
    public ParticipantStatus cancelParticipation(Long participantId) {
        return cancelParticipation(participantId, null, null);
    }

    /**
     * 当選者が参加をキャンセルする（理由付き）
     *
     * @param participantId    参加者レコードID
     * @param cancelReason     キャンセル理由コード
     * @param cancelReasonDetail キャンセル理由詳細（その他の場合）
     * @return キャンセル後のステータス
     */
    @Transactional
    public ParticipantStatus cancelParticipation(Long participantId, String cancelReason, String cancelReasonDetail) {
        AdminWaitlistNotificationData notifData = cancelParticipationInternal(participantId, cancelReason, cancelReasonDetail);

        if (notifData != null) {
            PracticeSession session = practiceSessionRepository.findById(notifData.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", notifData.getSessionId()));
            sendBatchedAdminWaitlistNotifications(List.of(notifData), session);
        }

        densukeSyncService.triggerWriteAsync();
        return ParticipantStatus.CANCELLED;
    }

    /**
     * 当選者が参加をキャンセルする（通知抑制オプション付き）。
     * suppressNotification=true の場合、管理者通知を送信せず通知データを返す。
     * 呼び出し元で複数件分をまとめて sendBatchedAdminWaitlistNotifications に渡す用途。
     *
     * @param participantId      参加者レコードID
     * @param cancelReason       キャンセル理由コード
     * @param cancelReasonDetail キャンセル理由詳細
     * @param suppressNotification true=通知を送信しない
     * @return 通知データ（通知不要の場合はnull）
     */
    @Transactional
    public AdminWaitlistNotificationData cancelParticipationSuppressed(Long participantId,
                                                                        String cancelReason,
                                                                        String cancelReasonDetail) {
        AdminWaitlistNotificationData notifData = cancelParticipationInternal(participantId, cancelReason, cancelReasonDetail);
        densukeSyncService.triggerWriteAsync();
        return notifData;
    }

    /**
     * キャンセル処理の内部実装。通知送信は行わず、通知データを返す。
     */
    private AdminWaitlistNotificationData cancelParticipationInternal(Long participantId,
                                                                       String cancelReason,
                                                                       String cancelReasonDetail) {
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.WON) {
            throw new IllegalStateException("当選者のみキャンセルできます（現在のステータス: " + participant.getStatus() + "）");
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        // ステータスをCANCELLEDに変更
        participant.setStatus(ParticipantStatus.CANCELLED);
        participant.setDirty(true);
        participant.setCancelReason(cancelReason);
        participant.setCancelReasonDetail(cancelReasonDetail);
        participant.setCancelledAt(JstDateTimeUtil.now());
        practiceParticipantRepository.save(participant);

        log.info("Player {} cancelled participation in session {} match {} (reason: {})",
                participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber(), cancelReason);

        // 当日12:00以降のキャンセル → 新フロー（全体募集＋先着ボタン方式）
        if (lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())) {
            log.info("Same-day after-noon cancel: triggering vacancy recruitment for session {} match {}",
                    session.getId(), participant.getMatchNumber());

            handleSameDayCancelAndRecruit(participant, session);
            return null; // 当日補充は独自の通知フローを持つ
        }

        // 当日12:00より前のキャンセル → 従来通りwaitlistNumberに基づく繰り上げフロー
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

        // 定員未達（WAITLISTEDなし）の場合は通知不要
        if (promoted.isEmpty()) {
            return null;
        }

        return AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.get())
                .build();
    }

    /**
     * 空き募集ボタンへの応答を処理する。
     * 先着1名のみWONに変更。2人目以降はエラー。練習開始時間を過ぎていたら無効。
     */
    @Transactional
    public synchronized void handleSameDayJoin(Long sessionId, int matchNumber, Long playerId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("セッションが見つかりません"));

        // 練習開始時間チェック
        if (session.getStartTime() != null) {
            LocalDateTime practiceStart = session.getSessionDate().atTime(session.getStartTime());
            if (JstDateTimeUtil.now().isAfter(practiceStart)) {
                throw new IllegalStateException("練習開始時間を過ぎているため、参加登録できません。");
            }
        }

        // この試合で既にWONかどうかチェック
        List<PracticeParticipant> existingWon = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber);
        if (existingWon.stream().anyMatch(p -> p.getStatus() == ParticipantStatus.WON)) {
            throw new IllegalStateException("既にこの試合に参加登録済みです。");
        }

        // 空き枠チェック
        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        List<PracticeParticipant> currentWon = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatus(sessionId, matchNumber, ParticipantStatus.WON);
        if (currentWon.size() >= capacity) {
            throw new IllegalStateException("定員に達してしまいました...");
        }

        // 既存レコードがあればステータス更新、なければ新規作成
        Optional<PracticeParticipant> existing = existingWon.stream()
                .filter(p -> p.getStatus() != ParticipantStatus.WON)
                .findFirst();

        PracticeParticipant participant;
        if (existing.isPresent()) {
            participant = existing.get();
            participant.setStatus(ParticipantStatus.WON);
            participant.setDirty(true);
        } else {
            participant = PracticeParticipant.builder()
                    .sessionId(sessionId)
                    .playerId(playerId)
                    .matchNumber(matchNumber)
                    .status(ParticipantStatus.WON)
                    .dirty(true)
                    .build();
        }
        practiceParticipantRepository.save(participant);

        Player joinedPlayer = playerRepository.findById(playerId).orElse(null);
        String playerName = joinedPlayer != null ? joinedPlayer.getName() : "不明";

        log.info("Same-day join: player {} ({}) joined session {} match {}",
                playerId, playerName, sessionId, matchNumber);

        // 参加通知 + 枠状況通知
        lineNotificationService.sendSameDayJoinNotification(session, matchNumber, playerName, playerId);
        lineNotificationService.sendSameDayVacancyUpdateNotification(session, matchNumber, playerName, playerId);

        densukeSyncService.triggerWriteAsync();
    }

    /**
     * 当日12:00以降のキャンセル時に、キャンセル通知＋空き募集通知を送信する。
     */
    private void handleSameDayCancelAndRecruit(PracticeParticipant cancelledParticipant, PracticeSession session) {
        Player cancelledPlayer = playerRepository.findById(cancelledParticipant.getPlayerId()).orElse(null);
        String playerName = cancelledPlayer != null ? cancelledPlayer.getName() : "不明";

        // 1. キャンセル通知 → 当該セッションの全WON参加者（キャンセル本人除く）
        lineNotificationService.sendSameDayCancelNotification(
                session, cancelledParticipant.getMatchNumber(), playerName, cancelledParticipant.getPlayerId());

        // 2. 空き募集通知 → 当該セッションの非WON参加者（キャンセル本人除く）
        lineNotificationService.sendSameDayVacancyNotification(
                session, cancelledParticipant.getMatchNumber(), cancelledParticipant.getPlayerId());

        // 3. 管理者通知
        AdminWaitlistNotificationData notifData = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル（当日補充）")
                .triggerPlayerId(cancelledParticipant.getPlayerId())
                .sessionId(cancelledParticipant.getSessionId())
                .matchNumber(cancelledParticipant.getMatchNumber())
                .promotedParticipant(null)
                .build();
        sendBatchedAdminWaitlistNotifications(List.of(notifData), session);

        densukeSyncService.triggerWriteAsync();
    }

    /**
     * キャンセル待ちリストから次の人を繰り上げる
     *
     * @return 繰り上げた参加者（繰り上げ対象なしの場合はempty）
     */
    @Transactional
    public Optional<PracticeParticipant> promoteNextWaitlisted(Long sessionId, Integer matchNumber, LocalDate sessionDate) {
        return promoteNextWaitlisted(sessionId, matchNumber, sessionDate, null);
    }

    /**
     * キャンセル待ちリストから次の人を繰り上げる（特定プレイヤーを除外可能）
     *
     * @param excludePlayerId 除外するプレイヤーID（nullなら除外なし）
     * @return 繰り上げた参加者（繰り上げ対象なしの場合はempty）
     */
    @Transactional
    public Optional<PracticeParticipant> promoteNextWaitlisted(Long sessionId, Integer matchNumber, LocalDate sessionDate, Long excludePlayerId) {
        Optional<PracticeParticipant> nextWaitlisted = excludePlayerId != null
                ? practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusAndPlayerIdNotOrderByWaitlistNumberAsc(
                            sessionId, matchNumber, ParticipantStatus.WAITLISTED, excludePlayerId)
                : practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            sessionId, matchNumber, ParticipantStatus.WAITLISTED);

        if (nextWaitlisted.isEmpty()) {
            log.info("No waitlisted players remaining for session {} match {} - slot remains open",
                    sessionId, matchNumber);
            return Optional.empty();
        }

        PracticeParticipant next = nextWaitlisted.get();

        // OFFEREDに変更し、応答期限を設定
        LocalDateTime deadline = lotteryDeadlineHelper.calculateOfferDeadline(sessionDate);

        Integer oldWaitlistNumber = next.getWaitlistNumber();

        next.setStatus(ParticipantStatus.OFFERED);
        next.setDirty(true);
        next.setOfferedAt(JstDateTimeUtil.now());
        next.setOfferDeadline(deadline);
        practiceParticipantRepository.save(next);

        // 後続のキャンセル待ち番号を一括繰り上げ
        if (oldWaitlistNumber != null) {
            practiceParticipantRepository.decrementWaitlistNumbersAfter(sessionId, matchNumber, oldWaitlistNumber);
        }

        log.info("Offered waitlist #{} (player {}) for session {} match {}. Deadline: {}",
                oldWaitlistNumber, next.getPlayerId(), sessionId, matchNumber, deadline);

        // 通知を送信
        notificationService.createOfferNotification(next);

        // LINE通知を送信
        lineNotificationService.sendWaitlistOfferNotification(next);

        return Optional.of(next);
    }

    /**
     * 繰り上げオファーに対する応答を処理する
     *
     * @param participantId 参加者レコードID
     * @param accept        true=参加する, false=参加しない
     */
    @Transactional
    public void respondToOffer(Long participantId, boolean accept) {
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            throw new IllegalStateException("OFFERED状態のみ応答できます（現在: " + participant.getStatus() + "）");
        }

        // 応答期限の超過チェック
        if (participant.getOfferDeadline() != null
                && JstDateTimeUtil.now().isAfter(participant.getOfferDeadline())) {
            throw new IllegalStateException("応答期限が過ぎています");
        }

        participant.setRespondedAt(JstDateTimeUtil.now());

        if (accept) {
            participant.setStatus(ParticipantStatus.WON);
            participant.setDirty(true);
            log.info("Player {} accepted offer for session {} match {}",
                    participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());
        } else {
            participant.setStatus(ParticipantStatus.DECLINED);
            participant.setDirty(true);
            practiceParticipantRepository.save(participant);

            log.info("Player {} declined offer for session {} match {}",
                    participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());

            // 次のキャンセル待ちに通知
            PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
            Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                    participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

            // 管理者通知
            AdminWaitlistNotificationData notifData = AdminWaitlistNotificationData.builder()
                    .triggerAction("オファー辞退")
                    .triggerPlayerId(participant.getPlayerId())
                    .sessionId(participant.getSessionId())
                    .matchNumber(participant.getMatchNumber())
                    .promotedParticipant(promoted.orElse(null))
                    .build();
            sendBatchedAdminWaitlistNotifications(List.of(notifData), session);

            densukeSyncService.triggerWriteAsync();
            return;
        }

        practiceParticipantRepository.save(participant);
        densukeSyncService.triggerWriteAsync();
    }

    /**
     * セッション単位でキャンセル待ちを辞退する
     *
     * @param sessionId セッションID
     * @param playerId  プレイヤーID
     * @return 辞退した件数
     */
    @Transactional
    public int declineWaitlistBySession(Long sessionId, Long playerId) {
        List<PracticeParticipant> waitlisted = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndStatus(sessionId, playerId, ParticipantStatus.WAITLISTED);

        if (waitlisted.isEmpty()) {
            throw new IllegalStateException("辞退対象のキャンセル待ちがありません");
        }

        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        // 通知データを蓄積
        List<AdminWaitlistNotificationData> notificationDataList = new ArrayList<>();

        for (PracticeParticipant p : waitlisted) {
            Integer oldNumber = p.getWaitlistNumber();
            p.setStatus(ParticipantStatus.WAITLIST_DECLINED);
            p.setDirty(true);
            p.setWaitlistNumber(null);
            practiceParticipantRepository.save(p);

            // 後続のキャンセル待ち番号を一括繰り上げ
            if (oldNumber != null) {
                practiceParticipantRepository.decrementWaitlistNumbersAfter(sessionId, p.getMatchNumber(), oldNumber);
            }

            notificationDataList.add(AdminWaitlistNotificationData.builder()
                    .triggerAction("キャンセル待ち辞退")
                    .triggerPlayerId(playerId)
                    .sessionId(sessionId)
                    .matchNumber(p.getMatchNumber())
                    .promotedParticipant(null)
                    .build());

            log.info("Player {} declined waitlist for session {} match {} (was #{})",
                    playerId, sessionId, p.getMatchNumber(), oldNumber);
        }

        // まとめて通知送信
        sendBatchedAdminWaitlistNotifications(notificationDataList, session);

        densukeSyncService.triggerWriteAsync();
        return waitlisted.size();
    }

    /**
     * セッション単位でキャンセル待ちに復帰する
     *
     * @param sessionId セッションID
     * @param playerId  プレイヤーID
     * @return 復帰した件数
     */
    @Transactional
    public int rejoinWaitlistBySession(Long sessionId, Long playerId) {
        List<PracticeParticipant> declined = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndStatus(sessionId, playerId, ParticipantStatus.WAITLIST_DECLINED);

        if (declined.isEmpty()) {
            throw new IllegalStateException("復帰対象がありません（WAITLIST_DECLINED状態のものがない）");
        }

        for (PracticeParticipant p : declined) {
            // 該当試合の最後尾番号を取得
            int maxNumber = practiceParticipantRepository
                    .findMaxWaitlistNumber(sessionId, p.getMatchNumber())
                    .orElse(0);

            p.setStatus(ParticipantStatus.WAITLISTED);
            p.setDirty(true);
            p.setWaitlistNumber(maxNumber + 1);
            practiceParticipantRepository.save(p);

            log.info("Player {} rejoined waitlist for session {} match {} (new #{})",
                    playerId, sessionId, p.getMatchNumber(), maxNumber + 1);
        }

        densukeSyncService.triggerWriteAsync();

        return declined.size();
    }

    /**
     * 期限切れのOFFERを自動的にDECLINEDにし、次の人に繰り上げる
     * （OfferExpirySchedulerから呼ばれる）
     */
    @Transactional
    public void expireOffer(PracticeParticipant participant) {
        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            return;
        }

        participant.setStatus(ParticipantStatus.DECLINED);
        participant.setDirty(true);
        participant.setRespondedAt(JstDateTimeUtil.now());
        practiceParticipantRepository.save(participant);

        log.info("Offer expired for player {} in session {} match {} (waitlist #{})",
                participant.getPlayerId(), participant.getSessionId(),
                participant.getMatchNumber(), participant.getWaitlistNumber());

        notificationService.createOfferExpiredNotification(participant);

        // LINE通知を送信
        lineNotificationService.sendOfferExpiredNotification(participant);

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

        // 管理者通知
        AdminWaitlistNotificationData notifData = AdminWaitlistNotificationData.builder()
                .triggerAction("オファー期限切れ")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.orElse(null))
                .build();
        sendBatchedAdminWaitlistNotifications(List.of(notifData), session);
    }

    /**
     * 当日12:00確定時に、OFFERED状態の参加者を期限切れ処理する。
     * 通常の expireOffer と異なり、promoteNextWaitlisted ではなく
     * 当日空き募集フロー（先着ボタン方式）を使用する。
     * SameDayConfirmationScheduler から呼ばれる。
     */
    @Transactional
    public void expireOfferedForSameDayConfirmation(PracticeSession session) {
        List<PracticeParticipant> offered = practiceParticipantRepository
                .findBySessionIdAndStatus(session.getId(), ParticipantStatus.OFFERED);

        if (offered.isEmpty()) {
            log.debug("No OFFERED participants for session {}", session.getId());
            return;
        }

        log.info("Expiring {} OFFERED participants for same-day confirmation (session {})",
                offered.size(), session.getId());

        Set<Integer> affectedMatches = new LinkedHashSet<>();

        for (PracticeParticipant participant : offered) {
            participant.setStatus(ParticipantStatus.DECLINED);
            participant.setDirty(true);
            participant.setRespondedAt(JstDateTimeUtil.now());
            practiceParticipantRepository.save(participant);

            notificationService.createOfferExpiredNotification(participant);
            lineNotificationService.sendOfferExpiredNotification(participant);

            affectedMatches.add(participant.getMatchNumber());

            log.info("Expired offer for player {} session {} match {}",
                    participant.getPlayerId(), session.getId(), participant.getMatchNumber());
        }

        // 空き枠がある試合に対して当日空き募集フローを発動
        for (Integer matchNumber : affectedMatches) {
            long wonCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                    session.getId(), matchNumber, ParticipantStatus.WON);
            int capacity = session.getCapacity() != null ? session.getCapacity() : 0;

            if (wonCount < capacity) {
                lineNotificationService.sendSameDayVacancyNotification(
                        session, matchNumber, null);

                log.info("Triggered same-day vacancy recruitment for session {} match {} ({} vacancies)",
                        session.getId(), matchNumber, capacity - wonCount);
            }
        }

        densukeSyncService.triggerWriteAsync();
    }

    /**
     * 複数試合分のキャンセル待ち状況通知をまとめて送信する。
     * 同一セッション×トリガー×プレイヤーの通知データをリストで受け取り、
     * 試合ごとのキャンセル待ち列を収集して1通のFlexメッセージとして送信する。
     *
     * @param notificationDataList 通知データのリスト（同一セッション・トリガー・プレイヤー）
     * @param session              対象セッション
     */
    public void sendBatchedAdminWaitlistNotifications(List<AdminWaitlistNotificationData> notificationDataList,
                                                       PracticeSession session) {
        if (notificationDataList.isEmpty()) return;

        try {
            AdminWaitlistNotificationData first = notificationDataList.get(0);
            Player triggerPlayer = playerRepository.findById(first.getTriggerPlayerId()).orElse(null);
            if (triggerPlayer == null) return;

            // 各試合の情報を収集
            List<Integer> matchNumbers = new ArrayList<>();
            for (AdminWaitlistNotificationData data : notificationDataList) {
                matchNumbers.add(data.getMatchNumber());
            }

            // 暫定実装: 試合ごとにループして旧シグネチャのメソッドを呼ぶ
            // タスク4でLineNotificationServiceのシグネチャ変更後にバッチ送信に置き換える
            for (AdminWaitlistNotificationData data : notificationDataList) {
                Player offeredPlayer = null;
                if (data.getPromotedParticipant() != null) {
                    offeredPlayer = playerRepository.findById(data.getPromotedParticipant().getPlayerId()).orElse(null);
                }

                List<PracticeParticipant> remainingWaitlist = practiceParticipantRepository
                        .findBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                                session.getId(), data.getMatchNumber(), ParticipantStatus.WAITLISTED);

                lineNotificationService.sendAdminWaitlistNotification(
                        data.getTriggerAction(), triggerPlayer, session, data.getMatchNumber(),
                        offeredPlayer, remainingWaitlist);

                lineNotificationService.sendWaitlistPositionUpdateNotifications(
                        data.getTriggerAction(), triggerPlayer, session, data.getMatchNumber(),
                        offeredPlayer, remainingWaitlist);
            }
        } catch (Exception e) {
            log.error("Failed to send batched waitlist change notifications: {}", e.getMessage(), e);
        }
    }
}
