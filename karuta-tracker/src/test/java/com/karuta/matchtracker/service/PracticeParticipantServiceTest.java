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

import java.time.LocalDate;
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
    @Mock
    private DensukeSyncService densukeSyncService;
    @Mock
    private com.karuta.matchtracker.repository.PlayerOrganizationRepository playerOrganizationRepository;
    @Mock
    private LineNotificationService lineNotificationService;
    @Mock
    private OrganizationService organizationService;

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
        s.setTotalMatches(7);
        return s;
    }

    @Test
    @DisplayName("締切後+抽選済み+定員超過の場合WAITLISTEDで登録される")
    void afterDeadline_overCapacity_waitlisted() {
        PracticeSession session = createSession(100L, 4);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(false);
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                2025, 4, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
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
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(false);
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                2025, 4, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
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
    @DisplayName("After deadline reuses cancelled record")
    void afterDeadline_reuseCancelledRecord() {
        PracticeSession session = createSession(100L, 4);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(false);
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                2025, 4, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
        when(practiceParticipantRepository.existsActiveBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(false);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                .thenReturn(2L);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WAITLISTED))
                .thenReturn(false);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(false);

        PracticeParticipant cancelled = PracticeParticipant.builder()
                .id(999L)
                .sessionId(100L)
                .playerId(10L)
                .matchNumber(1)
                .status(ParticipantStatus.CANCELLED)
                .waitlistNumber(5)
                .cancelReason("test")
                .dirty(false)
                .build();
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(List.of(cancelled));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant saved = participantCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(999L);
        assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(saved.getWaitlistNumber()).isNull();
        assertThat(saved.getCancelReason()).isNull();
        assertThat(saved.isDirty()).isTrue();
    }

    @Test
    @DisplayName("Before deadline ignores duplicate request entries")
    void beforeDeadline_duplicateInRequest_savedOnce() {
        PracticeSession session = createSession(100L, 4);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID)).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(List.of());

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(
                createParticipation(100L, 1),
                createParticipation(100L, 1)
        ));

        service.registerParticipations(request);

        verify(practiceParticipantRepository, times(1)).save(any(PracticeParticipant.class));
    }

    @Test
    @DisplayName("Same day reuses cancelled record")
    void sameDay_reuseCancelledRecord() {
        PracticeSession session = createSession(100L, null);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID)).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);

        PracticeParticipant cancelled = PracticeParticipant.builder()
                .id(555L)
                .sessionId(100L)
                .playerId(10L)
                .matchNumber(1)
                .status(ParticipantStatus.CANCELLED)
                .cancelReason("test")
                .dirty(false)
                .build();
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(List.of(cancelled));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant saved = participantCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(555L);
        assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(saved.getCancelReason()).isNull();
        assertThat(saved.isDirty()).isTrue();
    }

    @Test
    @DisplayName("Same day ignores duplicate request entries")
    void sameDay_duplicateInRequest_savedOnce() {
        PracticeSession session = createSession(100L, null);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID)).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(List.of());

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(
                createParticipation(100L, 1),
                createParticipation(100L, 1)
        ));

        service.registerParticipations(request);

        verify(practiceParticipantRepository, times(1)).save(any(PracticeParticipant.class));
    }

    @Test
    @DisplayName("After deadline ignores duplicate request entries")
    void afterDeadline_duplicateInRequest_savedOnce() {
        PracticeSession session = createSession(100L, null);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(false);
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                2025, 4, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
        when(practiceParticipantRepository.existsActiveBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(false);
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(List.of());

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(
                createParticipation(100L, 1),
                createParticipation(100L, 1)
        ));

        service.registerParticipations(request);

        verify(practiceParticipantRepository, times(1)).save(any(PracticeParticipant.class));
    }

    @Test
    @DisplayName("Set match participants reuses cancelled record")
    void setMatchParticipants_reuseCancelledRecord() {
        PracticeSession session = createSession(100L, 4);
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(playerRepository.findAllById(any())).thenReturn(List.of(mock(com.karuta.matchtracker.entity.Player.class)));

        PracticeParticipant cancelled = PracticeParticipant.builder()
                .id(777L)
                .sessionId(100L)
                .playerId(10L)
                .matchNumber(2)
                .status(ParticipantStatus.CANCELLED)
                .dirty(false)
                .build();
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 2))
                .thenReturn(List.of(cancelled));

        service.setMatchParticipants(100L, 2, List.of(10L, 10L));

        verify(playerRepository).findAllById(List.of(10L));
        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant saved = participantCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(777L);
        assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(saved.isDirty()).isTrue();
    }

    @Test
    @DisplayName("Add participant to match reuses cancelled record")
    void addParticipantToMatch_reuseCancelledRecord() {
        LocalDate date = LocalDate.of(2025, 4, 5);
        PracticeSession session = createSession(100L, 4);
        session.setSessionDate(date);
        when(practiceSessionRepository.findBySessionDate(date)).thenReturn(Optional.of(session));
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceParticipantRepository.existsActiveBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 2))
                .thenReturn(false);

        PracticeParticipant cancelled = PracticeParticipant.builder()
                .id(888L)
                .sessionId(100L)
                .playerId(10L)
                .matchNumber(2)
                .status(ParticipantStatus.CANCELLED)
                .dirty(false)
                .build();
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 2))
                .thenReturn(List.of(cancelled));

        service.addParticipantToMatch(date, 2, 10L);

        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant saved = participantCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(888L);
        assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
        verify(densukeSyncService).triggerWriteAsync();
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

    @Test
    @DisplayName("参加登録時にensurePlayerBelongsToOrganizationが該当団体で呼ばれる")
    void registerParticipations_callsEnsureForOrganization() {
        PracticeSession session = createSession(100L, 10);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                .thenReturn(0L);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WAITLISTED))
                .thenReturn(false);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(false);

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        verify(organizationService).ensurePlayerBelongsToOrganization(10L, ORG_ID);
    }

    @Test
    @DisplayName("複数団体のセッションに参加登録した場合、団体ごとにensureが呼ばれ、リクエスト先頭セッションの団体で締切判定される")
    void registerParticipations_multipleOrgs_callsEnsureForEach() {
        Long orgId2 = 2L;
        PracticeSession session1 = createSession(100L, null);
        PracticeSession session2 = new PracticeSession();
        session2.setId(200L);
        session2.setCapacity(null);
        session2.setOrganizationId(orgId2);
        session2.setTotalMatches(7);

        when(playerRepository.existsById(10L)).thenReturn(true);
        // findAllByIdの戻り順をリクエスト順と逆にして、決定性を検証
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session2, session1));
        // リクエスト先頭のセッション100(ORG_ID=1)が締切判定に使われることを検証
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID)).thenReturn(List.of(session1));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(
                createParticipation(100L, 1),
                createParticipation(200L, 1)
        ));

        service.registerParticipations(request);

        // リクエスト先頭セッションの団体(ORG_ID)で締切判定が呼ばれたことを検証
        verify(lotteryDeadlineHelper).getDeadlineType(ORG_ID);
        verify(organizationService).ensurePlayerBelongsToOrganization(10L, ORG_ID);
        verify(organizationService).ensurePlayerBelongsToOrganization(10L, orgId2);
        verify(organizationService, times(2)).ensurePlayerBelongsToOrganization(anyLong(), anyLong());
    }

    @Test
    @DisplayName("参加登録が空リストの場合ensureは呼ばれない")
    void registerParticipations_emptyParticipations_noEnsureCall() {
        when(playerRepository.existsById(10L)).thenReturn(true);

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of());

        service.registerParticipations(request);

        verify(organizationService, never()).ensurePlayerBelongsToOrganization(anyLong(), anyLong());
    }

    @Test
    @DisplayName("SAME_DAYタイプでクロス団体のセッションがsoft-deleteされないこと")
    void sameDay_crossOrganization_doesNotSoftDeleteOtherOrg() {
        Long orgId2 = 2L;
        PracticeSession session1 = createSession(100L, null); // ORG_ID=1
        PracticeSession session2 = new PracticeSession();
        session2.setId(200L);
        session2.setCapacity(null);
        session2.setOrganizationId(orgId2);
        session2.setTotalMatches(7);

        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session1));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        // 団体1のセッションのみ返す（団体2のセッションは含まない）
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID))
                .thenReturn(List.of(session1));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        // soft-deleteはORG_IDのセッション(100L)のみに対して呼ばれる
        verify(practiceParticipantRepository).softDeleteByPlayerIdAndSessionIds(
                eq(10L), eq(List.of(100L)), any());
        // 団体2のセッション(200L)はsoft-deleteに含まれない
        verify(practiceParticipantRepository, never()).softDeleteByPlayerIdAndSessionIds(
                eq(10L), argThat(ids -> ids.contains(200L)), any());
    }

    @Test
    @DisplayName("締切前タイプでクロス団体のセッションがsoft-deleteされないこと")
    void beforeDeadline_crossOrganization_doesNotSoftDeleteOtherOrg() {
        Long orgId2 = 2L;
        PracticeSession session1 = createSession(100L, null); // ORG_ID=1
        PracticeSession session2 = new PracticeSession();
        session2.setId(200L);
        session2.setCapacity(null);
        session2.setOrganizationId(orgId2);
        session2.setTotalMatches(7);

        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session1));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(true);
        // 団体1のセッションのみ返す
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID))
                .thenReturn(List.of(session1));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        // soft-deleteはORG_IDのセッション(100L)のみに対して呼ばれる
        verify(practiceParticipantRepository).softDeleteByPlayerIdAndSessionIds(
                eq(10L), eq(List.of(100L)), any());
        // 団体2のセッション(200L)はsoft-deleteに含まれない
        verify(practiceParticipantRepository, never()).softDeleteByPlayerIdAndSessionIds(
                eq(10L), argThat(ids -> ids.contains(200L)), any());
    }

    private PracticeParticipationRequest.SessionMatchParticipation createParticipation(Long sessionId, int matchNumber) {
        PracticeParticipationRequest.SessionMatchParticipation p = new PracticeParticipationRequest.SessionMatchParticipation();
        p.setSessionId(sessionId);
        p.setMatchNumber(matchNumber);
        return p;
    }
}
