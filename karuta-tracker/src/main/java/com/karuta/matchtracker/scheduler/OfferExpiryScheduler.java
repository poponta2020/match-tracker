package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.service.WaitlistPromotionService;
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

    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 5分ごと、起動1分後に初回実行
    public void checkExpiredOffers() {
        List<PracticeParticipant> expired = practiceParticipantRepository
                .findExpiredOffers(LocalDateTime.now());

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
}
