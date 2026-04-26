package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryQueryService isLotteryConfirmed テスト")
class LotteryQueryServiceTest {

    @Mock
    private LotteryExecutionRepository lotteryExecutionRepository;

    @InjectMocks
    private LotteryQueryService lotteryQueryService;

    @Test
    @DisplayName("organizationId指定 + 自団体confirmed → true")
    void organizationSpecific_confirmed_returnsTrue() {
        LotteryExecution confirmed = LotteryExecution.builder()
                .id(1L)
                .targetYear(2026).targetMonth(4)
                .organizationId(1L)
                .status(ExecutionStatus.SUCCESS)
                .confirmedAt(LocalDateTime.of(2026, 4, 1, 12, 0))
                .build();
        when(lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                        2026, 4, 1L, ExecutionStatus.SUCCESS))
                .thenReturn(Optional.of(confirmed));

        boolean result = lotteryQueryService.isLotteryConfirmed(2026, 4, 1L);

        assertThat(result).isTrue();
        // 団体特定で確定が見つかったので、全団体共通レコードのチェックは行わない
        verify(lotteryExecutionRepository, never())
                .findTopByTargetYearAndTargetMonthAndOrganizationIdIsNullAndStatusOrderByExecutedAtDesc(
                        anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("organizationId指定 + 自団体未確定だが全団体共通confirmed → true")
    void organizationSpecific_orgUnconfirmed_globalConfirmed_returnsTrue() {
        // 自団体のSUCCESSレコードはあるが confirmedAt が null（未確定）
        LotteryExecution orgExec = LotteryExecution.builder()
                .id(1L)
                .targetYear(2026).targetMonth(4)
                .organizationId(1L)
                .status(ExecutionStatus.SUCCESS)
                .confirmedAt(null)
                .build();
        // 全団体対象の確定レコード
        LotteryExecution globalConfirmed = LotteryExecution.builder()
                .id(2L)
                .targetYear(2026).targetMonth(4)
                .organizationId(null)
                .status(ExecutionStatus.SUCCESS)
                .confirmedAt(LocalDateTime.of(2026, 4, 1, 12, 0))
                .build();
        when(lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                        2026, 4, 1L, ExecutionStatus.SUCCESS))
                .thenReturn(Optional.of(orgExec));
        when(lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndOrganizationIdIsNullAndStatusOrderByExecutedAtDesc(
                        2026, 4, ExecutionStatus.SUCCESS))
                .thenReturn(Optional.of(globalConfirmed));

        boolean result = lotteryQueryService.isLotteryConfirmed(2026, 4, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("organizationId指定 + 自団体未確定 + 全団体共通も未確定 → false")
    void organizationSpecific_unconfirmed_returnsFalse() {
        when(lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                        2026, 4, 1L, ExecutionStatus.SUCCESS))
                .thenReturn(Optional.empty());
        when(lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndOrganizationIdIsNullAndStatusOrderByExecutedAtDesc(
                        2026, 4, ExecutionStatus.SUCCESS))
                .thenReturn(Optional.empty());

        boolean result = lotteryQueryService.isLotteryConfirmed(2026, 4, 1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("organizationId=null + confirmed → true（団体非特定パス）")
    void noOrganizationId_confirmed_returnsTrue() {
        LotteryExecution confirmed = LotteryExecution.builder()
                .id(1L)
                .targetYear(2026).targetMonth(4)
                .organizationId(null)
                .status(ExecutionStatus.SUCCESS)
                .confirmedAt(LocalDateTime.of(2026, 4, 1, 12, 0))
                .build();
        when(lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndStatusOrderByExecutedAtDesc(
                        2026, 4, ExecutionStatus.SUCCESS))
                .thenReturn(Optional.of(confirmed));

        boolean result = lotteryQueryService.isLotteryConfirmed(2026, 4, null);

        assertThat(result).isTrue();
        // 団体非特定パスではOrganizationId付きのクエリは実行しない
        verify(lotteryExecutionRepository, never())
                .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                        anyInt(), anyInt(), anyLong(), any());
    }

    @Test
    @DisplayName("organizationId=null + 抽選なし → false")
    void noOrganizationId_noLottery_returnsFalse() {
        when(lotteryExecutionRepository
                .findTopByTargetYearAndTargetMonthAndStatusOrderByExecutedAtDesc(
                        2026, 4, ExecutionStatus.SUCCESS))
                .thenReturn(Optional.empty());

        boolean result = lotteryQueryService.isLotteryConfirmed(2026, 4, null);

        assertThat(result).isFalse();
    }
}
