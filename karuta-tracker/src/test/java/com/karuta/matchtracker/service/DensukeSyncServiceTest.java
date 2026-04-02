package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeSyncService 単体テスト")
class DensukeSyncServiceTest {

    @Mock private DensukeWriteService densukeWriteService;
    @Mock private DensukeImportService densukeImportService;
    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock private LotteryService lotteryService;

    @InjectMocks
    private DensukeSyncService densukeSyncService;

    @Test
    @DisplayName("syncForOrganization: 指定団体・指定年月のみを書き込み、対象年月で取り込む")
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
    @DisplayName("syncForOrganization: URL未登録なら例外を投げる")
    void syncForOrganization_throwsWhenUrlMissing() {
        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2025, 1, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> densukeSyncService.syncForOrganization(2025, 1, 1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Densuke URL not found");
    }
}
