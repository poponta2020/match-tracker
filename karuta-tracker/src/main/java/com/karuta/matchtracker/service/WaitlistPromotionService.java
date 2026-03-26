package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * キャンセル・繰り上げサービス
 *
 * 当選者のキャンセル→キャンセル待ち1番への通知→承諾/辞退の処理を行う。
 * 当日キャンセルの場合は繰り上げフローを開始しない。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistPromotionService {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final NotificationService notificationService;
    private final LineNotificationService lineNotificationService;

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
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.WON) {
            throw new IllegalStateException("当選者のみキャンセルできます（現在のステータス: " + participant.getStatus() + "）");
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        // ステータスをCANCELLEDに変更
        participant.setStatus(ParticipantStatus.CANCELLED);
        participant.setCancelReason(cancelReason);
        participant.setCancelReasonDetail(cancelReasonDetail);
        participant.setCancelledAt(JstDateTimeUtil.now());
        practiceParticipantRepository.save(participant);

        log.info("Player {} cancelled participation in session {} match {} (reason: {})",
                participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber(), cancelReason);

        // 当日キャンセルかどうかチェック
        if (lotteryDeadlineHelper.isToday(session.getSessionDate())) {
            log.info("Same-day cancel: no waitlist promotion triggered for session {} match {}",
                    session.getId(), participant.getMatchNumber());
            return ParticipantStatus.CANCELLED;
        }

        // 当日でなければ繰り上げフローを開始
        promoteNextWaitlisted(participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

        return ParticipantStatus.CANCELLED;
    }

    /**
     * キャンセル待ちリストから次の人を繰り上げる
     */
    @Transactional
    public void promoteNextWaitlisted(Long sessionId, Integer matchNumber, LocalDate sessionDate) {
        Optional<PracticeParticipant> nextWaitlisted = practiceParticipantRepository
                .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                        sessionId, matchNumber, ParticipantStatus.WAITLISTED);

        if (nextWaitlisted.isEmpty()) {
            log.info("No waitlisted players remaining for session {} match {} - slot remains open",
                    sessionId, matchNumber);
            return;
        }

        PracticeParticipant next = nextWaitlisted.get();

        // OFFEREDに変更し、応答期限を設定
        LocalDateTime deadline = lotteryDeadlineHelper.calculateOfferDeadline(sessionDate);

        next.setStatus(ParticipantStatus.OFFERED);
        next.setOfferedAt(JstDateTimeUtil.now());
        next.setOfferDeadline(deadline);
        practiceParticipantRepository.save(next);

        log.info("Offered waitlist #{} (player {}) for session {} match {}. Deadline: {}",
                next.getWaitlistNumber(), next.getPlayerId(), sessionId, matchNumber, deadline);

        // 通知を送信
        notificationService.createOfferNotification(next);

        // LINE通知を送信
        lineNotificationService.sendWaitlistOfferNotification(next);
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
            log.info("Player {} accepted offer for session {} match {}",
                    participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());
        } else {
            participant.setStatus(ParticipantStatus.DECLINED);
            practiceParticipantRepository.save(participant);

            log.info("Player {} declined offer for session {} match {}",
                    participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());

            // 次のキャンセル待ちに通知
            PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
            promoteNextWaitlisted(participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());
            return;
        }

        practiceParticipantRepository.save(participant);
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

        for (PracticeParticipant p : waitlisted) {
            Integer oldNumber = p.getWaitlistNumber();
            p.setStatus(ParticipantStatus.WAITLIST_DECLINED);
            p.setWaitlistNumber(null);
            practiceParticipantRepository.save(p);

            // 後続のキャンセル待ち番号を繰り上げ
            if (oldNumber != null) {
                List<PracticeParticipant> subsequent = practiceParticipantRepository
                        .findWaitlistedAfterNumber(sessionId, p.getMatchNumber(), oldNumber);
                for (PracticeParticipant s : subsequent) {
                    s.setWaitlistNumber(s.getWaitlistNumber() - 1);
                    practiceParticipantRepository.save(s);
                }
            }

            log.info("Player {} declined waitlist for session {} match {} (was #{})",
                    playerId, sessionId, p.getMatchNumber(), oldNumber);
        }

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
            p.setWaitlistNumber(maxNumber + 1);
            practiceParticipantRepository.save(p);

            log.info("Player {} rejoined waitlist for session {} match {} (new #{})",
                    playerId, sessionId, p.getMatchNumber(), maxNumber + 1);
        }

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
        promoteNextWaitlisted(participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());
    }
}
