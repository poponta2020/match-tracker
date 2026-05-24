package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


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
    private final DensukeScheduleWriteService densukeScheduleWriteService;

    /**
     * 特定団体の伝助同期（書き込み + 読み取り）
     *
     * <p>順序は {@link #syncAll()} と揃える: スケジュール push → 参加者書き込み → 取り込み。
     * 即時 push (afterCommit) が失敗した直後に管理者が団体別手動同期を実行した場合でも
     * スケジュール差分が自動補完される（Codex Round 3 WARNING 1 対応）。
     */
    public DensukeImportService.ImportResult syncForOrganization(int year, int month, Long organizationId, Long userId) throws IOException {
        // ① 伝助URLを取得
        DensukeUrl densukeUrl = densukeUrlRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Densuke URL not found for " + year + "/" + month + " (orgId=" + organizationId + ")"));

        // ② アプリ→伝助 スケジュール push（手動同期経路でも syncAll() と同じ順序に揃える）
        //    失敗は WARN ログのみ。手動同期画面のメイン操作は writeToDensuke / import なので
        //    スケジュール push の失敗で全体を中断しない。即時 push の失敗通知は afterCommit
        //    フック側で既に発火しているため、ここではスケジューラ経路と同じく抑制する。
        try {
            densukeScheduleWriteService.pushSilently(year, month, organizationId);
        } catch (Exception e) {
            log.warn("Densuke schedule push (syncForOrganization) failed: orgId={}, {}/{}, err={}",
                    organizationId, year, month, e.getMessage());
        }

        // ③ アプリ→伝助: 指定団体・指定年月のdirty=true 参加者のみを書き込む
        densukeWriteService.writeToDensukeForOrganization(densukeUrl);

        // ④ 伝助→アプリ: 対象団体のURLからデータを読み取り
        LocalDate targetMonth = LocalDate.of(year, month, 1);
        return densukeImportService.importFromDensuke(densukeUrl.getUrl(), targetMonth, userId, organizationId);
    }

    /**
     * 全団体の伝助同期（当月 + 翌月）
     *
     * <p>順序が重要:
     * <ol>
     *   <li>アプリ→伝助 スケジュール push（フォロー同期。即時 push 失敗時の自動回復）</li>
     *   <li>アプリ→伝助 参加者 dirty 書き込み（スケジュールが揃った状態で書く）</li>
     *   <li>伝助→アプリ 取り込み（伝助の最新状態を取り込む）</li>
     * </ol>
     */
    public void syncAll() {
        // ① アプリ→伝助 スケジュール push（即時 push 失敗の自動回復用フォロー同期）
        //    失敗は WARN ログのみで管理者通知は発火しない（DensukeScheduleWriteService 側で抑制済み、フラッディング防止）
        try {
            densukeScheduleWriteService.pushAllForCurrentAndNextMonth();
        } catch (Exception e) {
            log.warn("Densuke schedule push (scheduler) failed: {}", e.getMessage());
        }

        // ② アプリ→伝助: dirty=true の参加者を書き込む（全団体分を1回で処理）
        try {
            densukeWriteService.writeToDensuke();
        } catch (Exception e) {
            log.warn("Densuke write failed: {}", e.getMessage());
        }

        LocalDate now = JstDateTimeUtil.today();

        // ③ 伝助→アプリ: 当月・翌月の全団体のURLを読み取り
        syncForMonth(now.getYear(), now.getMonthValue());
        LocalDate nextMonth = now.plusMonths(1);
        syncForMonth(nextMonth.getYear(), nextMonth.getMonthValue());
    }

    private void syncForMonth(int year, int month) {
        List<DensukeUrl> densukeUrls = densukeUrlRepository.findByYearAndMonth(year, month);
        for (DensukeUrl densukeUrl : densukeUrls) {
            Long orgId = densukeUrl.getOrganizationId();

            try {
                LocalDate targetMonth = LocalDate.of(year, month, 1);
                var result = densukeImportService.importFromDensuke(
                        densukeUrl.getUrl(), targetMonth,
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
