package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionType;
import com.karuta.matchtracker.entity.Organization;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
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
 * MONTHLYタイプの団体のみ対象（SAME_DAYタイプは抽選なし）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LotteryScheduler {

    private final LotteryService lotteryService;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final OrganizationRepository organizationRepository;

    // 自動抽選は現在無効（手動実行のみ）。将来的に有効化する場合はアノテーションを戻す。
    // @Scheduled(cron = "0 0 0 * * *")
    public void checkAndExecuteLottery() {
        LocalDate today = JstDateTimeUtil.today();
        YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
        int targetYear = nextMonth.getYear();
        int targetMonth = nextMonth.getMonthValue();

        // MONTHLYタイプの団体のみ抽選チェック
        for (Organization org : organizationRepository.findAll()) {
            if (org.getDeadlineType() != DeadlineType.MONTHLY) continue;

            if (lotteryDeadlineHelper.isAfterDeadline(targetYear, targetMonth, org.getId())) {
                executeLotteryIfNotDone(targetYear, targetMonth, org.getId());
            }
        }
    }

    // @EventListener(ApplicationReadyEvent.class)
    public void retryOnStartup() {
        try {
            LocalDate today = JstDateTimeUtil.today();

            for (Organization org : organizationRepository.findAll()) {
                if (org.getDeadlineType() != DeadlineType.MONTHLY) continue;

                int currentYear = today.getYear();
                int currentMonth = today.getMonthValue();
                if (lotteryDeadlineHelper.isAfterDeadline(currentYear, currentMonth, org.getId())) {
                    executeLotteryIfNotDone(currentYear, currentMonth, org.getId());
                }

                YearMonth nextMonth = YearMonth.from(today).plusMonths(1);
                int nextYear = nextMonth.getYear();
                int nextMonthValue = nextMonth.getMonthValue();
                if (lotteryDeadlineHelper.isAfterDeadline(nextYear, nextMonthValue, org.getId())) {
                    executeLotteryIfNotDone(nextYear, nextMonthValue, org.getId());
                }
            }
        } catch (Exception e) {
            log.error("Failed to execute startup lottery retry", e);
        }
    }

    private void executeLotteryIfNotDone(int year, int month, Long organizationId) {
        boolean alreadyExecuted = organizationId != null
                ? lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                        year, month, organizationId, ExecutionStatus.SUCCESS)
                : lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                        year, month, ExecutionStatus.SUCCESS);

        if (alreadyExecuted) {
            log.debug("Lottery for {}-{} already executed, skipping", year, month);
            return;
        }

        log.info("Executing lottery for {}-{} (orgId={})", year, month, organizationId);
        LotteryExecution result = lotteryService.executeLottery(year, month, null, ExecutionType.AUTO, organizationId, new java.util.Random().nextLong());

        if (result.getStatus() == ExecutionStatus.SUCCESS) {
            log.info("Lottery for {}-{} completed successfully", year, month);
        } else {
            log.error("Lottery for {}-{} failed: {}", year, month, result.getDetails());
        }
    }
}
