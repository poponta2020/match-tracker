package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.service.DensukeScraper.DensukeData;
import com.karuta.matchtracker.service.DensukeScraper.ScheduleEntry;
import com.karuta.matchtracker.service.DensukeImportService.ImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DensukeImportServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeImportService 単体テスト")
class DensukeImportServiceTest {

    @Mock private DensukeScraper densukeScraper;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private PlayerService playerService;
    @Mock private VenueRepository venueRepository;
    @Mock private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock private LotteryService lotteryService;
    @Mock private WaitlistPromotionService waitlistPromotionService;
    @Mock private PracticeParticipantService practiceParticipantService;
    @Mock private LineNotificationService lineNotificationService;
    @Mock private OrganizationService organizationService;

    @InjectMocks
    private DensukeImportService densukeImportService;

    private Player player1;
    private Player player2;
    private Player adminPlayer;

    @BeforeEach
    void setUp() {
        player1 = Player.builder().id(1L).name("田中").role(Player.Role.PLAYER)
                .gender(Player.Gender.男性).dominantHand(Player.DominantHand.右).build();
        player2 = Player.builder().id(2L).name("鈴木").role(Player.Role.PLAYER)
                .gender(Player.Gender.女性).dominantHand(Player.DominantHand.右).build();
        adminPlayer = Player.builder().id(10L).name("管理者").role(Player.Role.ADMIN)
                .adminOrganizationId(1L)
                .gender(Player.Gender.男性).dominantHand(Player.DominantHand.右).build();
    }

    private DensukeData createSampleData() {
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(LocalDate.of(2026, 4, 1));
        entry.setMatchNumber(1);
        entry.setVenueName("すずらん");
        entry.getParticipants().add("田中");
        entry.getParticipants().add("鈴木");
        data.getEntries().add(entry);
        return data;
    }

    @Test
    @DisplayName("新規セッション作成と参加者登録が正しく行われる")
    void testImportCreatesSessionAndRegistersParticipants() throws IOException {
        DensukeData data = createSampleData();
        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(List.of(
                Venue.builder().id(100L).name("すずらん").build()));
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.empty());
        when(practiceSessionRepository.save(any())).thenAnswer(inv -> {
            PracticeSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        assertThat(result.getTotalEntries()).isEqualTo(1);
        assertThat(result.getCreatedSessionCount()).isEqualTo(1);
        assertThat(result.getRegisteredCount()).isEqualTo(2);
        assertThat(result.getUnmatchedNames()).isEmpty();

        verify(practiceSessionRepository).save(argThat(session ->
                session.getSessionDate().equals(LocalDate.of(2026, 4, 1)) &&
                session.getCreatedBy().equals(10L)));
        verify(practiceParticipantRepository, times(2)).save(any(PracticeParticipant.class));
    }

    @Test
    @DisplayName("既存セッションがある場合は新規作成しない")
    void testImportUsesExistingSession() throws IOException {
        DensukeData data = createSampleData();
        PracticeSession existingSession = PracticeSession.builder().id(99L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(existingSession));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(99L, 1))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        assertThat(result.getCreatedSessionCount()).isEqualTo(0);
        verify(practiceSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Phase2（締切後・抽選確定前）のセッションはスキップされる")
    void testImportSkipsPhase2Session() throws IOException {
        DensukeData data = createSampleData();
        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(session));
        // Phase2: 締切後 かつ 抽選未確定
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(false);
        when(lotteryService.isLotteryConfirmed(2026, 4, 1L)).thenReturn(false);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        assertThat(result.getSkippedCount()).isEqualTo(2);
        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("未登録者がunmatchedNamesに記録される")
    void testImportTracksUnmatchedNames() throws IOException {
        DensukeData data = createSampleData();
        data.getEntries().get(0).getParticipants().add("未登録者");

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(Collections.emptyList());

        // 管理者への通知用
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(Collections.emptyList());
        when(playerRepository.findByRoleAndActive(Player.Role.ADMIN)).thenReturn(List.of(adminPlayer));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        assertThat(result.getUnmatchedNames()).containsExactly("未登録者");
        assertThat(result.getSkippedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("未登録者がいる場合に管理者へ通知が送信される")
    void testImportNotifiesAdminsOnUnmatchedNames() throws IOException {
        DensukeData data = createSampleData();
        data.getEntries().get(0).getParticipants().add("新人");

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(Collections.emptyList());
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(Collections.emptyList());
        when(playerRepository.findByRoleAndActive(Player.Role.ADMIN)).thenReturn(List.of(adminPlayer));

        densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());

        List<Notification> notifications = captor.getValue();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getPlayerId()).isEqualTo(10L);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.DENSUKE_UNMATCHED_NAMES);
        assertThat(notifications.get(0).getMessage()).contains("新人");
    }

    @Test
    @DisplayName("伝助から消えた参加者が自動削除される")
    void testImportRemovesAbsentParticipants() throws IOException {
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(LocalDate.of(2026, 4, 1));
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中"); // 鈴木は消えた
        data.getEntries().add(entry);

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();
        // dirty=false: 伝助から同期済みのため、伝助から消えたら削除される
        PracticeParticipant existingParticipant = PracticeParticipant.builder()
                .id(50L).sessionId(1L).playerId(2L).matchNumber(1).dirty(false).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(List.of(existingParticipant));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        assertThat(result.getRemovedCount()).isEqualTo(1);
        verify(practiceParticipantRepository).delete(existingParticipant);
    }

    @Test
    @DisplayName("registerAndSyncで未登録者が自動登録される")
    void testRegisterAndSync() throws IOException {
        DensukeData data = createSampleData();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerRepository.findByNameAndActive("新人")).thenReturn(Optional.empty());
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.empty());
        when(practiceSessionRepository.save(any())).thenAnswer(inv -> {
            PracticeSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        densukeImportService.registerAndSync(List.of("新人"), "http://example.com", null, 10L, 1L);

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());

        Player savedPlayer = playerCaptor.getValue();
        assertThat(savedPlayer.getName()).isEqualTo("新人");
        assertThat(savedPlayer.getRequirePasswordChange()).isTrue();
        assertThat(savedPlayer.getRole()).isEqualTo(Player.Role.PLAYER);
        verify(organizationService).ensurePlayerBelongsToOrganization(savedPlayer.getId(), 1L);
    }

