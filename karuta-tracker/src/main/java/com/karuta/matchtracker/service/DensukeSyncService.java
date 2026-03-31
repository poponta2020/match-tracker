package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DensukeSyncService {

    private final DensukeWriteService densukeWriteService;
    private final DensukeImportService densukeImportService;
    private final DensukeUrlRepository densukeUrlRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final LotteryService lotteryService;

    /**
     * 特定団体の伝助同期（書き込み + 読み取り）
     */
    @Transactional
    public DensukeImportService.ImportResult syncForOrganization(int year, int month, Long organizationId, Long userId) throws IOException {
        // ① アプリ→伝助: dirty=true の参加者を書き込む
        densukeWriteService.writeToDensuke();

        // ② 伝助→アプリ: 対象団体のURLからデータを読み取り
        DensukeUrl densukeUrl = densukeUrlRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Densuke URL not found for " + year + "/" + month + " (orgId=" + organizationId + ")"));

        return densukeImportService.importFromDensuke(densukeUrl.getUrl(), null, userId, organizationId);
    }

    /**
     * 全団体の伝助同期（当月 + 翌月）
     */
    public void syncAll() {
        // ① アプリ→伝助: dirty=true の参加者を書き込む（全団体分を1回で処理）
        try {
            densukeWriteService.writeToDensuke();
        } catch (Exception e) {
            log.warn("Densuke write failed: {}", e.getMessage());
        }

        LocalDate now = JstDateTimeUtil.today();

        // ② 伝助→アプリ: 当月・翌月の全団体のURLを読み取り
        syncForMonth(now.getYear(), now.getMonthValue());
        LocalDate nextMonth = now.plusMonths(1);
        syncForMonth(nextMonth.getYear(), nextMonth.getMonthValue());
    }

    private void syncForMonth(int year, int month) {
        List<DensukeUrl> densukeUrls = densukeUrlRepository.findByYearAndMonth(year, month);
        for (DensukeUrl densukeUrl : densukeUrls) {
            Long orgId = densukeUrl.getOrganizationId();

            // MONTHLY型でフェーズ2（締切後・抽選確定前）の場合、インポートをスキップ
            // （書き戻しは writeToDensuke() で既に実行済み）
            DeadlineType deadlineType = lotteryDeadlineHelper.getDeadlineType(orgId);
            if (deadlineType != DeadlineType.SAME_DAY
                    && lotteryDeadlineHelper.isAfterDeadline(year, month, orgId)
                    && !lotteryService.isLotteryConfirmed(year, month)) {
                log.debug("Skipping import for {}/{} (orgId={}): Phase 2 (after deadline, before lottery confirmation)",
                        year, month, orgId);
                continue;
            }

            try {
                var result = densukeImportService.importFromDensuke(
                        densukeUrl.getUrl(), null,
                        DensukeImportService.SYSTEM_USER_ID, orgId);
                if (result.getRegisteredCount() > 0 || result.getCreatedSessionCount() > 0
                        || result.getRemovedCount() > 0) {
                    log.info("Sync {}/{} (orgId={}): {} sessions created, {} registered, {} removed",
                            year, month, orgId,
                            result.getCreatedSessionCount(), result.getRegisteredCount(),
                            result.getRemovedCount());
                }
            } catch (Exception e) {
                log.error("Sync failed for {}/{} (orgId={})", year, month, orgId, e);
            }
        }
    }

    /**
     * 非同期で伝助への書き込みのみ実行
     */
    @Async
    public void triggerWriteAsync() {
        try {
            densukeWriteService.writeToDensuke();
        } catch (Exception e) {
            log.warn("Async Densuke write failed: {}", e.getMessage());
        }
    }
}
