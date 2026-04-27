package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.AdminEditParticipantsRequest;
import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.dto.SameDayCancelContext;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LotteryService 抽選アルゴリズム テスト")
class LotteryServiceTest {

    @Mock private com.karuta.matchtracker.repository.PracticeSessionRepository practiceSessionRepository;
    @Mock private com.karuta.matchtracker.repository.PracticeParticipantRepository practiceParticipantRepository;
    @Mock private com.karuta.matchtracker.repository.LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private com.karuta.matchtracker.repository.PlayerRepository playerRepository;
    @Mock private com.karuta.matchtracker.repository.VenueRepository venueRepository;
    @Mock private NotificationService notificationService;
    @Mock private SystemSettingService systemSettingService;
    @Mock private WaitlistPromotionService waitlistPromotionService;
    @Mock private LineNotificationService lineNotificationService;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock private DensukeWriteService densukeWriteService;
    @Mock private ObjectMapper objectMapper;
    @Mock private LotteryQueryService lotteryQueryService;
    @Mock private com.karuta.matchtracker.repository.PlayerOrganizationRepository playerOrganizationRepository;

    @InjectMocks
    private LotteryService lotteryService;

    private static final Long ORG_ID = 1L;
    private static final int MATCH = 1;

    private PracticeSession session(int capacity) {
        PracticeSession s = new PracticeSession();
        s.setId(100L);
        s.setOrganizationId(ORG_ID);
        s.setCapacity(capacity);
        s.setSessionDate(LocalDate.of(2026, 4, 1));
        return s;
    }

    private PracticeParticipant participant(long id, long playerId) {
        return PracticeParticipant.builder()
                .id(id)
                .playerId(playerId)
                .sessionId(100L)
                .matchNumber(MATCH)
                .status(ParticipantStatus.PENDING)
                .build();
    }

    @BeforeEach
    void setUp() {
        when(systemSettingService.getLotteryNormalReservePercent(any())).thenReturn(0);
    }

    @Test
    @DisplayName("優先選手未指定の場合、既存の2層ロジック（救済 > 一般）が維持される")
    void processMatch_noPriorityPlayers_behavesAsLegacy() {
        PracticeSession session = session(2);
        // 定員2、applicant 4人（うち1人は救済対象）
        PracticeParticipant rescue = participant(1L, 10L);
        PracticeParticipant g1 = participant(2L, 20L);
        PracticeParticipant g2 = participant(3L, 30L);
        PracticeParticipant g3 = participant(4L, 40L);

        List<PracticeParticipant> applicants = List.of(rescue, g1, g2, g3);
        Set<Long> monthlyLosers = new HashSet<>(Set.of(10L)); // 救済対象（可変セット）
        Set<Long> sessionLosers = new HashSet<>();

        lotteryService.processMatch(session, MATCH, applicants,
                sessionLosers, monthlyLosers, null,
                Map.of(), new HashMap<>(), Set.of(), false, new Random(0));

        // 救済対象(10)は必ず当選
        assertThat(rescue.getStatus()).isEqualTo(ParticipantStatus.WON);
        // 1枠残り → g1〜g3のうち1人が当選
        long wonCount = applicants.stream().filter(p -> p.getStatus() == ParticipantStatus.WON).count();
        assertThat(wonCount).isEqualTo(2);
    }

