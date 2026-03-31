package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.service.DensukeSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 伝助との自動同期スケジューラー
 * 5分間隔で実行し、当月＋翌月の全団体の伝助URLからデータを同期する
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DensukeSyncScheduler {

    private final DensukeSyncService densukeSyncService;

    /**
     * 5分間隔で実行：① アプリ→伝助（残存dirty書き込み）→ ② 伝助→アプリ（読み取り）の順で同期
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void syncDensuke() {
        densukeSyncService.syncAll();
    }
}
