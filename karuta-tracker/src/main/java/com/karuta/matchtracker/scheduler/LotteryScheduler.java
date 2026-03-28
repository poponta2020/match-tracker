package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionType;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.service.LotteryDeadlineHelper;
import com.karuta.matchtracker.service.LotteryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 抽選自動実行スケジューラ
 *
 * - 毎日0時にチェックし、当日が月末日なら翌月分の抽選を実行
 * - アプリケーション起動時に未実行の抽選がないかチェック（リトライ機構）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LotteryScheduler {

    private final LotteryService lotteryService;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;

    /**
     * 毎日0:00にチェック
     * 翌月分の抽選締め切りが過ぎていれば抽選を実行
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void checkAndExecuteLottery() {
        LocalDate today = JstDateTimeUtil.today();
        YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
        int targetYear = nextMonth.getYear();
        int targetMonth = nextMonth.getMonthValue();

        // TODO: タスク7で団体別に処理するよう改修
        if (lotteryDeadlineHelper.isAfterDeadline(targetYear, targetMonth, null)) {
            executeLotteryIfNotDone(targetYear, targetMonth);
        }
    }

    /**
     * アプリケーション起動時のリトライチェック
     * 未実行の抽選がないか確認し、あれば実行
     *
     * 当月分と翌月分の両方をチェックする。
     * - 当月分: 前月末に実行されたはずの抽選が失敗していた場合のリトライ
     * - 翌月分: 当日が締め切り後（月末等）で自動抽選が失敗していた場合のリトライ
     */
    @EventListener(ApplicationReadyEvent.class)
    public void retryOnStartup() {
        try {
            LocalDate today = JstDateTimeUtil.today();

            // 当月分のチェック
            int currentYear = today.getYear();
            int currentMonth = today.getMonthValue();
            // TODO: タスク7で団体別に処理するよう改修
            if (lotteryDeadlineHelper.isAfterDeadline(currentYear, currentMonth, null)) {
                executeLotteryIfNotDone(currentYear, currentMonth);
            }

            // 翌月分のチェック（月末に自動抽選が失敗し同日中に再起動した場合に対応）
            YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
            int nextYear = nextMonth.getYear();
            int nextMonthValue = nextMonth.getMonthValue();
            if (lotteryDeadlineHelper.isAfterDeadline(nextYear, nextMonthValue, null)) {
                executeLotteryIfNotDone(nextYear, nextMonthValue);
            }
        } catch (Exception e) {
            log.error("Failed to execute startup lottery retry", e);
        }
    }

    /**
     * 指定年月の抽選が未実行の場合のみ実行
     */
    private void executeLotteryIfNotDone(int year, int month) {
        boolean alreadyExecuted = lotteryExecutionRepository
                .existsByTargetYearAndTargetMonthAndStatus(year, month, ExecutionStatus.SUCCESS);

        if (alreadyExecuted) {
            log.debug("Lottery for {}-{} already executed, skipping", year, month);
            return;
        }

        log.info("Executing lottery for {}-{}", year, month);
        LotteryExecution result = lotteryService.executeLottery(year, month, null, ExecutionType.AUTO);

        if (result.getStatus() == ExecutionStatus.SUCCESS) {
            log.info("Lottery for {}-{} completed successfully", year, month);
        } else {
            log.error("Lottery for {}-{} failed: {}", year, month, result.getDetails());
        }
    }
}