    @Test
    @DisplayName("targetDateで対象月のフィルタが機能する")
    void testImportWithTargetDateFilter() throws IOException {
        DensukeData data = new DensukeData();

        // 4月のエントリ
        ScheduleEntry entry1 = new ScheduleEntry();
        entry1.setDate(LocalDate.of(2026, 4, 1));
        entry1.setMatchNumber(1);
        entry1.getParticipants().add("田中");
        data.getEntries().add(entry1);

        // 5月のエントリ（フィルタでスキップされるべき）
        ScheduleEntry entry2 = new ScheduleEntry();
        entry2.setDate(LocalDate.of(2026, 5, 10));
        entry2.setMatchNumber(1);
        entry2.getParticipants().add("鈴木");
        data.getEntries().add(entry2);

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(eq(LocalDate.of(2026, 4, 1)), eq(1L)))
                .thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke(
                "http://example.com", LocalDate.of(2026, 4, 1), 10L, 1L);

        // 4月のエントリだけが処理され、5月はスキップされる
        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceSessionRepository, never()).findBySessionDateAndOrganizationId(eq(LocalDate.of(2026, 5, 10)), eq(1L));
    }

    @Test
    @DisplayName("createdByがセッション作成時に正しく設定される")
    void testCreatedByIsPassedToSession() throws IOException {
        DensukeData data = createSampleData();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.empty());
        when(practiceSessionRepository.save(any())).thenAnswer(inv -> {
            PracticeSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        densukeImportService.importFromDensuke("http://example.com", null, 42L, 1L);

        verify(practiceSessionRepository).save(argThat(session ->
                session.getCreatedBy().equals(42L) && session.getUpdatedBy().equals(42L)));
    }

    @Test
    @DisplayName("SYSTEM_USER_IDが0Lとして定義されている")
    void testSystemUserIdConstant() {
        assertThat(DensukeImportService.SYSTEM_USER_ID).isEqualTo(0L);
    }

    // ----------------------------------------------------------------
    // Phase3 3-A6: WAITLISTED + 伝助○ の処理テスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("当日12:00以降に伝助で○にされたWAITLISTEDが空き枠ありならWONに昇格する")
    void testPhase3A6_afterNoon_withVacancy_promotesToWon() throws IOException {
        LocalDate today = LocalDate.of(2026, 4, 2);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(today);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中"); // ○
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(today).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant waitlisted = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(4).dirty(false).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(today, 1L))
                .thenReturn(Optional.of(session));
        // Phase3: 締切後 + 抽選確定済み
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(false);
        when(lotteryService.isLotteryConfirmed(2026, 4, 1L)).thenReturn(true);
        // 当日12:00以降
        when(lotteryDeadlineHelper.isAfterSameDayNoon(today)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(waitlisted));
        // 空き枠あり（13/14）
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                .thenReturn(13L);
        densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(waitlisted.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(waitlisted.getWaitlistNumber()).isNull();
        assertThat(waitlisted.isDirty()).isFalse(); // 伝助は既に○
        verify(practiceParticipantRepository).decrementWaitlistNumbersAfter(100L, 1, 4);
    }

    @Test
    @DisplayName("12:00より前に伝助で○にされたWAITLISTEDは△に書き戻される")
    void testPhase3A6_beforeNoon_writesBackSankaku() throws IOException {
        LocalDate today = LocalDate.of(2026, 4, 2);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(today);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中"); // ○
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(today).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant waitlisted = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(4).dirty(false).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(today, 1L))
                .thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(false);
        when(lotteryService.isLotteryConfirmed(2026, 4, 1L)).thenReturn(true);
        // 12:00より前
        when(lotteryDeadlineHelper.isAfterSameDayNoon(today)).thenReturn(false);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(waitlisted));

        densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        // ステータスはWAITLISTEDのまま、dirty=trueで△書き戻し
        assertThat(waitlisted.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(waitlisted.isDirty()).isTrue();
    }

    // ----------------------------------------------------------------
    // dirty フラグ関連テスト
    // ----------------------------------------------------------------

    @Test
    @DisplayName("dirty=trueの参加者は伝助から消えても削除されない")
    void testImportDoesNotRemoveDirtyParticipants() throws IOException {
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(LocalDate.of(2026, 4, 1));
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中"); // 鈴木は伝助にいない
        data.getEntries().add(entry);

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();
        // 鈴木: dirty=true（アプリ側で変更済み）
        PracticeParticipant dirtyParticipant = PracticeParticipant.builder()
                .id(50L).sessionId(1L).playerId(2L).matchNumber(1).dirty(true).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(List.of(dirtyParticipant));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        assertThat(result.getRemovedCount()).isEqualTo(0);
        verify(practiceParticipantRepository, never()).delete(dirtyParticipant);
    }

    @Test
    @DisplayName("dirty=falseの参加者は伝助から消えた場合に削除される")
    void testImportRemovesNonDirtyAbsentParticipants() throws IOException {
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(LocalDate.of(2026, 4, 1));
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中"); // 鈴木は伝助にいない
        data.getEntries().add(entry);

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();
        // 鈴木: dirty=false（伝助から同期済み）
        PracticeParticipant cleanParticipant = PracticeParticipant.builder()
                .id(50L).sessionId(1L).playerId(2L).matchNumber(1).dirty(false).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(List.of(cleanParticipant));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        assertThat(result.getRemovedCount()).isEqualTo(1);
        verify(practiceParticipantRepository).delete(cleanParticipant);
    }

    // ================================================================
    // Phase3 3-A テスト: 伝助○の処理（12ケース）
    // ================================================================

    /**
     * Phase3テスト用の共通セットアップ。
     * MONTHLY + 締切後 + 抽選確定済みでPhase3に入る。
     * entryのparticipants（○）にmarkedNamesを設定する。
     */
    private void setupPhase3Mocks(DensukeData data, PracticeSession session,
                                   LocalDate date, List<Player> players) throws IOException {
        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(players);
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(date, 1L))
                .thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(date.getYear(), date.getMonthValue(), 1L)).thenReturn(false);
        when(lotteryService.isLotteryConfirmed(date.getYear(), date.getMonthValue(), 1L)).thenReturn(true);
    }

    @Test
    @DisplayName("3-A1: Phase3 ○ 未登録 + 空きあり → WON で新規登録")
    void testPhase3Maru_3A1_unregistered_freeCapacity_createsAsWon() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantService.isFreeRegistrationOpen(session, 1)).thenReturn(true);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceParticipantRepository).save(argThat(p ->
                p.getPlayerId().equals(1L) &&
                p.getStatus() == ParticipantStatus.WON &&
                p.isDirty()));
    }

    @Test
    @DisplayName("3-A2: Phase3 ○ 未登録 + 定員超過 → WAITLISTED で新規登録")
    void testPhase3Maru_3A2_unregistered_overCapacity_createsAsWaitlisted() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantService.isFreeRegistrationOpen(session, 1)).thenReturn(false);
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1)).thenReturn(Optional.of(3));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceParticipantRepository).save(argThat(p ->
                p.getPlayerId().equals(1L) &&
                p.getStatus() == ParticipantStatus.WAITLISTED &&
                p.getWaitlistNumber() == 4 &&
                p.isDirty()));
    }

    @Test
    @DisplayName("3-A3: Phase3 ○ 未登録 + 定員未設定 → WON で新規登録")
    void testPhase3Maru_3A3_unregistered_noCapacity_createsAsWon() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(null).organizationId(1L).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantService.isFreeRegistrationOpen(session, 1)).thenReturn(true);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceParticipantRepository).save(argThat(p ->
                p.getPlayerId().equals(1L) &&
                p.getStatus() == ParticipantStatus.WON));
    }

    @Test
    @DisplayName("3-A4: Phase3 ○ WON + dirty=false → スキップ")
    void testPhase3Maru_3A4_won_notDirty_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("3-A5: Phase3 ○ WON + dirty=true → スキップ（dirty保護）")
    void testPhase3Maru_3A5_won_dirty_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(true).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("3-A6: Phase3 ○ WAITLISTED + dirty=false → dirty=true に設定（△書き戻し）")
    void testPhase3Maru_3A6_waitlisted_notDirty_setsDirtyTrue() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).dirty(false).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(lotteryDeadlineHelper.isAfterSameDayNoon(date)).thenReturn(false);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.isDirty()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        verify(practiceParticipantRepository).save(existing);
    }

    @Test
    @DisplayName("3-A7: Phase3 ○ WAITLISTED + dirty=true → スキップ（dirty保護）")
    void testPhase3Maru_3A7_waitlisted_dirty_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).dirty(true).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("3-A8a: Phase3 ○ OFFERED + dirty=false + 期限内 → オファー承認")
    void testPhase3Maru_3A8a_offered_notDirty_deadlineValid_acceptsOffer() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.OFFERED).dirty(false)
                .offerDeadline(LocalDateTime.of(2099, 12, 31, 23, 59)).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(waitlistPromotionService).respondToOffer(50L, true);
    }

    @Test
    @DisplayName("3-A8b: Phase3 ○ OFFERED + dirty=false + 期限切れ → スキップ")
    void testPhase3Maru_3A8b_offered_notDirty_deadlineExpired_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.OFFERED).dirty(false)
                .offerDeadline(LocalDateTime.of(2020, 1, 1, 0, 0)).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(waitlistPromotionService, never()).respondToOffer(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("3-A9: Phase3 ○ OFFERED + dirty=true → スキップ（dirty保護）")
    void testPhase3Maru_3A9_offered_dirty_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.OFFERED).dirty(true)
                .offerDeadline(LocalDateTime.of(2099, 12, 31, 23, 59)).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(waitlistPromotionService, never()).respondToOffer(anyLong(), anyBoolean());
        verify(practiceParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("3-A10: Phase3 ○ CANCELLED + dirty=false + 空きあり → WON で再活性化")
    void testPhase3Maru_3A10_cancelled_notDirty_freeCapacity_reactivatesAsWon() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.CANCELLED).dirty(false)
                .cancelReason("USER_REQUEST").cancelledAt(LocalDateTime.of(2026, 4, 1, 10, 0)).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));
        when(practiceParticipantService.isFreeRegistrationOpen(session, 1)).thenReturn(true);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.WON);
        assertThat(existing.getWaitlistNumber()).isNull();
        assertThat(existing.getCancelReason()).isNull();
        assertThat(existing.getCancelledAt()).isNull();
        assertThat(existing.isDirty()).isTrue();
        verify(practiceParticipantRepository).save(existing);
    }

    @Test
    @DisplayName("3-A11: Phase3 ○ CANCELLED + dirty=false + 定員超過 → WAITLISTED で再活性化")
    void testPhase3Maru_3A11_cancelled_notDirty_overCapacity_reactivatesAsWaitlisted() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 5);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(50L).sessionId(100L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.CANCELLED).dirty(false)
                .cancelReason("USER_REQUEST").cancelledAt(LocalDateTime.of(2026, 4, 1, 10, 0)).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(100L, 1))
                .thenReturn(List.of(existing));
        when(practiceParticipantService.isFreeRegistrationOpen(session, 1)).thenReturn(false);
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1)).thenReturn(Optional.of(5));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(existing.getWaitlistNumber()).isEqualTo(6);
        assertThat(existing.getCancelReason()).isNull();
        assertThat(existing.getCancelledAt()).isNull();
        assertThat(existing.isDirty()).isTrue();
        verify(practiceParticipantRepository).save(existing);
    }

    // ================================================================
    // 既存テスト: dirty フラグ関連
    // ================================================================

    @Test
    @DisplayName("伝助から追加された参加者はdirty=falseで登録される")
    void testImportSetsCleanFlagForDensukeAddedParticipants() throws IOException {
        DensukeData data = createSampleData();

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(any(), eq(1L))).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(Collections.emptyList());

        densukeImportService.importFromDensuke("http://example.com", null, 10L, 1L);

        ArgumentCaptor<PracticeParticipant> captor =
                ArgumentCaptor.forClass(PracticeParticipant.class);
        verify(practiceParticipantRepository, times(2)).save(captor.capture());

        captor.getAllValues().forEach(p ->
                assertThat(p.isDirty()).as("伝助追加参加者はdirty=false").isFalse());
    }
    // ================================================================
    // 空き枠統合通知テスト
    // ================================================================

    @Test
    @DisplayName("Phase3で複数WON登録があってもsendConsolidatedSameDayVacancyNotificationは1回だけ呼ばれる")
    void testPhase3_multipleWonRegistrations_sendsConsolidatedNotificationOnce() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 10);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        entry.getParticipants().add("鈴木");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中", "鈴木"));

        PracticeSession session = PracticeSession.builder()
                .id(200L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();

        setupPhase3Mocks(data, session, date, List.of(player1, player2));
        when(lotteryDeadlineHelper.isAfterSameDayNoon(date)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(200L, 1))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantService.isFreeRegistrationOpen(session, 1)).thenReturn(true);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(200L, 1, ParticipantStatus.WON))
                .thenReturn(12L);

        densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        verify(lineNotificationService, times(1)).sendConsolidatedSameDayVacancyNotification(
                eq(session), anyMap(), isNull());
        verify(lineNotificationService, times(1)).sendConsolidatedAdminVacancyNotification(
                eq(session), anyMap());
        verify(lineNotificationService, never()).sendSameDayVacancyUpdateNotification(
                any(), anyInt(), anyString(), anyLong());
    }

    @Test
    @DisplayName("2回目の同期でWON状態が変わらなければ空き枚通知は送信されない")
    void testPhase3_secondSync_noStateChange_noNotification() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 10);
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(1);
        entry.getParticipants().add("田中");
        data.getEntries().add(entry);
        data.setMemberNames(List.of("田中"));

        PracticeSession session = PracticeSession.builder()
                .id(200L).sessionDate(date).totalMatches(1).capacity(14).organizationId(1L).build();

        // 既にWONの参加者がいる（前回の同期で登録済み）
        PracticeParticipant existingWon = PracticeParticipant.builder()
                .id(50L).sessionId(200L).playerId(1L).matchNumber(1)
                .status(ParticipantStatus.WON).dirty(false).build();

        setupPhase3Mocks(data, session, date, List.of(player1));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(200L, 1))
                .thenReturn(List.of(existingWon));

        densukeImportService.importFromDensuke("http://example.com", null, 0L, 1L);

        // WON状態が変わらないので通知は送信されない
        verify(lineNotificationService, never()).sendConsolidatedSameDayVacancyNotification(
                any(), anyMap(), any());
        verify(lineNotificationService, never()).sendConsolidatedAdminVacancyNotification(
                any(), anyMap());
    }

}
