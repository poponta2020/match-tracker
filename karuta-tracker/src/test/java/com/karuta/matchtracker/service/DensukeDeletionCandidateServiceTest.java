package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.DensukeDeletionCandidateRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeDeletionCandidateService 単体テスト")
class DensukeDeletionCandidateServiceTest {

    @Mock private DensukeDeletionCandidateRepository densukeDeletionCandidateRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;

    @InjectMocks
    private DensukeDeletionCandidateService service;

    @Test
    @DisplayName("approve: 該当試合の出欠エントリのみ削除し、totalMatchesには触れずAPPROVEDにする")
    void approve_deletesParticipantsAndMarksApproved() {
        DensukeDeletionCandidate candidate = DensukeDeletionCandidate.builder()
                .id(1L).densukeUrlId(10L).organizationId(1L)
                .sessionDate(LocalDate.of(2026, 7, 10)).matchNumber(2)
                .status(DensukeDeletionCandidate.Status.PENDING)
                .build();
        PracticeSession session = PracticeSession.builder().id(99L).organizationId(1L)
                .sessionDate(LocalDate.of(2026, 7, 10)).totalMatches(5).build();

        when(densukeDeletionCandidateRepository.findById(1L)).thenReturn(Optional.of(candidate));
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(
                LocalDate.of(2026, 7, 10), 1L)).thenReturn(Optional.of(session));
        when(densukeDeletionCandidateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DensukeDeletionCandidate result = service.approve(1L, 55L);

        verify(practiceParticipantRepository).deleteBySessionIdAndMatchNumber(99L, 2);
        assertThat(result.getStatus()).isEqualTo(DensukeDeletionCandidate.Status.APPROVED);
        assertThat(result.getResolvedBy()).isEqualTo(55L);
        assertThat(result.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("reject: データは削除せずREJECTEDにする")
    void reject_marksRejectedWithoutDeleting() {
        DensukeDeletionCandidate candidate = DensukeDeletionCandidate.builder()
                .id(2L).densukeUrlId(10L).organizationId(1L)
                .sessionDate(LocalDate.of(2026, 7, 10)).matchNumber(2)
                .status(DensukeDeletionCandidate.Status.PENDING)
                .build();
        when(densukeDeletionCandidateRepository.findById(2L)).thenReturn(Optional.of(candidate));
        when(densukeDeletionCandidateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DensukeDeletionCandidate result = service.reject(2L, 55L);

        verifyNoInteractions(practiceParticipantRepository);
        assertThat(result.getStatus()).isEqualTo(DensukeDeletionCandidate.Status.REJECTED);
    }

    @Test
    @DisplayName("approve: 既に承認済みの候補は IllegalStateException")
    void approve_alreadyResolved_throws() {
        DensukeDeletionCandidate candidate = DensukeDeletionCandidate.builder()
                .id(3L).status(DensukeDeletionCandidate.Status.APPROVED).build();
        when(densukeDeletionCandidateRepository.findById(3L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.approve(3L, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("approve: 存在しない候補は ResourceNotFoundException")
    void approve_notFound_throws() {
        when(densukeDeletionCandidateRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("checkAdminScope: ADMINが他団体の候補を操作しようとするとForbidden")
    void checkAdminScope_blocksOtherOrgForAdmin() {
        DensukeDeletionCandidate candidate = DensukeDeletionCandidate.builder()
                .id(4L).organizationId(2L).build();
        when(densukeDeletionCandidateRepository.findById(4L)).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.checkAdminScope(4L, "ADMIN", 1L))
                .isInstanceOf(com.karuta.matchtracker.exception.ForbiddenException.class);
    }

    @Test
    @DisplayName("checkAdminScope: SUPER_ADMINは団体を問わず操作可能")
    void checkAdminScope_allowsSuperAdmin() {
        assertThat(catchThrowableIfAny(() -> service.checkAdminScope(5L, "SUPER_ADMIN", null))).isNull();
        verifyNoInteractions(densukeDeletionCandidateRepository);
    }

    private Throwable catchThrowableIfAny(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
