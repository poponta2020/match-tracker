package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PracticeParticipationRequest;
import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.DensukeDeletionCandidateRepository;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import com.karuta.matchtracker.util.JstDateTimeUtil;

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
    @Mock
    private DensukeDeletionCandidateRepository densukeDeletionCandidateRepository;

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
        s.setSessionDate(LocalDate.of(2025, 4, 1));
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
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                2025, 4, ORG_ID, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
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
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                2025, 4, ORG_ID, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
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
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                2025, 4, ORG_ID, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
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
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                2025, 4, ORG_ID, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);
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
    @DisplayName("A-1: setMatchParticipants は既存 CANCELLED を WON へ復活させない（編集対象外）")
    void setMatchParticipants_doesNotReviveCancelled() {
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
        // 実DBと同様、(session, match) 走査と (session, player, match) 走査で同じ CANCELLED 行が見える
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 2))
                .thenReturn(List.of(cancelled));

        service.setMatchParticipants(100L, 2, List.of(10L));

        verify(playerRepository).findAllById(List.of(10L));
        // 非アクティブ(CANCELLED)は編集対象外 → WON へ昇格せず、保存もされない（× の復活を防ぐ）
        assertThat(cancelled.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("A-1: setMatchParticipants は既存 WAITLISTED/OFFERED を WON へ昇格させない（編集対象外）")
    void setMatchParticipants_doesNotPromoteWaitlistedOrOffered() {
        PracticeSession session = createSession(100L, 4);
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(playerRepository.findAllById(any()))
                .thenReturn(List.of(mock(com.karuta.matchtracker.entity.Player.class),
                        mock(com.karuta.matchtracker.entity.Player.class)));

        PracticeParticipant waitlisted = buildParticipant(100L, 50L, 2, ParticipantStatus.WAITLISTED);
        waitlisted.setId(1L);
        PracticeParticipant offered = buildParticipant(100L, 60L, 2, ParticipantStatus.OFFERED);
        offered.setId(2L);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 2))
                .thenReturn(List.of(waitlisted, offered));

        service.setMatchParticipants(100L, 2, List.of(50L, 60L));

        // 待機/応答待ちは編集対象外 → WON へ昇格せず据え置き（抽選なしWON昇格を防ぐ）
        assertThat(waitlisted.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(offered.getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("A-1: setMatchParticipants は WAITLISTED/OFFERED を温存し、外れた WON のみ CANCELLED にする")
    void setMatchParticipants_preservesWaitlistedAndOffered() {
        PracticeSession session = createSession(100L, 4);
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(playerRepository.findAllById(List.of(10L)))
                .thenReturn(List.of(mock(com.karuta.matchtracker.entity.Player.class)));

        PracticeParticipant wonRemoved = buildParticipant(100L, 99L, 2, ParticipantStatus.WON);
        wonRemoved.setId(1L);
        PracticeParticipant waitlisted = buildParticipant(100L, 50L, 2, ParticipantStatus.WAITLISTED);
        waitlisted.setId(2L);
        PracticeParticipant offered = buildParticipant(100L, 60L, 2, ParticipantStatus.OFFERED);
        offered.setId(3L);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 2))
                .thenReturn(List.of(wonRemoved, waitlisted, offered));
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 2))
                .thenReturn(List.of());

        service.setMatchParticipants(100L, 2, List.of(10L));

        // 外れた WON は CANCELLED、WAITLISTED/OFFERED は温存（伝助の×/△を巻き込まない）
        assertThat(wonRemoved.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);
        assertThat(waitlisted.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(offered.getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        // WAITLISTED/OFFERED は保存されない（温存）
        verify(practiceParticipantRepository, never()).save(waitlisted);
        verify(practiceParticipantRepository, never()).save(offered);
    }

    @Test
    @DisplayName("A-2: 締切後+抽選未実行の新規登録は PENDING で登録される")
    void afterDeadline_lotteryNotExecuted_pending() {
        PracticeSession session = createSession(100L, 4);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2025), eq(4), eq(ORG_ID))).thenReturn(false);
        // 当該団体・全団体一括ともに抽選未実行 → PENDING
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                2025, 4, ORG_ID, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(false);
        when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndOrganizationIdIsNullAndStatus(
                2025, 4, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(false);
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1))
                .thenReturn(List.of());

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));

        service.registerParticipations(request);

        verify(practiceParticipantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getStatus()).isEqualTo(ParticipantStatus.PENDING);
        // A-2 団体スコープ: 月全体ではなく当該団体（＋全団体一括）の抽選のみを実行済み判定に使う。
        // 別団体の抽選SUCCESSで当該団体が未抽選なのに即WON化しないことを担保する。
        verify(lotteryExecutionRepository).existsByTargetYearAndTargetMonthAndOrganizationIdAndStatus(
                2025, 4, ORG_ID, LotteryExecution.ExecutionStatus.SUCCESS);
        verify(lotteryExecutionRepository, never()).existsByTargetYearAndTargetMonthAndStatus(
                eq(2025), eq(4), any());
    }

    @Test
    @DisplayName("B-4: expectedVersion 不一致なら ConflictStateException(409) で保存を中止する")
    void registerParticipations_versionMismatch_conflict() {
        when(playerRepository.existsById(10L)).thenReturn(true);
        PracticeSession session = createSession(100L, 4);
        session.setSessionDate(LocalDate.of(2025, 4, 5));
        when(practiceSessionRepository.findByYearAndMonth(2025, 4)).thenReturn(List.of(session));
        PracticeParticipant existing = buildParticipant(100L, 10L, 1, ParticipantStatus.WON);
        existing.setId(1L);
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(eq(10L), anyList()))
                .thenReturn(List.of(existing));

        PracticeParticipationRequest request = new PracticeParticipationRequest();
        request.setPlayerId(10L);
        request.setYear(2025);
        request.setMonth(4);
        request.setParticipations(List.of(createParticipation(100L, 1)));
        request.setExpectedVersion("stale-version");

        assertThrows(com.karuta.matchtracker.exception.ConflictStateException.class,
                () -> service.registerParticipations(request));
        verify(practiceParticipantRepository, never()).save(any());
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

        // organizationId=null（SUPER_ADMIN 相当・スコープ非限定）は従来どおり日付のみでセッション特定
        service.addParticipantToMatch(date, 2, 10L, null);

        verify(practiceParticipantRepository).save(participantCaptor.capture());
        PracticeParticipant saved = participantCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(888L);
        assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
        verify(densukeSyncService).triggerWriteAsync();
    }

    @Test
    @DisplayName("Add participant to match with organizationId scopes session lookup by org")
    void addParticipantToMatch_organizationScopedLookup() {
        // Given: 同日に複数団体のセッションがあっても、organizationId で対象団体のセッションだけを特定する
        LocalDate date = LocalDate.of(2025, 4, 5);
        Long orgId = 7L;
        PracticeSession session = createSession(200L, 4);
        session.setSessionDate(date);
        session.setOrganizationId(orgId);
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(date, orgId))
                .thenReturn(Optional.of(session));
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceParticipantRepository.existsActiveBySessionIdAndPlayerIdAndMatchNumber(200L, 10L, 2))
                .thenReturn(false);
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(200L, 10L, 2))
                .thenReturn(List.of());

        // When
        service.addParticipantToMatch(date, 2, 10L, orgId);

        // Then: 団体スコープ取得のみが使われ、日付のみ取得(findBySessionDate)は使われない
        verify(practiceSessionRepository).findBySessionDateAndOrganizationId(date, orgId);
        verify(practiceSessionRepository, never()).findBySessionDate(any());
        verify(practiceParticipantRepository).save(participantCaptor.capture());
        assertThat(participantCaptor.getValue().getSessionId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("伝助側で削除が承認された試合には addParticipantToMatch で登録できない")
    void addParticipantToMatch_rejectsApprovedDensukeDeletion() {
        LocalDate date = LocalDate.of(2025, 4, 5);
        PracticeSession session = createSession(100L, 4);
        session.setSessionDate(date);
        when(practiceSessionRepository.findBySessionDate(date)).thenReturn(Optional.of(session));

        com.karuta.matchtracker.entity.DensukeDeletionCandidate approved =
                com.karuta.matchtracker.entity.DensukeDeletionCandidate.builder()
                        .id(1L).organizationId(ORG_ID).sessionDate(date).matchNumber(2)
                        .status(com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status.APPROVED)
                        .build();
        when(densukeDeletionCandidateRepository.findByOrganizationIdAndSessionDateAndStatus(
                ORG_ID, date, com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status.APPROVED))
                .thenReturn(List.of(approved));

        assertThrows(IllegalArgumentException.class,
                () -> service.addParticipantToMatch(date, 2, 10L, null));

        verify(practiceParticipantRepository, never()).save(any());
        verifyNoInteractions(densukeSyncService);
    }

    @Test
    @DisplayName("伝助側で削除が承認された試合には setMatchParticipants で登録できない")
    void setMatchParticipants_rejectsApprovedDensukeDeletion() {
        LocalDate date = LocalDate.of(2025, 4, 5);
        PracticeSession session = createSession(100L, 4);
        session.setSessionDate(date);
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        com.karuta.matchtracker.entity.DensukeDeletionCandidate approved =
                com.karuta.matchtracker.entity.DensukeDeletionCandidate.builder()
                        .id(1L).organizationId(ORG_ID).sessionDate(date).matchNumber(2)
                        .status(com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status.APPROVED)
                        .build();
        when(densukeDeletionCandidateRepository.findByOrganizationIdAndSessionDateAndStatus(
                ORG_ID, date, com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status.APPROVED))
                .thenReturn(List.of(approved));

        assertThrows(IllegalArgumentException.class,
                () -> service.setMatchParticipants(100L, 2, List.of(10L)));

        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("伝助側で削除が承認された試合には registerParticipations で登録できない")
    void registerParticipations_rejectsApprovedDensukeDeletion() {
        LocalDate date = LocalDate.of(2025, 4, 10);
        PracticeSession session = createSession(300L, 4);
        session.setSessionDate(date);
        when(playerRepository.existsById(10L)).thenReturn(true);
        when(practiceSessionRepository.findAllById(List.of(300L))).thenReturn(List.of(session));

        com.karuta.matchtracker.entity.DensukeDeletionCandidate approved =
                com.karuta.matchtracker.entity.DensukeDeletionCandidate.builder()
                        .id(1L).organizationId(ORG_ID).sessionDate(date).matchNumber(2)
                        .status(com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status.APPROVED)
                        .build();
        when(densukeDeletionCandidateRepository.findByOrganizationIdInAndSessionDateBetweenAndStatus(
                List.of(ORG_ID), date, date, com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status.APPROVED))
                .thenReturn(List.of(approved));

        PracticeParticipationRequest request = PracticeParticipationRequest.builder()
                .playerId(10L).year(2025).month(4)
                .participations(List.of(
                        PracticeParticipationRequest.SessionMatchParticipation.builder()
                                .sessionId(300L).matchNumber(2).build()))
                .build();

        assertThrows(IllegalArgumentException.class, () -> service.registerParticipations(request));

        verify(practiceParticipantRepository, never()).save(any());
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
        session2.setSessionDate(LocalDate.of(2025, 4, 1));

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
        session2.setSessionDate(LocalDate.of(2025, 4, 1));

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
        session2.setSessionDate(LocalDate.of(2025, 4, 1));

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

    @Test
    @DisplayName("当月扱い: 既存アクティブ参加がリクエストに含まれていない場合は IllegalArgumentException（API直叩きでの理由なしキャンセルを拒否）")
    void currentMonth_missingActiveEntry_throwsIllegalArgument() {
        LocalDate fixedToday = LocalDate.of(2026, 5, 15);
        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(fixedToday);

            PracticeSession session = createSession(100L, 4);
            session.setSessionDate(LocalDate.of(2026, 5, 25));

            when(playerRepository.existsById(10L)).thenReturn(true);
            when(practiceSessionRepository.findByYearAndMonth(2026, 5)).thenReturn(List.of(session));
            // 既存: 第1, 第2試合がアクティブ（PENDING）
            when(practiceParticipantRepository.findByPlayerIdAndSessionIds(eq(10L), eq(List.of(100L))))
                    .thenReturn(List.of(
                            buildParticipant(100L, 10L, 1, ParticipantStatus.PENDING),
                            buildParticipant(100L, 10L, 2, ParticipantStatus.PENDING)
                    ));

            PracticeParticipationRequest request = new PracticeParticipationRequest();
            request.setPlayerId(10L);
            request.setYear(2026);
            request.setMonth(5);
            // 第1のみ含む（第2は削除差分 → 拒否されるべき）
            request.setParticipations(List.of(createParticipation(100L, 1)));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.registerParticipations(request));
            assertThat(ex.getMessage()).contains("matchNumber=2");
            // softDelete は一度も呼ばれないこと（拒否で処理打ち切り）
            verify(practiceParticipantRepository, never()).softDeleteByPlayerIdAndSessionIds(any(), any(), any());
        }
    }

    @Test
    @DisplayName("未来月+抽選確定済み: 既存アクティブ参加の削除差分があれば IllegalArgumentException（当月扱いに昇格）")
    void futureMonth_lotteryExecuted_missingActiveEntry_throwsIllegalArgument() {
        LocalDate fixedToday = LocalDate.of(2026, 5, 15);
        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(fixedToday);
            // 翌月（2026年6月）に抽選確定済みあり → 当月扱いに昇格
            when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                    2026, 6, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(true);

            PracticeSession session = createSession(200L, 4);
            session.setSessionDate(LocalDate.of(2026, 6, 10));

            when(playerRepository.existsById(10L)).thenReturn(true);
            when(practiceSessionRepository.findByYearAndMonth(2026, 6)).thenReturn(List.of(session));
            when(practiceParticipantRepository.findByPlayerIdAndSessionIds(eq(10L), eq(List.of(200L))))
                    .thenReturn(List.of(
                            buildParticipant(200L, 10L, 1, ParticipantStatus.WON)
                    ));

            PracticeParticipationRequest request = new PracticeParticipationRequest();
            request.setPlayerId(10L);
            request.setYear(2026);
            request.setMonth(6);
            // 空リクエストで第1試合を削除しようとする
            request.setParticipations(List.of());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.registerParticipations(request));
            assertThat(ex.getMessage()).contains("matchNumber=1");
        }
    }

    @Test
    @DisplayName("未来月+抽選確定なし: 削除差分があっても通常処理（来月扱いのため理由なしキャンセル可）")
    void futureMonth_noLotteryExecuted_missingActiveEntry_allowed() {
        LocalDate fixedToday = LocalDate.of(2026, 5, 15);
        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(fixedToday);
            // 翌月（2026年6月）に抽選確定済みなし → 来月扱い
            when(lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                    2026, 6, LotteryExecution.ExecutionStatus.SUCCESS)).thenReturn(false);

            when(playerRepository.existsById(10L)).thenReturn(true);

            PracticeParticipationRequest request = new PracticeParticipationRequest();
            request.setPlayerId(10L);
            request.setYear(2026);
            request.setMonth(6);
            // 空リクエストでの削除も来月扱いなら許可される（validate を通過）
            request.setParticipations(List.of());

            // 例外がスローされないことを検証（registerParticipations 本体は空リクエストで no-op）
            service.registerParticipations(request);
        }
    }

    @Test
    @DisplayName("getPlayerParticipationStatusByMonth: 月次抽選 SUCCESS (organizationId=null) は月内の全セッションで lotteryExecuted=true、hasAnyExecutedLotteryInMonth=true")
    void getPlayerParticipationStatusByMonth_monthlyLotteryAllOrgs_marksAllSessions() {
        PracticeSession session1 = createSession(100L, 4);
        session1.setSessionDate(LocalDate.of(2026, 6, 10));
        PracticeSession session2 = createSession(200L, 4);
        session2.setSessionDate(LocalDate.of(2026, 6, 15));

        LotteryExecution exec = new LotteryExecution();
        exec.setSessionId(null);
        exec.setOrganizationId(null);
        exec.setStatus(LotteryExecution.ExecutionStatus.SUCCESS);

        when(practiceSessionRepository.findByYearAndMonth(2026, 6))
                .thenReturn(List.of(session1, session2));
        when(lotteryExecutionRepository.findByTargetYearAndTargetMonth(2026, 6))
                .thenReturn(List.of(exec));
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(10L, List.of(100L, 200L)))
                .thenReturn(List.of());
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2026), eq(6), eq(ORG_ID)))
                .thenReturn(false);

        PlayerParticipationStatusDto dto = service.getPlayerParticipationStatusByMonth(10L, 2026, 6);

        // 月次抽選 SUCCESS で全 organization → 月内全セッションをロック
        assertThat(dto.getLotteryExecuted()).containsEntry(100L, true);
        assertThat(dto.getLotteryExecuted()).containsEntry(200L, true);
        assertThat(dto.getHasAnyExecutedLotteryInMonth()).isTrue();
    }

    @Test
    @DisplayName("getPlayerParticipationStatusByMonth: 月次抽選 SUCCESS (organizationId 指定) は同じ団体のセッションのみ lotteryExecuted=true")
    void getPlayerParticipationStatusByMonth_monthlyLotteryPerOrg_marksOnlyThatOrg() {
        // session1 は ORG_ID=1（抽選対象）、session2 は他団体（抽選なし）
        PracticeSession session1 = createSession(100L, 4); // organizationId = ORG_ID
        session1.setSessionDate(LocalDate.of(2026, 6, 10));
        PracticeSession session2 = createSession(200L, 4);
        session2.setOrganizationId(99L); // 別団体
        session2.setSessionDate(LocalDate.of(2026, 6, 15));

        LotteryExecution exec = new LotteryExecution();
        exec.setSessionId(null);
        exec.setOrganizationId(ORG_ID); // ORG_ID の月次抽選のみ
        exec.setStatus(LotteryExecution.ExecutionStatus.SUCCESS);

        when(practiceSessionRepository.findByYearAndMonth(2026, 6))
                .thenReturn(List.of(session1, session2));
        when(lotteryExecutionRepository.findByTargetYearAndTargetMonth(2026, 6))
                .thenReturn(List.of(exec));
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(10L, List.of(100L, 200L)))
                .thenReturn(List.of());
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2026), eq(6), eq(ORG_ID)))
                .thenReturn(false);

        PlayerParticipationStatusDto dto = service.getPlayerParticipationStatusByMonth(10L, 2026, 6);

        // 同じ団体（ORG_ID）の session1 のみ true、別団体の session2 は false
        assertThat(dto.getLotteryExecuted()).containsEntry(100L, true);
        assertThat(dto.getLotteryExecuted()).containsEntry(200L, false);
        assertThat(dto.getHasAnyExecutedLotteryInMonth()).isTrue();
    }

    @Test
    @DisplayName("getPlayerParticipationStatusByMonth: セッション単位の再抽選 SUCCESS は当該セッションのみ lotteryExecuted=true、hasAnyExecutedLotteryInMonth=true")
    void getPlayerParticipationStatusByMonth_sessionRelottery_marksOnlyThatSession() {
        PracticeSession session1 = createSession(100L, 4);
        session1.setSessionDate(LocalDate.of(2026, 6, 10));
        PracticeSession session2 = createSession(200L, 4);
        session2.setSessionDate(LocalDate.of(2026, 6, 15));

        LotteryExecution exec = new LotteryExecution();
        exec.setSessionId(100L);
        exec.setStatus(LotteryExecution.ExecutionStatus.SUCCESS);

        when(practiceSessionRepository.findByYearAndMonth(2026, 6))
                .thenReturn(List.of(session1, session2));
        when(lotteryExecutionRepository.findByTargetYearAndTargetMonth(2026, 6))
                .thenReturn(List.of(exec));
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(10L, List.of(100L, 200L)))
                .thenReturn(List.of());
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2026), eq(6), eq(ORG_ID)))
                .thenReturn(true);

        PlayerParticipationStatusDto dto = service.getPlayerParticipationStatusByMonth(10L, 2026, 6);

        // session1 のみ true（個別セッションロック）、session2 は未抽選で false
        assertThat(dto.getLotteryExecuted()).containsEntry(100L, true);
        assertThat(dto.getLotteryExecuted()).containsEntry(200L, false);
        assertThat(dto.getHasAnyExecutedLotteryInMonth()).isTrue();
    }

    @Test
    @DisplayName("getPlayerParticipationStatusByMonth: 抽選 SUCCESS レコードがない場合は lotteryExecuted 全 false、hasAnyExecutedLotteryInMonth=false")
    void getPlayerParticipationStatusByMonth_noLottery_marksAllFalse() {
        PracticeSession session = createSession(300L, 4);
        session.setSessionDate(LocalDate.of(2026, 7, 5));

        when(practiceSessionRepository.findByYearAndMonth(2026, 7))
                .thenReturn(List.of(session));
        when(lotteryExecutionRepository.findByTargetYearAndTargetMonth(2026, 7))
                .thenReturn(List.of());
        when(practiceParticipantRepository.findByPlayerIdAndSessionIds(10L, List.of(300L)))
                .thenReturn(List.of());
        when(lotteryDeadlineHelper.isBeforeDeadline(eq(2026), eq(7), eq(ORG_ID)))
                .thenReturn(true);

        PlayerParticipationStatusDto dto = service.getPlayerParticipationStatusByMonth(10L, 2026, 7);

        assertThat(dto.getLotteryExecuted()).containsEntry(300L, false);
        assertThat(dto.getHasAnyExecutedLotteryInMonth()).isFalse();
    }

    @Test
    @DisplayName("当月扱い: 削除差分がない（追加のみ）リクエストは通常通り処理される")
    void currentMonth_noMissingEntry_processedNormally() {
        LocalDate fixedToday = LocalDate.of(2026, 5, 15);
        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(fixedToday);
            jstMock.when(JstDateTimeUtil::now).thenReturn(fixedToday.atTime(12, 0));

            PracticeSession session = createSession(100L, 4);
            session.setSessionDate(LocalDate.of(2026, 5, 25));

            when(playerRepository.existsById(10L)).thenReturn(true);
            when(practiceSessionRepository.findByYearAndMonth(2026, 5)).thenReturn(List.of(session));
            // 既存: 第1試合がアクティブ
            when(practiceParticipantRepository.findByPlayerIdAndSessionIds(eq(10L), eq(List.of(100L))))
                    .thenReturn(List.of(
                            buildParticipant(100L, 10L, 1, ParticipantStatus.PENDING)
                    ));
            when(practiceSessionRepository.findAllById(any())).thenReturn(List.of(session));
            when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, ORG_ID))
                    .thenReturn(List.of(session));
            when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
            when(lotteryDeadlineHelper.isBeforeDeadline(eq(2026), eq(5), eq(ORG_ID))).thenReturn(true);

            PracticeParticipationRequest request = new PracticeParticipationRequest();
            request.setPlayerId(10L);
            request.setYear(2026);
            request.setMonth(5);
            // 第1（既存）と第2（追加）の両方をリクエスト → 削除差分なし
            request.setParticipations(List.of(
                    createParticipation(100L, 1),
                    createParticipation(100L, 2)
            ));

            // 例外がスローされないことを検証
            service.registerParticipations(request);
        }
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

    @Test
    @DisplayName("参加率グループ(団体別): 全試合WON+抜け番(matchNumber=null)でも各セッションで予定試合数を上限にキャップし100%を超えない")
    void getParticipationGroups_byeAndFullMatches_cappedAt100Percent() {
        LocalDate today = LocalDate.of(2026, 6, 19);
        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(today);

            PracticeSession session = createSession(100L, 7); // totalMatches=7
            session.setSessionDate(LocalDate.of(2026, 6, 10));

            when(practiceSessionRepository.findByOrganizationIdInAndYearAndMonth(List.of(ORG_ID), 2026, 6))
                    .thenReturn(List.of(session));
            when(playerOrganizationRepository.findByOrganizationIdIn(List.of(ORG_ID)))
                    .thenReturn(List.of(com.karuta.matchtracker.entity.PlayerOrganization.builder()
                            .playerId(10L).organizationId(ORG_ID).build()));

            // 全7試合WON + 抜け番(matchNumber=null, WON) = 8行
            java.util.List<PracticeParticipant> participants = new java.util.ArrayList<>();
            for (int m = 1; m <= 7; m++) {
                participants.add(buildParticipant(100L, 10L, m, ParticipantStatus.WON));
            }
            participants.add(PracticeParticipant.builder()
                    .sessionId(100L).playerId(10L).matchNumber(null).status(ParticipantStatus.WON).build());
            when(practiceParticipantRepository.findBySessionIdIn(List.of(100L)))
                    .thenReturn(participants);

            com.karuta.matchtracker.entity.Player player = new com.karuta.matchtracker.entity.Player();
            player.setId(10L);
            player.setName("白石新菜");
            when(playerRepository.findAllActive()).thenReturn(List.of(player));

            java.util.List<com.karuta.matchtracker.dto.ParticipationGroupDto> groups =
                    service.getParticipationGroups(10L, 2026, 6,
                            List.of(com.karuta.matchtracker.dto.OrganizationDto.builder()
                                    .id(ORG_ID).name("テスト団体").build()));

            // 1団体所属 → 「全体」グループなしでその団体のみ
            assertThat(groups).hasSize(1);
            java.util.List<com.karuta.matchtracker.dto.ParticipationRateDto> top3 = groups.get(0).getTop3();
            assertThat(top3).hasSize(1);
            // 8行あっても totalMatches=7 でキャップ
            assertThat(top3.get(0).getParticipatedMatches()).isEqualTo(7);
            assertThat(top3.get(0).getTotalScheduledMatches()).isEqualTo(7);
            assertThat(top3.get(0).getRate()).isEqualTo(1.0); // 114%ではなく100%
            // myRate は同一データから引き当てられる
            assertThat(groups.get(0).getMyRate()).isNotNull();
            assertThat(groups.get(0).getMyRate().getRate()).isEqualTo(1.0);
        }
    }

    @Test
    @DisplayName("参加率グループ(団体別): CANCELLED/WAITLISTED/DECLINED 等の無効ステータスは分子に含めない")
    void getParticipationGroups_excludesInactiveStatuses() {
        LocalDate today = LocalDate.of(2026, 6, 19);
        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(today);

            PracticeSession session = createSession(100L, 7); // totalMatches=7
            session.setSessionDate(LocalDate.of(2026, 6, 10));

            when(practiceSessionRepository.findByOrganizationIdInAndYearAndMonth(List.of(ORG_ID), 2026, 6))
                    .thenReturn(List.of(session));
            when(playerOrganizationRepository.findByOrganizationIdIn(List.of(ORG_ID)))
                    .thenReturn(List.of(com.karuta.matchtracker.entity.PlayerOrganization.builder()
                            .playerId(10L).organizationId(ORG_ID).build()));

            // 有効(WON×3, PENDING×1)=4、無効(CANCELLED/WAITLISTED/DECLINED)=3
            when(practiceParticipantRepository.findBySessionIdIn(List.of(100L)))
                    .thenReturn(List.of(
                            buildParticipant(100L, 10L, 1, ParticipantStatus.WON),
                            buildParticipant(100L, 10L, 2, ParticipantStatus.WON),
                            buildParticipant(100L, 10L, 3, ParticipantStatus.WON),
                            buildParticipant(100L, 10L, 4, ParticipantStatus.PENDING),
                            buildParticipant(100L, 10L, 5, ParticipantStatus.CANCELLED),
                            buildParticipant(100L, 10L, 6, ParticipantStatus.WAITLISTED),
                            buildParticipant(100L, 10L, 7, ParticipantStatus.DECLINED)
                    ));

            com.karuta.matchtracker.entity.Player player = new com.karuta.matchtracker.entity.Player();
            player.setId(10L);
            player.setName("白石新菜");
            when(playerRepository.findAllActive()).thenReturn(List.of(player));

            java.util.List<com.karuta.matchtracker.dto.ParticipationGroupDto> groups =
                    service.getParticipationGroups(10L, 2026, 6,
                            List.of(com.karuta.matchtracker.dto.OrganizationDto.builder()
                                    .id(ORG_ID).name("テスト団体").build()));

            assertThat(groups).hasSize(1);
            java.util.List<com.karuta.matchtracker.dto.ParticipationRateDto> top3 = groups.get(0).getTop3();
            assertThat(top3).hasSize(1);
            assertThat(top3.get(0).getParticipatedMatches()).isEqualTo(4); // 有効4のみ
            assertThat(top3.get(0).getTotalScheduledMatches()).isEqualTo(7);
            assertThat(top3.get(0).getRate()).isEqualTo(4.0 / 7.0);
        }
    }

    @Test
    @DisplayName("参加率グループ(複数団体): 全体+各団体の3グループを構築し、月間データのロードは各リポジトリ1回だけ")
    void getParticipationGroups_multiOrg_buildsAllGroupsWithSingleLoad() {
        LocalDate today = LocalDate.of(2026, 6, 19);
        Long org2Id = 2L;
        try (MockedStatic<JstDateTimeUtil> jstMock = mockStatic(JstDateTimeUtil.class)) {
            jstMock.when(JstDateTimeUtil::today).thenReturn(today);

            // 団体1のセッション(100L)と団体2のセッション(200L)、各 totalMatches=7
            PracticeSession session1 = createSession(100L, 7); // organizationId=ORG_ID
            session1.setSessionDate(LocalDate.of(2026, 6, 10));
            PracticeSession session2 = createSession(200L, 7);
            session2.setOrganizationId(org2Id);
            session2.setSessionDate(LocalDate.of(2026, 6, 12));

            when(practiceSessionRepository.findByOrganizationIdInAndYearAndMonth(List.of(ORG_ID, org2Id), 2026, 6))
                    .thenReturn(List.of(session1, session2));
            when(playerOrganizationRepository.findByOrganizationIdIn(List.of(ORG_ID, org2Id)))
                    .thenReturn(List.of(
                            com.karuta.matchtracker.entity.PlayerOrganization.builder()
                                    .playerId(10L).organizationId(ORG_ID).build(),
                            com.karuta.matchtracker.entity.PlayerOrganization.builder()
                                    .playerId(20L).organizationId(org2Id).build()));

            // p10=団体1で3試合WON、p20=団体2で7試合WON
            java.util.List<PracticeParticipant> participants = new java.util.ArrayList<>();
            for (int m = 1; m <= 3; m++) {
                participants.add(buildParticipant(100L, 10L, m, ParticipantStatus.WON));
            }
            for (int m = 1; m <= 7; m++) {
                participants.add(buildParticipant(200L, 20L, m, ParticipantStatus.WON));
            }
            when(practiceParticipantRepository.findBySessionIdIn(List.of(100L, 200L)))
                    .thenReturn(participants);

            com.karuta.matchtracker.entity.Player p10 = new com.karuta.matchtracker.entity.Player();
            p10.setId(10L);
            p10.setName("白石新菜");
            com.karuta.matchtracker.entity.Player p20 = new com.karuta.matchtracker.entity.Player();
            p20.setId(20L);
            p20.setName("泉駆");
            when(playerRepository.findAllActive()).thenReturn(List.of(p10, p20));

            java.util.List<com.karuta.matchtracker.dto.ParticipationGroupDto> groups =
                    service.getParticipationGroups(10L, 2026, 6, List.of(
                            com.karuta.matchtracker.dto.OrganizationDto.builder().id(ORG_ID).name("団体1").build(),
                            com.karuta.matchtracker.dto.OrganizationDto.builder().id(org2Id).name("団体2").build()));

            // 全体 + 団体1 + 団体2 の3グループ（この順）
            assertThat(groups).hasSize(3);
            assertThat(groups.get(0).getOrganizationId()).isNull();
            assertThat(groups.get(0).getOrganizationName()).isEqualTo("全体");
            assertThat(groups.get(1).getOrganizationId()).isEqualTo(ORG_ID);
            assertThat(groups.get(2).getOrganizationId()).isEqualTo(org2Id);

            // 全体: 分母14（7+7）、p20=7/14 が1位、p10=3/14。myRate は p10
            com.karuta.matchtracker.dto.ParticipationGroupDto all = groups.get(0);
            assertThat(all.getTop3()).hasSize(2);
            assertThat(all.getTop3().get(0).getPlayerId()).isEqualTo(20L);
            assertThat(all.getTop3().get(0).getRate()).isEqualTo(7.0 / 14.0);
            assertThat(all.getMyRate().getPlayerId()).isEqualTo(10L);
            assertThat(all.getMyRate().getRate()).isEqualTo(3.0 / 14.0);

            // 団体1: 自団体セッションのみが分母（7）。他団体の参加は混入しない
            com.karuta.matchtracker.dto.ParticipationGroupDto g1 = groups.get(1);
            assertThat(g1.getTop3()).hasSize(1);
            assertThat(g1.getTop3().get(0).getPlayerId()).isEqualTo(10L);
            assertThat(g1.getTop3().get(0).getRate()).isEqualTo(3.0 / 7.0);

            // 団体2: p10 は非メンバーなので myRate は null
            com.karuta.matchtracker.dto.ParticipationGroupDto g2 = groups.get(2);
            assertThat(g2.getTop3()).hasSize(1);
            assertThat(g2.getTop3().get(0).getPlayerId()).isEqualTo(20L);
            assertThat(g2.getMyRate()).isNull();

            // 月間データのロードは各リポジトリ1回だけ（改修の主目的: グループ×top3/myRate ごとの再ロード撤廃）
            verify(practiceSessionRepository, times(1)).findByOrganizationIdInAndYearAndMonth(anyList(), anyInt(), anyInt());
            verify(practiceParticipantRepository, times(1)).findBySessionIdIn(anyList());
            verify(playerOrganizationRepository, times(1)).findByOrganizationIdIn(anyList());
            verify(playerRepository, times(1)).findAllActive();
        }
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("autoRegisterMatchParticipant（試合記録に伴う自動参加登録）")
    class AutoRegisterMatchParticipantTests {

        @Test
        @DisplayName("未参加なら WON で参加登録される")
        void registersWonWhenNotParticipating() {
            // findBy 未スタブ → 空リスト（参加記録なし）
            boolean result = service.autoRegisterMatchParticipant(100L, 10L, 1);

            assertThat(result).isTrue();
            verify(practiceParticipantRepository).save(participantCaptor.capture());
            PracticeParticipant saved = participantCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
            assertThat(saved.getSessionId()).isEqualTo(100L);
            assertThat(saved.getPlayerId()).isEqualTo(10L);
            assertThat(saved.getMatchNumber()).isEqualTo(1);
            // dirty=true で保存され、次回の伝助同期で反映される（waitlistNumber は持たない）
            assertThat(saved.isDirty()).isTrue();
            assertThat(saved.getWaitlistNumber()).isNull();
        }

        @Test
        @DisplayName("既に WON/PENDING（参加確定）なら二重登録しない（冪等）")
        void idempotentWhenAlreadyConfirmed() {
            PracticeParticipant won = PracticeParticipant.builder()
                    .sessionId(100L).playerId(10L).matchNumber(1).status(ParticipantStatus.WON).build();
            when(practiceParticipantRepository
                    .findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1)).thenReturn(List.of(won));

            boolean result = service.autoRegisterMatchParticipant(100L, 10L, 1);

            assertThat(result).isFalse();
            verify(practiceParticipantRepository, never()).save(any(PracticeParticipant.class));
        }

        @Test
        @DisplayName("WAITLISTED の相手は WON に昇格する（実際に対戦したため）")
        void promotesWaitlistedToWon() {
            PracticeParticipant waitlisted = PracticeParticipant.builder()
                    .sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(3).build();
            when(practiceParticipantRepository
                    .findBySessionIdAndPlayerIdAndMatchNumber(100L, 10L, 1)).thenReturn(List.of(waitlisted));

            boolean result = service.autoRegisterMatchParticipant(100L, 10L, 1);

            assertThat(result).isTrue();
            verify(practiceParticipantRepository).save(participantCaptor.capture());
            PracticeParticipant saved = participantCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ParticipantStatus.WON);
            assertThat(saved.getWaitlistNumber()).isNull(); // 昇格時に待機番号はクリア
        }

        @Test
        @DisplayName("未登録相手（playerId=0）・null引数では登録しない")
        void skipsForInvalidArguments() {
            assertThat(service.autoRegisterMatchParticipant(100L, 0L, 1)).isFalse();
            assertThat(service.autoRegisterMatchParticipant(null, 10L, 1)).isFalse();
            assertThat(service.autoRegisterMatchParticipant(100L, null, 1)).isFalse();
            assertThat(service.autoRegisterMatchParticipant(100L, 10L, null)).isFalse();

            verifyNoInteractions(practiceParticipantRepository);
        }
    }
}
