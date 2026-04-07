package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.dto.ExpireOfferResult;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 繰り上げオファー期限切れチェックスケジューラ
 *
 * 5分ごとに実行し、応答期限が切れたOFFERED状態のレコードを
 * 自動的にDECLINEDに変更し、次のキャンセル待ちに通知する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfferExpiryScheduler {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final WaitlistPromotionService waitlistPromotionService;
    private final LineNotificationService lineNotificationService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    private static final int EXPIRING_WARNING_HOURS = 3;

    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 5分ごと、起動1分後に初回実行
    public void checkExpiredOffers() {
        LocalDateTime now = JstDateTimeUtil.now();

        // 1. 期限間近のオファーに警告通知を送信（期限3時間以内）
        warnExpiringOffers(now);

        // 2. 期限切れのオファーを処理
        List<PracticeParticipant> expired = practiceParticipantRepository
                .findExpiredOffers(now);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} expired offers to process", expired.size());

        // 通知データを蓄積
        List<ExpireOfferResult> results = new ArrayList<>();

        for (PracticeParticipant participant : expired) {
            try {
                ExpireOfferResult result = waitlistPromotionService.expireOfferSuppressed(participant);
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.error("Failed to expire offer for participant {}: {}",
                        participant.getId(), e.getMessage(), e);
            }
        }

        // 繰り上げ先プレイヤーへの統合LINE通知（セッション×プレイヤーでグルーピング）
        sendConsolidatedOfferNotifications(results);

        // 管理者通知（セッション単位でバッチ送信）
        sendBatchedAdminNotifications(results);
    }

    /**
     * 繰り上げ先プレイヤーへのLINE通知をセッション×プレイヤーでグルーピングして統合送信する。
     */
    private void sendConsolidatedOfferNotifications(List<ExpireOfferResult> results) {
        // promotedParticipant があるもののみ抽出し、セッション×プレイヤーでグルーピング
        Map<String, List<PracticeParticipant>> promotedBySessionPlayer = new LinkedHashMap<>();
        Map<String, Long> triggerPlayerByKey = new LinkedHashMap<>();

        for (ExpireOfferResult result : results) {
            if (result.getPromotedParticipant() == null) continue;

            PracticeParticipant promoted = result.getPromotedParticipant();
            String key = promoted.getSessionId() + ":" + promoted.getPlayerId();
            promotedBySessionPlayer.computeIfAbsent(key, k -> new ArrayList<>()).add(promoted);
            triggerPlayerByKey.putIfAbsent(key, result.getNotificationData().getTriggerPlayerId());
        }

        for (Map.Entry<String, List<PracticeParticipant>> entry : promotedBySessionPlayer.entrySet()) {
            List<PracticeParticipant> offeredList = entry.getValue();
            Long triggerPlayerId = triggerPlayerByKey.get(entry.getKey());
            try {
                PracticeSession session = practiceSessionRepository
                        .findById(offeredList.get(0).getSessionId()).orElse(null);
                if (session == null) continue;

                lineNotificationService.sendConsolidatedWaitlistOfferNotification(
                        offeredList, session, "オファー期限切れ", triggerPlayerId);
            } catch (Exception e) {
                log.error("Failed to send consolidated offer notification: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * 管理者通知をセッション単位でグルーピングしてバッチ送信する。
     */
    private void sendBatchedAdminNotifications(List<ExpireOfferResult> results) {
        // セッションIDでグルーピング
        Map<Long, List<AdminWaitlistNotificationData>> bySession = results.stream()
                .map(ExpireOfferResult::getNotificationData)
                .collect(Collectors.groupingBy(
                        AdminWaitlistNotificationData::getSessionId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<Long, List<AdminWaitlistNotificationData>> entry : bySession.entrySet()) {
            try {
                PracticeSession session = practiceSessionRepository
                        .findById(entry.getKey()).orElse(null);
                if (session == null) continue;

                waitlistPromotionService.sendBatchedAdminWaitlistNotifications(
                        entry.getValue(), session);
            } catch (Exception e) {
                log.error("Failed to send batched admin notification for session {}: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
    }

    /**
     * 応答期限が間近のOFFEREDに警告通知を送信する
     */
    private void warnExpiringOffers(LocalDateTime now) {
        LocalDateTime warningThreshold = now.plusHours(EXPIRING_WARNING_HOURS);
        List<PracticeParticipant> expiring = practiceParticipantRepository
                .findExpiringOffers(now, warningThreshold);

        int warned = 0;
        for (PracticeParticipant participant : expiring) {
            try {
                // 同一参加者への重複送信を防止
                if (notificationRepository.existsByReferenceIdAndType(
                        participant.getId(), NotificationType.OFFER_EXPIRING)) {
                    continue;
                }
                notificationService.createOfferExpiringNotification(participant);
                warned++;
            } catch (Exception e) {
                log.error("Failed to send expiring warning for participant {}: {}",
                        participant.getId(), e.getMessage(), e);
            }
        }
        if (warned > 0) {
            log.info("Sent {} offer expiring warnings", warned);
        }
    }
}
