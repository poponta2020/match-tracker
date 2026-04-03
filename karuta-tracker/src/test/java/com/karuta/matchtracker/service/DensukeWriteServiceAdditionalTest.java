package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukeWriteStatusDto;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.DensukeMemberMappingRepository;
import com.karuta.matchtracker.repository.DensukeRowIdRepository;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeWriteService additional tests")
class DensukeWriteServiceAdditionalTest {

    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private DensukeUrlRepository densukeUrlRepository;
    @Mock private DensukeMemberMappingRepository densukeMemberMappingRepository;
    @Mock private DensukeRowIdRepository densukeRowIdRepository;
    @Mock private PlayerRepository playerRepository;

    @InjectMocks
    private DensukeWriteService densukeWriteService;

    @Test
    @DisplayName("writeToDensukeForOrganization updates success status when there are no dirty participants")
    void writeToDensukeForOrganization_noDirtyParticipants_setsSuccessStatus() {
        DensukeUrl densukeUrl = DensukeUrl.builder()
                .id(10L)
                .year(2026)
                .month(4)
                .organizationId(1L)
                .url("https://densuke.biz/list?cd=test")
                .build();
        PracticeSession session = PracticeSession.builder()
                .id(100L)
                .sessionDate(LocalDate.of(2026, 4, 3))
                .totalMatches(3)
                .organizationId(1L)
                .build();

        when(densukeUrlRepository.findByYearAndMonthAndOrganizationId(2026, 4, 1L))
                .thenReturn(Optional.of(densukeUrl));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 4, 1L))
                .thenReturn(List.of(session));
        when(practiceParticipantRepository.findDirtyForDensukeSync(List.of(100L)))
                .thenReturn(Collections.emptyList());

        densukeWriteService.writeToDensukeForOrganization(2026, 4, 1L);

        DensukeWriteStatusDto status = densukeWriteService.getStatus(1L);
        assertThat(status.getLastAttemptAt()).isNotNull();
        assertThat(status.getLastSuccessAt()).isNotNull();
        assertThat(status.getPendingCount()).isEqualTo(0);
        assertThat(status.getErrors()).isEmpty();
        verify(densukeMemberMappingRepository, never()).findByDensukeUrlIdAndPlayerId(any(), any());
    }
}
