package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeSyncService tests")
class DensukeSyncServiceTest {

    @Mock private DensukeWriteService densukeWriteService;
    @Mock private DensukeImportService densukeImportService;
    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private DensukeScheduleWriteService densukeScheduleWriteService;

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
    @DisplayName("syncAll runs import for every URL (phase decision is per-entry inside import)")
    void syncAll_runsImportForEveryUrl() throws Exception {
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
        when(densukeImportService.importFromDensuke(any(), any(), any(), any()))
                .thenReturn(result);

        densukeSyncService.syncAll();

        verify(densukeWriteService).writeToDensuke();
        verify(densukeImportService, times(2)).importFromDensuke(
                eq("https://densuke.biz/list?cd=test"),
                any(LocalDate.class),
                eq(DensukeImportService.SYSTEM_USER_ID),
                eq(1L));
    }

    @Test
    @DisplayName("testSyncForOrganizationInvokesPushBeforeWrite: syncForOrganization も pushSilently → writeToDensukeForOrganization → importFromDensuke の順")
    void testSyncForOrganizationInvokesPushBeforeWrite() throws Exception {
        // Given: 手動同期経路（Round 3 WARNING 1 対応の検証）
        DensukeUrl densukeUrl = DensukeUrl.builder()
                .id(10L)
                .year(2026)
                .month(5)
                .organizationId(1L)
                .url("https://densuke.biz/list?cd=test")
                .build();
        DensukeImportService.ImportResult result = new DensukeImportService.ImportResult();

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(Optional.of(densukeUrl));
        when(densukeImportService.importFromDensuke(any(), any(), any(), any()))
                .thenReturn(result);

        // When
        densukeSyncService.syncForOrganization(2026, 5, 1L, 99L);

        // Then: pushSilently → writeToDensukeForOrganization → importFromDensuke の順
        InOrder inOrder = inOrder(densukeScheduleWriteService, densukeWriteService, densukeImportService);
        inOrder.verify(densukeScheduleWriteService).pushSilently(2026, 5, 1L);
        inOrder.verify(densukeWriteService).writeToDensukeForOrganization(densukeUrl);
        inOrder.verify(densukeImportService).importFromDensuke(
                eq("https://densuke.biz/list?cd=test"),
                any(LocalDate.class),
                eq(99L),
                eq(1L));
    }

    @Test
    @DisplayName("testSyncAllInvokesScheduleFollowUpSync: syncAll() が pushAllForCurrentAndNextMonth() を writeToDensuke() の前に呼ぶ")
    void testSyncAllInvokesScheduleFollowUpSync() throws Exception {
        // Given: スケジューラ経路の通常フロー
        DensukeUrl densukeUrl = DensukeUrl.builder()
                .id(10L)
                .year(2026)
                .month(5)
                .organizationId(1L)
                .url("https://densuke.biz/list?cd=test")
                .build();
        DensukeImportService.ImportResult result = new DensukeImportService.ImportResult();

        when(densukeUrlRepository.findByYearAndMonth(anyInt(), anyInt()))
                .thenReturn(List.of(densukeUrl));
        when(densukeImportService.importFromDensuke(any(), any(), any(), any()))
                .thenReturn(result);

        // When
        densukeSyncService.syncAll();

        // Then: スケジュール push フォロー同期が writeToDensuke の前に呼ばれる
        verify(densukeScheduleWriteService).pushAllForCurrentAndNextMonth();
        InOrder inOrder = inOrder(densukeScheduleWriteService, densukeWriteService);
        inOrder.verify(densukeScheduleWriteService).pushAllForCurrentAndNextMonth();
        inOrder.verify(densukeWriteService).writeToDensuke();
    }
}
