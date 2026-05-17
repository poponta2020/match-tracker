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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(0L);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WAITLISTED))
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
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(0L);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WAITLISTED))
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
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(0L);
        when(practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WAITLISTED))
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
    @DisplayName("複数団体のセッションを含むリクエストが団体ごとに分割処理される")
    void registerParticipations_multipleOrgs_processedPerOrg() {
        Long orgId2 = 2L;
        PracticeSession session1 = createSession(100L, null);
        PracticeSession session2 = new PracticeSession();
        session2.setId(200L);
        session2.setCapacity(null);
        session2.setOrganizationId(orgId2);
        session2.setTotalMatches(7);

        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session1, session2));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.getDeadlineType(orgId2)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(true);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(orgId2))).thenReturn(true);
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID))
                .thenReturn(List.of(session1));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, orgId2))
                .thenReturn(List.of(session2));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(
                createParticipation(100L, 1),
                createParticipation(200L, 1)
        ));

        service.registerParticipations(request);

        // 各団体のensureが呼ばれる
        verify(organizationService).ensurePlayerBelongsToOrganization(10L, ORG_ID);
        verify(organizationService).ensurePlayerBelongsToOrganization(10L, orgId2);
        // 各団体ごとにsoft-deleteが呼ばれる
        verify(practiceParticipantRepository).softDeleteByPlayerIdAndSessionIds(eq(10L), eq(List.of(100L)), any());
        verify(practiceParticipantRepository).softDeleteByPlayerIdAndSessionIds(eq(10L), eq(List.of(200L)), any());
        // 各団体ごとにPENDINGで登録される
        verify(practiceParticipantRepository, times(2)).save(any(PracticeParticipant.class));
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
    @DisplayName("空リクエストで既存参加が解除される（CRITICAL回帰テスト）")
    void registerParticipations_emptyParticipations_clearsExistingRegistrations() {
        PracticeSession session = createSession(100L, null);

        when(playerRepository.existsById(10L)).thenReturn(true);
        // 月内セッションが存在する
        when(practiceSessionRepository.findByYearAndMonth(2025, 4)).thenReturn(List.of(session));
        // プレイヤーに既存のアクティブ参加がある
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(999L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.PENDING).build();
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(10L, List.of(100L)))
                .thenReturn(List.of(existing));
        // registerBeforeDeadline で使用
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(true);
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID))
                .thenReturn(List.of(session));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of()); // 空リスト

        service.registerParticipations(request);

        // 既存参加のsoft-deleteが実行される
        verify(practiceParticipantRepository).softDeleteByPlayerIdAndSessionIds(
                eq(10L), eq(List.of(100L)), any());
        // 新規登録はなし
        verify(practiceParticipantRepository, never()).save(any(PracticeParticipant.class));
        // ensureは呼ばれない（空リクエストなので）
        verify(organizationService, never()).ensurePlayerBelongsToOrganization(anyLong(), anyLong());
    }

    @Test
    @DisplayName("SAME_DAYタイプでクロス団体のセッションは差分処理の対象にならない")
    void sameDay_crossOrganization_doesNotTouchOtherOrg() {
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

        // 差分処理のスナップショット取得はORG_IDのセッション(100L)に限られる
        verify(practiceParticipantRepository).findByPlayerIdAndSessionIds(eq(10L), eq(List.of(100L)));
        // 団体2のセッション(200L)は触られない
        verify(practiceParticipantRepository, never())
                .findByPlayerIdAndSessionIds(eq(10L), argThat(ids -> ids.contains(200L)));
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

    @Test
    @DisplayName("getPlayerParticipationsByMonth はCANCELLED/DECLINED/WAITLIST_DECLINEDのレコードを除外する")
    void getPlayerParticipationsByMonth_excludesInactiveStatuses() {
        PracticeSession session = createSession(100L, 4);
        session.setSessionDate(LocalDate.of(2026, 5, 19));
        when(practiceSessionRepository.findByYearAndMonth(2026, 5)).thenReturn(List.of(session));

        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(10L, List.of(100L)))
                .thenReturn(List.of(
                        buildParticipant(100L, 10L, 1, ParticipantStatus.WON),
                        buildParticipant(100L, 10L, 2, ParticipantStatus.CANCELLED),
                        buildParticipant(100L, 10L, 3, ParticipantStatus.DECLINED),
                        buildParticipant(100L, 10L, 4, ParticipantStatus.WAITLIST_DECLINED),
                        buildParticipant(100L, 10L, 5, ParticipantStatus.WAITLISTED)
                ));

        java.util.Map<Long, List<Integer>> result = service.getPlayerParticipationsByMonth(10L, 2026, 5);

        // 一度キャンセルした試合(matchNumber=2)が再登録できるよう、登録済み扱いに含まれてはいけない
        assertThat(result).containsKey(100L);
        assertThat(result.get(100L)).containsExactlyInAnyOrder(1, 5);
    }

    private PracticeParticipant buildParticipant(Long sessionId, Long playerId, int matchNumber, ParticipantStatus status) {
        PracticeParticipant p = new PracticeParticipant();
        p.setSessionId(sessionId);
        p.setPlayerId(playerId);
        p.setMatchNumber(matchNumber);
        p.setStatus(status);
        return p;
    }

    @Test
    @DisplayName("SAME_DAY: 当日12:00以降に同月内の別日を新たに登録しても、既存WONには「今日参加します」通知が発火しない")
    void sameDay_existingWonNotReNotified_whenSavingAnotherDayInSameMonth() {
        // 5/17（当日）= 既存WON、5/19 = 新規参加。両方ともリクエストに含まれる。
        LocalDate today = LocalDate.of(2026, 5, 17);
        LocalDate later = LocalDate.of(2026, 5, 19);

        PracticeSession todaySession = createSession(100L, null);
        todaySession.setSessionDate(today);
        PracticeSession laterSession = createSession(200L, null);
        laterSession.setSessionDate(later);

        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(todaySession, laterSession));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, ORG_ID))
                .thenReturn(List.of(todaySession, laterSession));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        // softDelete前のスナップショット取得: 5/17 はWON、5/19 は未登録
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(eq(10L), eq(List.of(100L, 200L))))
                .thenReturn(List.of(buildParticipant(100L, 10L, 1, ParticipantStatus.WON)));
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(anyLong(), eq(10L), anyInt()))
                .thenReturn(List.of());
        // 5/17 は既存アクティブなので差分処理でno-op。5/19 は新規だが当日でないので isAfterSameDayNoon=false で早期return。
        when(lotteryDeadlineHelper.isAfterSameDayNoon(later)).thenReturn(false);

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2026);
        request.setMonth(5);
        request.setParticipations(List.of(
                createParticipation(100L, 1), // 既存WONはno-op
                createParticipation(200L, 1)  // 新規（5/19）
        ));

        service.registerParticipations(request);

        // 「今日参加します」通知は発火しないこと（既存WONはno-op、当日でない別日も対象外）
        verify(lineNotificationService, never())
                .sendSameDayJoinNotification(any(PracticeSession.class), anyInt(), anyString(), anyLong());

        // 既存WON（100:1）はsaveされない。新規（200:1）だけsaveされる。
        verify(practiceParticipantRepository, times(1)).save(any(PracticeParticipant.class));
        // 月単位softDeleteは使われない（差分処理化により）
        verify(practiceParticipantRepository, never())
                .softDeleteByPlayerIdAndSessionIds(anyLong(), anyList(), any());
    }

    @Test
    @DisplayName("SAME_DAY: リクエストにない既存アクティブは個別にCANCELLED化される")
    void sameDay_existingNotInRequest_cancelledIndividually() {
        PracticeSession session1 = createSession(100L, null);
        PracticeSession session2 = createSession(200L, null);

        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session1));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2025, 4, ORG_ID))
                .thenReturn(List.of(session1, session2));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        // 既存: 100:1 (WON), 200:1 (WON)。リクエストには 100:1 のみ → 200:1 はキャンセルされるはず
        PracticeParticipant existingKeep = PracticeParticipant.builder()
                .id(901L).sessionId(100L).playerId(10L).matchNumber(1).status(ParticipantStatus.WON).build();
        PracticeParticipant existingDrop = PracticeParticipant.builder()
                .id(902L).sessionId(200L).playerId(10L).matchNumber(1).status(ParticipantStatus.WON).build();
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(eq(10L), eq(List.of(100L, 200L))))
                .thenReturn(List.of(existingKeep, existingDrop));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        // 月単位softDeleteは呼ばれない
        verify(practiceParticipantRepository, never())
                .softDeleteByPlayerIdAndSessionIds(anyLong(), anyList(), any());
        // 200:1 だけが個別にsaveされる（CANCELLED 化）。100:1 はno-op。
        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant cancelledSaved = participantCaptor.getValue();
        assertThat(cancelledSaved.getId()).isEqualTo(902L);
        assertThat(cancelledSaved.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        assertThat(cancelledSaved.isDirty()).isTrue();
    }

    @Test
    @DisplayName("SAME_DAY: 当日12:00以降にキャンセル済みから新規WONになる場合は「今日参加します」通知が発火する")
    void sameDay_reactivateFromCancelled_firesJoinNotificationAfterNoon() {
        LocalDate today = LocalDate.of(2026, 5, 17);
        PracticeSession session = createSession(100L, null);
        session.setSessionDate(today);

        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, ORG_ID))
                .thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        // 既存はCANCELLEDなのでスナップショットのWONには含まれない
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(eq(10L), eq(List.of(100L))))
                .thenReturn(List.of(buildParticipant(100L, 10L, 1, ParticipantStatus.CANCELLED)));
        PracticeParticipant cancelled = PracticeParticipant.builder()
                .id(555L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.CANCELLED).dirty(false).build();
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(List.of(cancelled));
        when(lotteryDeadlineHelper.isAfterSameDayNoon(today)).thenReturn(true);
        com.karuta.matchtracker.entity.Player player = new com.karuta.matchtracker.entity.Player();
        player.setId(10L);
        player.setName("テスト太郎");
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2026);
        request.setMonth(5);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        // 通知発火を検証
        verify(lineNotificationService)
                .sendSameDayJoinNotification(eq(session), eq(1), eq("テスト太郎"), eq(10L));
    }
}
