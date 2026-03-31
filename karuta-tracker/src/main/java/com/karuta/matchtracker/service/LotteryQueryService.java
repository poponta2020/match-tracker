package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 抽選状態の参照専用サービス
 *
 * LotteryService から参照系メソッドを分離し、
 * LotteryService ↔ LineNotificationService 間の循環依存を解消する。
 */
@Service
@RequiredArgsConstructor
public class LotteryQueryService {

    private final LotteryExecutionRepository lotteryExecutionRepository;

    /**
     * 指定年月・団体の抽選が確定済みかどうかを返す
     */
    public boolean isLotteryConfirmed(int year, int month, Long organizationId) {
        if (organizationId != null) {
            return lotteryExecutionRepository
                    .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                            year, month, organizationId, ExecutionStatus.SUCCESS)
                    .map(e -> e.getConfirmedAt() != null)
                    .orElse(false);
        }
        return lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndStatusOrderByExecutedAtDesc(
                        year, month, ExecutionStatus.SUCCESS)
                .map(e -> e.getConfirmedAt() != null)
                .orElse(false);
    }
}
