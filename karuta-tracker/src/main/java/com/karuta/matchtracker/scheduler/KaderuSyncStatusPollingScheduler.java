package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.service.KaderuSyncTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Kaderu予約取り込み手動トリガーのステータス巡回スケジューラー。
 *
 * <p>30秒間隔で全 PENDING イベントを巡回し、GitHub Actions workflow run の
 * 完了を検知して COMPLETED / FAILED に確定する。
 * 30分タイムアウトの fail-safe もここで担保する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KaderuSyncStatusPollingScheduler {

    private final KaderuSyncTriggerService kaderuSyncTriggerService;

    /**
     * 30秒間隔で全 PENDING を巡回する。初回は30秒待機（起動直後の負荷集中を避ける）。
     * fixedDelay のため、前回ティックの処理時間に関係なく前回終了から30秒空く。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void pollPendingKaderuSyncEvents() {
        kaderuSyncTriggerService.pollPendingEvents();
    }
}