    @Test
    @DisplayName("管理者優先選手が定員内の場合、全員当選する")
    void processMatch_adminPriorityWithinCapacity_allWin() {
        PracticeSession session = session(3);
        PracticeParticipant a1 = participant(1L, 10L);
        PracticeParticipant a2 = participant(2L, 20L);
        PracticeParticipant g1 = participant(3L, 30L);
        PracticeParticipant g2 = participant(4L, 40L);

        List<PracticeParticipant> applicants = List.of(a1, a2, g1, g2);
        Set<Long> adminPriority = Set.of(10L, 20L); // a1, a2 が優先指定

        lotteryService.processMatch(session, MATCH, applicants,
                new HashSet<>(), new HashSet<>(), null,
                Map.of(), new HashMap<>(), adminPriority, false, new Random(0));

        // 管理者優先2人は確実に当選
        assertThat(a1.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(a2.getStatus()).isEqualTo(ParticipantStatus.WON);
        // 残り1枠はg1かg2どちらか
        long wonCount = applicants.stream().filter(p -> p.getStatus() == ParticipantStatus.WON).count();
        assertThat(wonCount).isEqualTo(3);
    }

    @Test
    @DisplayName("管理者優先選手が定員超過の場合、優先選手同士で抽選される（一般は必ず落選）")
    void processMatch_adminPriorityExceedsCapacity_adminLotteryOnly() {
        PracticeSession session = session(2);
        PracticeParticipant a1 = participant(1L, 10L);
        PracticeParticipant a2 = participant(2L, 20L);
        PracticeParticipant a3 = participant(3L, 30L);
        PracticeParticipant g1 = participant(4L, 40L);

        List<PracticeParticipant> applicants = List.of(a1, a2, a3, g1);
        Set<Long> adminPriority = Set.of(10L, 20L, 30L); // 3人指定だが定員2

        lotteryService.processMatch(session, MATCH, applicants,
                new HashSet<>(), new HashSet<>(), null,
                Map.of(), new HashMap<>(), adminPriority, false, new Random(0));

        // 一般(g1)は必ず落選（WAITLISTED）
        assertThat(g1.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        // 管理者優先3人のうち2人が当選、1人が落選
        long adminWon = List.of(a1, a2, a3).stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WON).count();
        long adminLost = List.of(a1, a2, a3).stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED).count();
        assertThat(adminWon).isEqualTo(2);
        assertThat(adminLost).isEqualTo(1);
    }

    @Test
    @DisplayName("管理者優先選手が落選した場合、キャンセル待ちの最上位に入る")
    void processMatch_adminPriorityLoser_getsTopWaitlistPosition() {
        PracticeSession session = session(1);
        PracticeParticipant admin = participant(1L, 10L);
        PracticeParticipant admin2 = participant(2L, 20L);
        PracticeParticipant general = participant(3L, 30L);

        List<PracticeParticipant> applicants = new ArrayList<>(List.of(admin, admin2, general));
        Set<Long> adminPriority = Set.of(10L, 20L);

        Map<Long, Integer> currentWaitlistOrder = new HashMap<>();
        lotteryService.processMatch(session, MATCH, applicants,
                new HashSet<>(), new HashSet<>(), null,
                Map.of(), currentWaitlistOrder, adminPriority, false, new Random(0));

        // 一般(30)は落選し、waitlistNumber >= 2
        assertThat(general.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        // 管理者落選者(どちらか1人)のwaitlistNumber は general より小さい
        int generalWaitlist = general.getWaitlistNumber();
        List<PracticeParticipant> adminLosers = List.of(admin, admin2).stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED)
                .collect(Collectors.toList());
        assertThat(adminLosers).hasSize(1);
        assertThat(adminLosers.get(0).getWaitlistNumber()).isLessThan(generalWaitlist);
    }

