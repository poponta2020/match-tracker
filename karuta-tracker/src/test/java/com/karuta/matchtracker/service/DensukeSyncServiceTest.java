package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeSyncService tests")
class DensukeSyncServiceTest {

    @Mock private DensukeWriteService densukeWriteService;
    @Mock private DensukeImportService densukeImportService;
    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock private LotteryService lotteryService;

    @InjectMocks
    private DensukeSyncService densukeSyncService;

    @Test
    @DisplayName("syncForOrganization writes scoped updates then imports target month")
    void syncForOrganization_scopedWriteAndTargetMonthImport() throws Exception {
        DensukeUrl densukeUrl = DensukeUrl.builder()
                .id(10L)
                .year(2025)
                .month(1)
                .organizationId(1L)
                .url("https://densuke.biz/list?cd=test")
                .build();
        DensukeImportService.ImportResult result = new DensukeImportService.ImportResult();

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2025, 1, 1L))
                .thenReturn(Optional.of(densukeUrl));
        when(densukeImportService.importFromDensuke(any(), any(), any(), any()))
                .thenReturn(result);

        DensukeImportService.ImportResult actual =
                densukeSyncService.syncForOrganization(2025, 1, 1L, 99L);

        assertThat(actual).isSameAs(result);
        verify(densukeWriteService).writeToDensukeForOrganization(densukeUrl);
        verify(densukeImportService).importFromDensuke(
                "https://densuke.biz/list?cd=test",
                LocalDate.of(2025, 1, 1),
                99L,
                1L
        );
    }

    @Test
    @DisplayName("syncForOrganization throws when URL is missing")
    void syncForOrganization_throwsWhenUrlMissing() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2025, 1, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> densukeSyncService.syncForOrganization(2025, 1, 1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Densuke URL not found");
    }

    @Test
    @DisplayName("syncAll skips import in MONTHLY phase2")
    void syncAll_skipsImportInMonthlyPhase2() throws Exception {
        DensukeUrl densukeUrl = DensukeUrl.builder()
                .id(10L)
                .year(2026)
                .month(4)
                .organizationId(1L)
                .url("https://densuke.biz/list?cd=test")
                .build();

        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenReturn(List.of(densukeUrl));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isAfterDeadline(anyInt(), anyInt(), eq(1L))).thenReturn(true);
        when(lotteryService.isLotteryConfirmed(anyInt(), anyInt(), eq(1L))).thenReturn(false);

        densukeSyncService.syncAll();

        verify(densukeWriteService).writeToDensuke();
        verify(densukeImportService, never()).importFromDensuke(any(), any(), any(), any());
    }

    @Test
    @DisplayName("syncAll does not skip import for SAME_DAY organizations")
    void syncAll_sameDayDoesNotSkipImport() throws Exception {
        DensukeUrl densukeUrl = DensukeUrl.builder()
                .id(10L)
                .year(2026)
                .month(4)
                .organizationId(1L)
                .url("https://densuke.biz/list?cd=test")
                .build();
        DensukeImportService.ImportResult result = new DensukeImportService.ImportResult();

        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenReturn(List.of(densukeUrl));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.SAME_DAY);
        when(densukeImportService.importFromDensuke(any(), any(), any(), any()))
                .thenReturn(result);

        densukeSyncService.syncAll();

        verify(densukeWriteService).writeToDensuke();
        verify(densukeImportService, times(2)).importFromDensuke(
                eq("https://densuke.biz/list?cd=test"),
                any(LocalDate.class),
                eq(DensukeImportService.SYSTEM_USER_ID),
                eq(1L));
        verify(lotteryDeadlineHelper, never()).isAfterDeadline(anyInt(), anyInt(), eq(1L));
        verify(lotteryService, never()).isLotteryConfirmed(anyInt(), anyInt(), eq(1L));
    }

    @Test
    @DisplayName("syncAll imports when MONTHLY lottery is already confirmed")
    void syncAll_importsWhenMonthlyLotteryConfirmed() throws Exception {
        DensukeUrl densukeUrl = DensukeUrl.builder()
                .id(10L)
                .year(2026)
                .month(4)
                .organizationId(1L)
                .url("https://densuke.biz/list?cd=test")
                .build();
        DensukeImportService.ImportResult result = new DensukeImportService.ImportResult();

        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenReturn(List.of(densukeUrl));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isAfterDeadline(anyInt(), anyInt(), eq(1L))).thenReturn(true);
        when(lotteryService.isLotteryConfirmed(anyInt(), anyInt(), eq(1L))).thenReturn(true);
        when(densukeImportService.importFromDensuke(any(), any(), any(), any()))
                .thenReturn(result);

        densukeSyncService.syncAll();

        verify(densukeImportService, times(2)).importFromDensuke(
                eq("https://densuke.biz/list?cd=test"),
                any(LocalDate.class),
                eq(DensukeImportService.SYSTEM_USER_ID),
                eq(1L));
    }
}
