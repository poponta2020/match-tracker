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
     * 当日が月末日の場合、翌月分の抽選を実行
     */
    @Scheduled(cron = "0 0 0 * * *")
    public void checkAndExecuteLottery() {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        LocalDate lastDayOfMonth = currentMonth.atEndOfMonth();

        if (!today.equals(lastDayOfMonth)) {
            return;
        }

        // 翌月分の抽選を実行
        YearMonth nextMonth = currentMonth.plusMonths(1);
        int targetYear = nextMonth.getYear();
        int targetMonth = nextMonth.getMonthValue();

        executeLotteryIfNotDone(targetYear, targetMonth);
    }

    /**
     * アプリケーション起動時のリトライチェック
     * 未実行の抽選がないか確認し、あれば実行
     */
    @EventListener(ApplicationReadyEvent.class)
    public void retryOnStartup() {
        LocalDate today = LocalDate.now();
        int targetYear = today.getYear();
        int targetMonth = today.getMonthValue();

        // 当月の抽選が実行済みかチェック
        if (lotteryDeadlineHelper.isAfterDeadline(targetYear, targetMonth)) {
            executeLotteryIfNotDone(targetYear, targetMonth);
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
