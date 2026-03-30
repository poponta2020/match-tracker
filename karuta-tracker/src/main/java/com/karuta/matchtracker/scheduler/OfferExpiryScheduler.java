package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

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
    private final WaitlistPromotionService waitlistPromotionService;
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

        for (PracticeParticipant participant : expired) {
            try {
                waitlistPromotionService.expireOffer(participant);
            } catch (Exception e) {
                log.error("Failed to expire offer for participant {}: {}",
                        participant.getId(), e.getMessage(), e);
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