    @Test
    @DisplayName("優先選手が希望していないセッションでは優先扱いにならない")
    void processMatch_adminPriorityPlayerNotApplied_noEffect() {
        PracticeSession session = session(1);
        // adminPriority に playerId=99 を指定するが、そのIDは applicants に含まれない
        PracticeParticipant g1 = participant(1L, 10L);
        PracticeParticipant g2 = participant(2L, 20L);

        List<PracticeParticipant> applicants = List.of(g1, g2);
        Set<Long> adminPriority = Set.of(99L); // このセッションに希望がない選手

        lotteryService.processMatch(session, MATCH, applicants,
                new HashSet<>(), new HashSet<>(), null,
                Map.of(), new HashMap<>(), adminPriority, false, new Random(0));

        // 通常の抽選が行われ、1人当選・1人落選
        long wonCount = applicants.stream().filter(p -> p.getStatus() == ParticipantStatus.WON).count();
        long waitlistedCount = applicants.stream().filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED).count();
        assertThat(wonCount).isEqualTo(1);
        assertThat(waitlistedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("reExecuteLottery: priorityPlayerIds が null のとき、save前の直前実行から引き継がれる")
    void reExecuteLottery_nullPriorityPlayerIds_inheritsFromPreviousExecution() throws Exception {
        Long sessionId = 100L;

        PracticeSession session = new PracticeSession();
        session.setId(sessionId);
        session.setOrganizationId(ORG_ID);
        session.setCapacity(2);
        session.setSessionDate(LocalDate.of(2026, 4, 1));

        // 前回の実行（優先選手 10, 20 を持つ）
        LotteryExecution prevExecution = LotteryExecution.builder().id(50L).build();
        prevExecution.setPriorityPlayerIds(List.of(10L, 20L));

        when(practiceSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(lotteryExecutionRepository
            .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                2026, 4, ORG_ID, LotteryExecution.ExecutionStatus.SUCCESS))
            .thenReturn(Optional.of(prevExecution));
        when(lotteryExecutionRepository.save(any())).thenAnswer(inv -> {
            LotteryExecution ex = inv.getArgument(0);
            if (ex.getId() == null) ex.setId(51L);
            return ex;
        });
        when(practiceParticipantRepository.findBySessionId(sessionId)).thenReturn(List.of());
        when(practiceParticipantRepository.findMonthlyLoserPlayerIds(2026, 4, sessionId, ORG_ID)).thenReturn(List.of());
        when(practiceParticipantRepository.saveAll(any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        LotteryExecution result = lotteryService.reExecuteLottery(sessionId, 1L, null);

        assertThat(result.getPriorityPlayerIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    // -------- editParticipants の WON→CANCELLED 分岐テスト ([#1]) --------

    private PracticeParticipant wonParticipant(long id, long playerId) {
        return PracticeParticipant.builder()
                .id(id)
                .playerId(playerId)
                .sessionId(100L)
                .matchNumber(MATCH)
                .status(ParticipantStatus.WON)
                .build();
    }

    private AdminEditParticipantsRequest cancelRequest(long participantId) {
        AdminEditParticipantsRequest.StatusChange change = new AdminEditParticipantsRequest.StatusChange();
        change.setParticipantId(participantId);
        change.setNewStatus(ParticipantStatus.CANCELLED);
        AdminEditParticipantsRequest req = new AdminEditParticipantsRequest();
        req.setSessionId(100L);
        req.setMatchNumber(MATCH);
        req.setStatusChanges(List.of(change));
        return req;
    }

    @Test
    @DisplayName("editParticipants WON→CANCELLED: cancelParticipationSuppressed に委譲する")
    void editParticipants_wonToCancelled_delegatesToCancelParticipationSuppressed() {
        PracticeParticipant p = wonParticipant(501L, 10L);
        when(practiceParticipantRepository.findById(501L)).thenReturn(Optional.of(p));

        AdminWaitlistNotificationData notif = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル")
                .triggerPlayerId(10L)
                .sessionId(100L)
                .matchNumber(MATCH)
                .build();
        when(waitlistPromotionService.cancelParticipationSuppressed(501L, null, null))
                .thenReturn(notif);
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList()))
                .thenReturn(List.of(notif));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session(6)));

        lotteryService.editParticipants(cancelRequest(501L));

        // setStatus / save は委譲側で行うため、editParticipants 自体では呼ばない
        verify(practiceParticipantRepository, never()).save(any());
        verify(waitlistPromotionService).cancelParticipationSuppressed(501L, null, null);
    }

    @Test
    @DisplayName("editParticipants 通常分（当日でない）: dispatchSameDayCancelNotifications が normal 分を返し、バッチ通知が送られる")
    void editParticipants_normalCancel_sendsBatchedAdminNotification() {
        PracticeParticipant p = wonParticipant(601L, 11L);
        when(practiceParticipantRepository.findById(601L)).thenReturn(Optional.of(p));

        AdminWaitlistNotificationData notif = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル")
                .triggerPlayerId(11L)
                .sessionId(100L)
                .matchNumber(MATCH)
                .build();
        when(waitlistPromotionService.cancelParticipationSuppressed(601L, null, null))
                .thenReturn(notif);
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList()))
                .thenReturn(List.of(notif));
        PracticeSession sess = session(6);
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(sess));

        lotteryService.editParticipants(cancelRequest(601L));

        verify(waitlistPromotionService).sendBatchedAdminWaitlistNotifications(List.of(notif), sess);
    }

    @Test
    @DisplayName("editParticipants 当日12:00以降キャンセル: dispatchSameDayCancelNotifications が空を返し、バッチ通知は送られない（afterCommit 登録に委譲）")
    void editParticipants_sameDayAfternoonCancel_skipsNormalBatchNotification() {
        PracticeParticipant p = wonParticipant(701L, 12L);
        when(practiceParticipantRepository.findById(701L)).thenReturn(Optional.of(p));

        // SameDayCancelContext 付きの通知データを返す（当日12:00以降のキャンセル想定）
        PracticeSession sess = session(6);
        SameDayCancelContext ctx = SameDayCancelContext.builder()
                .session(sess).playerId(12L).playerName("選手").matchNumber(MATCH).build();
        AdminWaitlistNotificationData notif = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル（当日補充）")
                .triggerPlayerId(12L)
                .sessionId(100L)
                .matchNumber(MATCH)
                .sameDayCancelContext(ctx)
                .build();
        when(waitlistPromotionService.cancelParticipationSuppressed(701L, null, null))
                .thenReturn(notif);
        // 当日キャンセル分は dispatch 内で afterCommit 登録され、normal リストは空が返る
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList()))
                .thenReturn(List.of());

        lotteryService.editParticipants(cancelRequest(701L));

        // dispatch には全件渡される
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminWaitlistNotificationData>> captor = ArgumentCaptor.forClass(List.class);
        verify(waitlistPromotionService).dispatchSameDayCancelNotifications(captor.capture());
        assertThat(captor.getValue()).hasSize(1);

        // 当日分のみなので通常バッチ通知は呼ばれない
        verify(waitlistPromotionService, never()).sendBatchedAdminWaitlistNotifications(anyList(), any());
    }

    @Test
    @DisplayName("editParticipants WON→CANCELLED 以外（WAITLISTED→WON など）: 既存の直接 setStatus + save 経路")
    void editParticipants_otherStatusChange_directSave() {
        PracticeParticipant p = PracticeParticipant.builder()
                .id(801L).playerId(13L).sessionId(100L).matchNumber(MATCH)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();
        when(practiceParticipantRepository.findById(801L)).thenReturn(Optional.of(p));
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList()))
                .thenReturn(List.of());

        AdminEditParticipantsRequest.StatusChange change = new AdminEditParticipantsRequest.StatusChange();
        change.setParticipantId(801L);
        change.setNewStatus(ParticipantStatus.WON);
        AdminEditParticipantsRequest req = new AdminEditParticipantsRequest();
        req.setSessionId(100L);
        req.setMatchNumber(MATCH);
        req.setStatusChanges(List.of(change));

        lotteryService.editParticipants(req);

        // 通常経路では setStatus + save が呼ばれる
        verify(practiceParticipantRepository).save(p);
        // 委譲メソッドは呼ばれない
        verify(waitlistPromotionService, never()).cancelParticipationSuppressed(any(), any(), any());
    }
}
