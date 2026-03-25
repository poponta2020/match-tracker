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
 * 毎月1日AM0:00に実行し、全チャネルの月間送信数をリセットする。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineMonthlyResetScheduler {

    private final LineChannelRepository lineChannelRepository;

    @Scheduled(cron = "0 0 0 1 * *") // 毎月1日AM0:00
    @Transactional
    public void resetMonthlyMessageCounts() {
        log.info("Resetting monthly message counts for all LINE channels");
        lineChannelRepository.resetAllMonthlyMessageCounts();
        log.info("Monthly message count reset completed");
    }
}
