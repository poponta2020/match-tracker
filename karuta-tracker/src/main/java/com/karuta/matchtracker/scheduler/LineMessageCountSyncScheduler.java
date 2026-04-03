package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.service.LineMessagingService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LINE月間送信数同期スケジューラ
 *
 * 1時間ごとにLINE APIから実際の送信数を取得し、DBを更新する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineMessageCountSyncScheduler {

    private static final long DELAY_BETWEEN_REQUESTS_MS = 100;
    private static final int REQUEST_TIMEOUT_FAILURE = -1;

    private final LineChannelRepository lineChannelRepository;
    private final LineMessagingService lineMessagingService;

    @Scheduled(cron = "0 30 * * * *") // 毎時30分
    public void syncMessageCounts() {
        List<LineChannel> channels = lineChannelRepository.findAll();
        log.info("Starting LINE message count sync for {} channels", channels.size());

        int successCount = 0;
        int failCount = 0;
        LocalDateTime now = JstDateTimeUtil.now();

        for (LineChannel channel : channels) {
            try {
                int usage = lineMessagingService.getMonthlyMessageConsumption(channel.getChannelAccessToken());

                if (usage != REQUEST_TIMEOUT_FAILURE) {
                    channel.setMonthlyMessageCount(usage);
                    channel.setMessageCountResetAt(now);
                    lineChannelRepository.save(channel);
                    successCount++;
                } else {
                    failCount++;
                    resetIfMonthChanged(channel, now);
                    log.warn("Failed to get message count for channel {} (id={})",
                        channel.getChannelName(), channel.getId());
                }

                Thread.sleep(DELAY_BETWEEN_REQUESTS_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Message count sync interrupted");
                break;
            } catch (Exception e) {
                failCount++;
                log.error("Error syncing message count for channel {} (id={}): {}",
                    channel.getChannelName(), channel.getId(), e.getMessage());
            }
        }

        log.info("LINE message count sync completed: success={}, failed={}", successCount, failCount);
    }

    /**
     * API同期失敗時、前回同期が前月以前であればカウントを0にリセットする。
     * 月跨ぎで同期が連続失敗した場合に、前月のカウントで送信がブロックされ続けるのを防ぐ。
     */
    private void resetIfMonthChanged(LineChannel channel, LocalDateTime now) {
        LocalDateTime lastSync = channel.getMessageCountResetAt();
        if (lastSync == null
                || lastSync.getYear() != now.getYear()
                || lastSync.getMonthValue() != now.getMonthValue()) {
            log.info("Month changed since last sync for channel {} (id={}), resetting count to 0",
                channel.getChannelName(), channel.getId());
            channel.setMonthlyMessageCount(0);
            channel.setMessageCountResetAt(now);
            lineChannelRepository.save(channel);
        }
    }
}
