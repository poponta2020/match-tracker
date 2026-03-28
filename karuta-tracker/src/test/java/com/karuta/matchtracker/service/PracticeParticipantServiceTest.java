package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PracticeParticipationRequest;
import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.karuta.matchtracker.dto.PlayerParticipationStatusDto;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PracticeParticipantService 締切後登録テスト")
class PracticeParticipantServiceTest {

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock
    private LotteryDeadlineHelper lotteryDeadlineHelper;

    @InjectMocks
    private PracticeParticipantService service;

    @Captor
    private ArgumentCaptor<PracticeParticipant> participantCaptor;

    private static final Long ORG_ID = 1L;

    private PracticeSession createSession(Long id, Integer capacity) {
        PracticeSession s = new PracticeSession();
        s.setId(id);
        s.setCapacity(capacity);
        s.setOrganizationId(ORG_ID);
        return s;
    }

    @Test
    @DisplayName("締切後+抽選済み+定員超過の場合WAITLISTEDで登録される")
    void afterDeadline_overCapacity_waitlisted() {
        PracticeSession session = createSession(100L, 4);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(false);
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                2025, 4, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
        when(practiceParticipantRepository.existsBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(false);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                .thenReturn(4L);
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1))
                .thenReturn(Optional.of(2));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(
                createParticipation(100L, 1)
        ));

        service.registerParticipations(request);

        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant saved = participantCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(saved.getWaitlistNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("締切後+空きがある場合WONで登録される")
    void afterDeadline_hasCapacity_won() {
        PracticeSession session = createSession(100L, 4);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(false);
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                2025, 4, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
        when(practiceParticipantRepository.existsBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(false);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                .thenReturn(2L);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WAITLISTED))
                .thenReturn(false);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(false);

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(
                createParticipation(100L, 1)
        ));

        service.registerParticipations(request);

        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant saved = participantCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
    }

    @Test
    @DisplayName("締切前の場合beforeDeadlineがtrueで返される")
    void getPlayerParticipationStatus_beforeDeadline_returnsTrue() {
        when(practiceSessionRepository.findByYearAndMonth(2025, 4)).thenReturn(List.of());
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), isNull())).thenReturn(true);

        PlayerParticipationStatusDto result = service.getPlayerParticipationStatusByMonth(10L, 2025, 4);

        assertThat(result.getBeforeDeadline()).isTrue();
    }

    @Test
    @DisplayName("締切後の場合beforeDeadlineがfalseで返される")
    void getPlayerParticipationStatus_afterDeadline_returnsFalse() {
        when(practiceSessionRepository.findByYearAndMonth(2025, 4)).thenReturn(List.of());
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), isNull())).thenReturn(false);

        PlayerParticipationStatusDto result = service.getPlayerParticipationStatusByMonth(10L, 2025, 4);

        assertThat(result.getBeforeDeadline()).isFalse();
    }

    private PracticeParticipationRequest.SessionMatchParticipation createParticipation(Long sessionId, int matchNumber) {
        PracticeParticipationRequest.SessionMatchParticipation p = new PracticeParticipationRequest.SessionMatchParticipation();
        p.setSessionId(sessionId);
        p.setMatchNumber(matchNumber);
        return p;
    }
}
