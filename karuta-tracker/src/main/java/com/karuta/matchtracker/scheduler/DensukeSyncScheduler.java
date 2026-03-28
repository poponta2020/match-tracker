package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.service.DensukeImportService;
import com.karuta.matchtracker.service.DensukeWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 伝助との自動同期スケジューラー
 * 毎分実行し、当月＋翌月の伝助URLからデータを同期する
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DensukeSyncScheduler {

    private final DensukeImportService densukeImportService;
    private final DensukeWriteService densukeWriteService;
    private final DensukeUrlRepository densukeUrlRepository;

    /**
     * 毎分実行：① アプリ→伝助（書き込み）→ ② 伝助→アプリ（読み取り）の順で同期
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void syncDensuke() {
        // ① アプリ→伝助: dirty=true の参加者を書き込む
        try {
            densukeWriteService.writeToDensuke();
        } catch (Exception e) {
            log.warn("Densuke write failed: {}", e.getMessage());
        }

        LocalDate now = JstDateTimeUtil.today();

        // ② 伝助→アプリ: 当月・翌月を読み取り
        syncForMonth(now.getYear(), now.getMonthValue());
        LocalDate nextMonth = now.plusMonths(1);
        syncForMonth(nextMonth.getYear(), nextMonth.getMonthValue());
    }

    private void syncForMonth(int year, int month) {
        Optional<DensukeUrl> densukeUrl = densukeUrlRepository.findByYearAndMonth(year, month);
        if (densukeUrl.isEmpty()) {
            return;
        }

        try {
            var result = densukeImportService.importFromDensuke(densukeUrl.get().getUrl(), null,
                    DensukeImportService.SYSTEM_USER_ID);
            if (result.getRegisteredCount() > 0 || result.getCreatedSessionCount() > 0) {
                log.info("Auto-sync {}/{}: {} sessions created, {} participants registered",
                        year, month, result.getCreatedSessionCount(), result.getRegisteredCount());
            }
        } catch (Exception e) {
            log.warn("Auto-sync failed for {}/{}: {}", year, month, e.getMessage());
        }
    }
}
