package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.service.KaderuSyncTriggerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Kaderu予約取り込み手動トリガーのステータス巡回スケジューラー。
 *
 * <p>30秒間隔で全 PENDING イベントを巡回し、GitHub Actions workflow run の
 * 完了を検知して COMPLETED / FAILED に確定する。
 * 30分タイムアウトの fail-safe もここで担保する。
 *
 * <p>本クラスからサービスメソッドを「個別の Bean 呼び出し」として叩くことが
 * 重要。{@link KaderuSyncTriggerService} 内のメソッドを直接呼ぶと
 * Spring AOP の {@code @Transactional} プロキシをバイパスして、
 * 「1イベントごとに独立 tx」の原則が崩れる。Scheduler から呼べば proxy 経由になる。
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
        List<Long> pendingIds;
        try {
            // listPendingIds() は @Transactional(readOnly = true)。Scheduler → Service の
            // 呼び出しはプロキシ経由になるので tx が正しく開かれる。
            pendingIds = kaderuSyncTriggerService.listPendingIds();
        } catch (Exception e) {
            log.warn("Failed to list pending kaderu sync events: {}", e.getMessage(), e);
            return;
        }
        if (pendingIds.isEmpty()) return;

        log.debug("Polling {} pending kaderu sync event(s)", pendingIds.size());
        for (Long id : pendingIds) {
            try {
                // processPendingEvent(id) は @Transactional。1件ずつ独立 tx で実行され、
                // 1件の例外が他イベントに波及しないよう外側で catch する。
                kaderuSyncTriggerService.processPendingEvent(id);
            } catch (Exception e) {
                log.warn("Failed to process pending kaderu sync event {}: {}", id, e.getMessage(), e);
            }
        }
    }
}
