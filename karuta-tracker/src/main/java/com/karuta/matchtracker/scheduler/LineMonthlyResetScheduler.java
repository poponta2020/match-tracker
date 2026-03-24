package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.repository.LineChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LINE月間送信数リセットスケジューラ
 *
 * 毎月1日 AM 0:00に実行し、全チャネルの月間送信数を0にリセットする。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LineMonthlyResetScheduler {

    private final LineChannelRepository lineChannelRepository;

    /**
     * 毎月1日 AM 0:00に実行
     */
    @Scheduled(cron = "0 0 0 1 * *")
    @Transactional
    public void resetMonthlyCounts() {
        log.info("Resetting monthly message counts for all LINE channels");
        lineChannelRepository.resetAllMonthlyMessageCounts();
        log.info("Monthly message counts reset completed");
    }
}
