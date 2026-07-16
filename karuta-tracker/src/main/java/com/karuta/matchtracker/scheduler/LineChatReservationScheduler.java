package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.service.LineChatReservationService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * LINEチャット予約キューのスケジューラ（line-chat-reserve-broadcast タスク2）。
 *
 * <ul>
 *   <li>前日20:00（JST）: 翌日分の予約レコードを冪等生成する。</li>
 *   <li>15分毎: リコンサイル（RESERVING 滞留昇格・変更検知の取消/再予約・取消後の再作成）。</li>
 * </ul>
 *
 * 実処理は {@link LineChatReservationService} にあり、こちらは起動タイミングと {@code today/now} の供給のみを担う。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineChatReservationScheduler {

    private final LineChatReservationService lineChatReservationService;

    /** 前日20:00（JST）に翌日分の予約を生成する。 */
    @Scheduled(cron = "0 0 20 * * *", zone = "Asia/Tokyo")
    public void generateDailyReservations() {
        try {
            lineChatReservationService.generateReservations(
                    JstDateTimeUtil.today().plusDays(1), JstDateTimeUtil.now());
        } catch (Exception e) {
            log.error("Chat reservation daily batch failed: {}", e.getMessage(), e);
        }
    }

    /** 15分毎にリコンサイルする（起動5分後に初回）。 */
    @Scheduled(fixedDelay = 900000, initialDelay = 300000)
    public void reconcile() {
        try {
            lineChatReservationService.reconcile(JstDateTimeUtil.today(), JstDateTimeUtil.now());
        } catch (Exception e) {
            log.error("Chat reservation reconcile failed: {}", e.getMessage(), e);
        }
    }
}
