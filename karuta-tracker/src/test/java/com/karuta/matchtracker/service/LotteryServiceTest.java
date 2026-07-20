package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.AdminEditParticipantsRequest;
import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.dto.SameDayCancelContext;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PlayerOrganization;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ForbiddenException;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
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

    /** 空のベースライン（既存WONなし）の公平抽選トラッカー */
    private LotteryFairShareTracker emptyTracker() {
        return new LotteryFairShareTracker();
    }

    private static final int CAP = 30;

    @Test
    @DisplayName("優先選手未指定・全員同条件（定員超過）の試合は定員数だけ当選し残りは落選する")
    void processMatch_noPriority_seatsUpToCapacity() {
        PracticeSession session = session(2);
        // 定員2、applicant 4人（全員 recentTaken=0・todayTaken=0 の同条件）
        PracticeParticipant g0 = participant(1L, 10L);
        PracticeParticipant g1 = participant(2L, 20L);
        PracticeParticipant g2 = participant(3L, 30L);
        PracticeParticipant g3 = participant(4L, 40L);

        List<PracticeParticipant> applicants = List.of(g0, g1, g2, g3);

        lotteryService.processMatch(session, MATCH, applicants,
                null, emptyTracker(), CAP, Set.of(), false, new Random(0));

        long wonCount = applicants.stream().filter(p -> p.getStatus() == ParticipantStatus.WON).count();
        long waitlisted = applicants.stream().filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED).count();
        assertThat(wonCount).isEqualTo(2);
        assertThat(waitlisted).isEqualTo(2);
        // キャンセル待ち番号は 1..2 で連番
        assertThat(applicants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED)
                .map(PracticeParticipant::getWaitlistNumber))
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("A-2: processMatch は既存WON/OFFEREDを定員から差し引いてから抽選する（定員超過防止）")
    void processMatch_subtractsExistingWonOffered() {
        PracticeSession session = session(3); // capacity 3
        // 抽選前に既に WON 2 が存在 → 残枠は1
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                100L, MATCH, ParticipantStatus.WON)).thenReturn(2L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                100L, MATCH, ParticipantStatus.OFFERED)).thenReturn(0L);

        PracticeParticipant p1 = participant(1L, 10L);
        PracticeParticipant p2 = participant(2L, 20L);
        PracticeParticipant p3 = participant(3L, 30L);
        List<PracticeParticipant> applicants = List.of(p1, p2, p3);

        lotteryService.processMatch(session, MATCH, applicants,
                null, emptyTracker(), CAP, Set.of(), false, new Random(0));

        // 残枠1 → PENDING 3人中1人だけ WON（既存WON2 + 新規1 = 定員3、超過しない）
        long won = applicants.stream().filter(p -> p.getStatus() == ParticipantStatus.WON).count();
        assertThat(won).isEqualTo(1);
    }

    @Test
    @DisplayName("B-2: computePopulationSignature は母集団(PENDING)が変わると変化する")
    void computePopulationSignature_changesWithPopulation() {
        PracticeSession s = session(4);
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 4, ORG_ID))
                .thenReturn(List.of(s));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.PENDING))
                .thenReturn(List.of(participant(1L, 10L), participant(2L, 20L)));

        String sig1 = lotteryService.computePopulationSignature(2026, 4, ORG_ID);

        // 母集団が変化（PENDING が1人増える）
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.PENDING))
                .thenReturn(List.of(participant(1L, 10L), participant(2L, 20L), participant(3L, 30L)));
        String sig2 = lotteryService.computePopulationSignature(2026, 4, ORG_ID);

        assertThat(sig1).isNotBlank();
        assertThat(sig1).isNotEqualTo(sig2);
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
                null, emptyTracker(), CAP, adminPriority, false, new Random(0));

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
                null, emptyTracker(), CAP, adminPriority, false, new Random(0));

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

        lotteryService.processMatch(session, MATCH, applicants,
                null, emptyTracker(), CAP, adminPriority, false, new Random(0));

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
                null, emptyTracker(), CAP, adminPriority, false, new Random(0));

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
        when(practiceParticipantRepository.findWonPlayerDates(any(), any(), any())).thenReturn(List.of());
        when(practiceParticipantRepository.saveAll(any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        LotteryExecution result = lotteryService.reExecuteLottery(sessionId, 1L, null);

        assertThat(result.getPriorityPlayerIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    // -------- 公平抽選（2ルール）の新挙動テスト --------

    @Test
    @DisplayName("AC-1: todayTaken が小さい候補だけが当選対象（本日既に取った人は候補が尽きるまで選ばれない）")
    void processMatch_rule1_onlyMinTodayTakenSelected() {
        PracticeSession session = session(1); // 定員1
        PracticeParticipant already = participant(1L, 10L); // 本日既に1試合取得
        PracticeParticipant fresh = participant(2L, 20L);   // 本日未取得
        List<PracticeParticipant> applicants = List.of(already, fresh);

        LotteryFairShareTracker tracker = emptyTracker();
        tracker.recordWin(10L, session.getSessionDate()); // player10 の todayTaken=1

        lotteryService.processMatch(session, MATCH, applicants,
                null, tracker, CAP, Set.of(), false, new Random(0));

        // ルール1で候補は本日未取得の fresh(20) のみ → seed に依らず fresh が当選
        assertThat(fresh.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(already.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
    }

    @Test
    @DisplayName("AC-2: 全員全試合希望・毎試合定員超過のセッションで落選回数の差は最大1（一巡保証）")
    void session_rule1_spreadsLossesEvenly() {
        long[] playerIds = {10L, 20L, 30L}; // 3人・毎試合全員希望
        int matches = 3;
        int capacity = 2; // 毎試合1人が落選

        for (long seed = 0; seed < 30; seed++) {
            LotteryFairShareTracker tracker = emptyTracker();
            Random random = new Random(seed);
            Map<Long, Integer> lossCount = new HashMap<>();
            for (long pid : playerIds) {
                lossCount.put(pid, 0);
            }

            PracticeSession session = session(capacity);
            for (int m = 1; m <= matches; m++) {
                List<PracticeParticipant> applicants = new ArrayList<>();
                long idBase = m * 100L;
                for (int i = 0; i < playerIds.length; i++) {
                    applicants.add(PracticeParticipant.builder()
                            .id(idBase + i).playerId(playerIds[i]).sessionId(100L)
                            .matchNumber(m).status(ParticipantStatus.PENDING).build());
                }
                lotteryService.processMatch(session, m, applicants,
                        null, tracker, CAP, Set.of(), false, random);
                for (PracticeParticipant p : applicants) {
                    if (p.getStatus() == ParticipantStatus.WAITLISTED) {
                        lossCount.merge(p.getPlayerId(), 1, Integer::sum);
                    }
                }
            }

            int max = lossCount.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = lossCount.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            assertThat(max - min)
                    .as("seed=%d lossCount=%s", seed, lossCount)
                    .isLessThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("AC-R3: 同一シード・同一入力・同一ベースラインなら当落は一致（プレビュー=確定の再現性）")
    void processMatch_sameSeed_previewEqualsExecute() {
        long seed = 12345L;
        List<Long> pids = List.of(10L, 20L, 30L, 40L, 50L);

        // saveResults=false（プレビュー）と true（確定）で当選集合が一致する
        Set<Long> preview = runMatchWinners(seed, pids, 2, false);
        Set<Long> execute = runMatchWinners(seed, pids, 2, true);
        assertThat(preview).isEqualTo(execute);
        assertThat(preview).hasSize(2);
    }

    /** 与えたベースライン・シードで1試合を実行し、当選 playerId 集合を返す（AC-R3 用ヘルパ） */
    private Set<Long> runMatchWinners(long seed, List<Long> playerIds, int capacity, boolean saveResults) {
        PracticeSession s = session(capacity);
        List<PracticeParticipant> applicants = new ArrayList<>();
        long id = 1;
        for (long pid : playerIds) {
            applicants.add(PracticeParticipant.builder()
                    .id(id++).playerId(pid).sessionId(100L).matchNumber(MATCH)
                    .status(ParticipantStatus.PENDING).build());
        }
        // recentTaken にばらつきを持たせる（重み付き抽選が効く状況で再現性を検証）
        LotteryFairShareTracker tracker = emptyTracker();
        tracker.recordWin(20L, s.getSessionDate().minusDays(3));
        tracker.recordWin(30L, s.getSessionDate().minusDays(3));
        tracker.recordWin(30L, s.getSessionDate().minusDays(10));
        lotteryService.processMatch(s, MATCH, applicants,
                null, tracker, CAP, Set.of(), saveResults, new Random(seed));
        return applicants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WON)
                .map(PracticeParticipant::getPlayerId)
                .collect(Collectors.toSet());
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
    @DisplayName("editParticipants WAITLISTED→WON: 待ち番号をクリアし残存キューを再採番する（管理者手動繰り上げ）")
    void editParticipants_waitlistedToWon_clearsWaitlistNumberAndRenumbers() {
        PracticeParticipant p = PracticeParticipant.builder()
                .id(701L).playerId(20L).sessionId(100L).matchNumber(MATCH)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2)
                .offeredAt(java.time.LocalDateTime.of(2026, 4, 1, 8, 0))
                .offerDeadline(java.time.LocalDateTime.of(2026, 4, 1, 12, 0))
                .build();
        when(practiceParticipantRepository.findById(701L)).thenReturn(Optional.of(p));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session(10)));
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList())).thenReturn(List.of());

        AdminEditParticipantsRequest.StatusChange change = new AdminEditParticipantsRequest.StatusChange();
        change.setParticipantId(701L);
        change.setNewStatus(ParticipantStatus.WON);
        AdminEditParticipantsRequest req = new AdminEditParticipantsRequest();
        req.setSessionId(100L);
        req.setMatchNumber(MATCH);
        req.setStatusChanges(List.of(change));

        lotteryService.editParticipants(req);

        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(p.getWaitlistNumber()).isNull();
        assertThat(p.getOfferedAt()).isNull();
        assertThat(p.getOfferDeadline()).isNull();
        assertThat(p.isDirty()).isTrue();
        verify(practiceParticipantRepository).save(p);
        // WAITLISTED だけでなく OFFERED も含めて残存キューを 1..N 再採番する
        verify(waitlistPromotionService).renumberRemainingWaitlist(100L, MATCH);
    }

    @Test
    @DisplayName("editParticipants OFFERED→WON: 定員チェックなしで昇格し残存キューを再採番する")
    void editParticipants_offeredToWon_promotesAndRenumbers() {
        PracticeParticipant p = PracticeParticipant.builder()
                .id(704L).playerId(23L).sessionId(100L).matchNumber(MATCH)
                .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                .offeredAt(java.time.LocalDateTime.of(2026, 4, 1, 8, 0))
                .offerDeadline(java.time.LocalDateTime.of(2026, 4, 1, 12, 0))
                .build();
        when(practiceParticipantRepository.findById(704L)).thenReturn(Optional.of(p));
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList())).thenReturn(List.of());

        AdminEditParticipantsRequest.StatusChange change = new AdminEditParticipantsRequest.StatusChange();
        change.setParticipantId(704L);
        change.setNewStatus(ParticipantStatus.WON);
        AdminEditParticipantsRequest req = new AdminEditParticipantsRequest();
        req.setSessionId(100L);
        req.setMatchNumber(MATCH);
        req.setStatusChanges(List.of(change));

        lotteryService.editParticipants(req);

        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(p.getWaitlistNumber()).isNull();
        assertThat(p.getOfferedAt()).isNull();
        assertThat(p.getOfferDeadline()).isNull();
        verify(practiceParticipantRepository).save(p);
        verify(waitlistPromotionService).renumberRemainingWaitlist(100L, MATCH);
        // OFFERED→WON は定員に算入済み（総数不変）のため空き枠チェック（session取得）はしない
        verify(practiceSessionRepository, never()).findById(100L);
    }

    @Test
    @DisplayName("editParticipants: 別セッションの participantId は 400 で拒否（IDOR 防止）")
    void editParticipants_participantFromAnotherSession_rejected() {
        PracticeParticipant p = PracticeParticipant.builder()
                .id(702L).playerId(21L).sessionId(999L).matchNumber(MATCH)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();
        when(practiceParticipantRepository.findById(702L)).thenReturn(Optional.of(p));

        AdminEditParticipantsRequest.StatusChange change = new AdminEditParticipantsRequest.StatusChange();
        change.setParticipantId(702L);
        change.setNewStatus(ParticipantStatus.WON);
        AdminEditParticipantsRequest req = new AdminEditParticipantsRequest();
        req.setSessionId(100L); // request は別セッション(100)なのに participant は 999 に属する
        req.setMatchNumber(MATCH);
        req.setStatusChanges(List.of(change));

        assertThatThrownBy(() -> lotteryService.editParticipants(req))
                .isInstanceOf(IllegalArgumentException.class);
        verify(practiceParticipantRepository, never()).save(any());
        verify(waitlistPromotionService, never()).renumberRemainingWaitlist(anyLong(), anyInt());
    }

    @Test
    @DisplayName("editParticipants WAITLISTED→WON: 定員満(WON+OFFERED>=capacity)なら 400 で拒否")
    void editParticipants_waitlistedToWon_full_rejected() {
        PracticeParticipant p = PracticeParticipant.builder()
                .id(703L).playerId(22L).sessionId(100L).matchNumber(MATCH)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();
        when(practiceParticipantRepository.findById(703L)).thenReturn(Optional.of(p));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session(2)));
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, MATCH, ParticipantStatus.WON))
                .thenReturn(2L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, MATCH, ParticipantStatus.OFFERED))
                .thenReturn(0L);

        AdminEditParticipantsRequest.StatusChange change = new AdminEditParticipantsRequest.StatusChange();
        change.setParticipantId(703L);
        change.setNewStatus(ParticipantStatus.WON);
        AdminEditParticipantsRequest req = new AdminEditParticipantsRequest();
        req.setSessionId(100L);
        req.setMatchNumber(MATCH);
        req.setStatusChanges(List.of(change));

        assertThatThrownBy(() -> lotteryService.editParticipants(req))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(p.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        verify(practiceParticipantRepository, never()).save(p);
        verify(waitlistPromotionService, never()).renumberRemainingWaitlist(anyLong(), anyInt());
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
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session(10)));
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

    // -------- validatePriorityPlayerIds: 団体所属チェック ([Issue #620]) --------

    private PlayerOrganization playerOrgRecord(long playerId, long organizationId) {
        return PlayerOrganization.builder()
                .playerId(playerId)
                .organizationId(organizationId)
                .build();
    }

    private PracticeSession sessionForApplicants(long sessionId, long orgId, LocalDate date) {
        PracticeSession s = new PracticeSession();
        s.setId(sessionId);
        s.setOrganizationId(orgId);
        s.setSessionDate(date);
        return s;
    }

    @Test
    @DisplayName("validatePriorityPlayerIds: 単一団体所属の選手は対象団体ならOK")
    void validatePriorityPlayerIds_singleOrgPlayer_inTargetOrg_passes() {
        // 選手 10 は org 1 のみに所属
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(10L)))
                .thenReturn(List.of(playerOrgRecord(10L, 1L)));
        // 参加希望チェックは通過させる
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, 1L))
                .thenReturn(List.of(sessionForApplicants(100L, 1L, LocalDate.of(2026, 5, 1))));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(100L)))
                .thenReturn(List.of(participant(1L, 10L)));

        assertThatCode(() -> lotteryService.validatePriorityPlayerIds(List.of(10L), 2026, 5, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePriorityPlayerIds: 複数団体所属の選手も対象団体に含まれていればOK（Fix #620）")
    void validatePriorityPlayerIds_multiOrgPlayer_targetIncluded_passes() {
        // 選手 41 は org 1 と org 2 の両方に所属（北大とわすらもち会の両方）
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(41L)))
                .thenReturn(List.of(
                        playerOrgRecord(41L, 1L),
                        playerOrgRecord(41L, 2L)));
        // 北大 (org 2) のセッションに 41 が PENDING で参加希望
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 5, 2L))
                .thenReturn(List.of(sessionForApplicants(200L, 2L, LocalDate.of(2026, 5, 1))));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(200L)))
                .thenReturn(List.of(PracticeParticipant.builder()
                        .id(1L).playerId(41L).sessionId(200L).matchNumber(MATCH)
                        .status(ParticipantStatus.PENDING).build()));

        // 北大 (org 2) の抽選で優先選手として 41 を指定 → 弾かれない
        assertThatCode(() -> lotteryService.validatePriorityPlayerIds(List.of(41L), 2026, 5, 2L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validatePriorityPlayerIds: 対象団体に所属していない選手は403")
    void validatePriorityPlayerIds_foreignOrgPlayer_throwsForbidden() {
        // 選手 50 は org 1 のみに所属
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(50L)))
                .thenReturn(List.of(playerOrgRecord(50L, 1L)));

        // org 2 の抽選で優先選手として 50 を指定 → 403
        assertThatThrownBy(() -> lotteryService.validatePriorityPlayerIds(List.of(50L), 2026, 5, 2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("playerIds=[50]");
    }

    @Test
    @DisplayName("validatePriorityPlayerIds: PlayerOrganization レコードが全くない選手も403")
    void validatePriorityPlayerIds_noOrgRecord_throwsForbidden() {
        // 選手 99 は player_organizations に1件もない（孤児データ）
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(99L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> lotteryService.validatePriorityPlayerIds(List.of(99L), 2026, 5, 1L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("playerIds=[99]");
    }

    @Test
    @DisplayName("validatePriorityPlayerIds: 複数選手のうち他団体所属のものだけが foreignIds に列挙される")
    void validatePriorityPlayerIds_mixedIds_onlyForeignReported() {
        // 10: org 1 のみ / 41: org 1+2 の複数所属 / 50: org 1 のみ
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(10L, 41L, 50L)))
                .thenReturn(List.of(
                        playerOrgRecord(10L, 1L),
                        playerOrgRecord(41L, 1L),
                        playerOrgRecord(41L, 2L),
                        playerOrgRecord(50L, 1L)));

        // org 2 の抽選 → 41 だけ通って良いはずだが、10 と 50 は他団体扱い
        assertThatThrownBy(() -> lotteryService.validatePriorityPlayerIds(
                List.of(10L, 41L, 50L), 2026, 5, 2L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("playerIds=[10, 50]");
    }

    @Test
    @DisplayName("validatePriorityPlayerIds: organizationId が null の場合は団体所属チェックをスキップ（SUPER_ADMIN 全団体モード）")
    void validatePriorityPlayerIds_nullOrgId_skipsOrgCheck() {
        // 全団体モード: 参加希望チェックのみ通過させる
        when(practiceSessionRepository.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(sessionForApplicants(100L, 1L, LocalDate.of(2026, 5, 1))));
        when(practiceParticipantRepository.findBySessionIdIn(List.of(100L)))
                .thenReturn(List.of(participant(1L, 10L)));

        assertThatCode(() -> lotteryService.validatePriorityPlayerIds(List.of(10L), 2026, 5, null))
                .doesNotThrowAnyException();

        // findByPlayerIdIn が呼ばれていないことを確認
        verify(playerOrganizationRepository, never()).findByPlayerIdIn(anyList());
    }

    @Test
    @DisplayName("validatePriorityPlayerIds: 空リストはバリデーション全体をスキップ（DBアクセス無し）")
    void validatePriorityPlayerIds_emptyIds_noOp() {
        assertThatCode(() -> lotteryService.validatePriorityPlayerIds(List.of(), 2026, 5, 1L))
                .doesNotThrowAnyException();

        verify(playerOrganizationRepository, never()).findByPlayerIdIn(anyList());
        verify(practiceSessionRepository, never()).findByYearAndMonthAndOrganizationId(anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("AC-5(org): organizationId=null の全団体一括実行は団体ごとにベースラインを分離し recentTaken が団体を越えない")
    void executeLottery_nullOrg_buildsPerOrgBaselineWithoutCrossOrgLeak() throws Exception {
        // 団体1(2026-05-03)・団体2(2026-05-10)のセッションを SUPER_ADMIN 全団体一括(org=null)で処理する。
        // 同一 playerId=10 が両団体の PENDING に現れるが、recentTaken のベースライン（既存WON）は
        // 団体ごとに findWonPlayerDates(orgId, ...) で個別に読まれ、全団体一括(null)では読まれない。
        // = 各団体を個別実行したのと同じ結果になり、片方の団体の当選実績がもう一方に混入しない。
        PracticeSession org1Session = sessionForApplicants(201L, 1L, LocalDate.of(2026, 5, 3));
        PracticeSession org2Session = sessionForApplicants(202L, 2L, LocalDate.of(2026, 5, 10));

        when(practiceSessionRepository.findByYearAndMonth(2026, 5))
                .thenReturn(List.of(org1Session, org2Session));
        when(practiceParticipantRepository.findBySessionIdAndStatus(201L, ParticipantStatus.PENDING))
                .thenReturn(new ArrayList<>(List.of(participant(1L, 10L))));
        when(practiceParticipantRepository.findBySessionIdAndStatus(202L, ParticipantStatus.PENDING))
                .thenReturn(new ArrayList<>(List.of(participant(2L, 10L))));
        when(systemSettingService.getLotteryWeightCapPercentile(anyLong())).thenReturn(30);
        when(practiceParticipantRepository.findWonPlayerDates(any(), any(), any()))
                .thenReturn(List.of());
        when(practiceParticipantRepository.saveAll(any())).thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(lotteryExecutionRepository.save(any())).thenAnswer(inv -> {
            LotteryExecution ex = inv.getArgument(0);
            if (ex.getId() == null) {
                ex.setId(900L);
            }
            return ex;
        });

        lotteryService.executeLottery(2026, 5, 99L, LotteryExecution.ExecutionType.MANUAL, null, 1L, List.of());

        // ベースラインは各団体IDで個別に引かれ、全団体一括(null)では引かれない（団体スコープの担保）
        verify(practiceParticipantRepository).findWonPlayerDates(eq(1L), any(), any());
        verify(practiceParticipantRepository).findWonPlayerDates(eq(2L), any(), any());
        verify(practiceParticipantRepository, never()).findWonPlayerDates(isNull(), any(), any());
    }
}
