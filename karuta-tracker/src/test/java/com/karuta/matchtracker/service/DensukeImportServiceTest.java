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
    @Mock private VenueRepository venueRepository;
    @Mock private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;

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
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(List.of(
                Venue.builder().id(100L).name("すずらん").build()));
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.empty());
        when(practiceSessionRepository.save(any())).thenAnswer(inv -> {
            PracticeSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(anyLong()))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L);

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
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.of(existingSession));
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(99L))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(99L, 1))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L);

        assertThat(result.getCreatedSessionCount()).isEqualTo(0);
        verify(practiceSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("抽選済みセッションの参加者はスキップされる")
    void testImportSkipsLotteryExecutedSession() throws IOException {
        DensukeData data = createSampleData();
        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.of(session));
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(1L))
                .thenReturn(Optional.of(LotteryExecution.builder().id(1L).build()));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L);

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
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.of(session));
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(Collections.emptyList());

        // 管理者への通知用
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(Collections.emptyList());
        when(playerRepository.findByRoleAndActive(Player.Role.ADMIN)).thenReturn(List.of(adminPlayer));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L);

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
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.of(session));
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(Collections.emptyList());
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(Collections.emptyList());
        when(playerRepository.findByRoleAndActive(Player.Role.ADMIN)).thenReturn(List.of(adminPlayer));

        densukeImportService.importFromDensuke("http://example.com", null, 10L);

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
        PracticeParticipant existingParticipant = PracticeParticipant.builder()
                .id(50L).sessionId(1L).playerId(2L).matchNumber(1).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.of(session));
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(List.of(existingParticipant));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 10L);

        assertThat(result.getRemovedCount()).isEqualTo(1);
        verify(practiceParticipantRepository).delete(existingParticipant);
    }

    @Test
    @DisplayName("registerAndSyncで未登録者が自動登録される")
    void testRegisterAndSync() throws IOException {
        DensukeData data = createSampleData();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerRepository.findByNameAndActive("新人")).thenReturn(Optional.empty());
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.empty());
        when(practiceSessionRepository.save(any())).thenAnswer(inv -> {
            PracticeSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(anyLong()))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        densukeImportService.registerAndSync(List.of("新人"), "http://example.com", null, 10L);

        ArgumentCaptor<Player> playerCaptor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(playerCaptor.capture());

        Player savedPlayer = playerCaptor.getValue();
        assertThat(savedPlayer.getName()).isEqualTo("新人");
        assertThat(savedPlayer.getRequirePasswordChange()).isTrue();
        assertThat(savedPlayer.getRole()).isEqualTo(Player.Role.PLAYER);
    }

    @Test
    @DisplayName("targetDateで対象日付のフィルタが機能する")
    void testImportWithTargetDateFilter() throws IOException {
        DensukeData data = new DensukeData();

        ScheduleEntry entry1 = new ScheduleEntry();
        entry1.setDate(LocalDate.of(2026, 4, 1));
        entry1.setMatchNumber(1);
        entry1.getParticipants().add("田中");
        data.getEntries().add(entry1);

        ScheduleEntry entry2 = new ScheduleEntry();
        entry2.setDate(LocalDate.of(2026, 4, 8));
        entry2.setMatchNumber(1);
        entry2.getParticipants().add("鈴木");
        data.getEntries().add(entry2);

        PracticeSession session = PracticeSession.builder().id(1L)
                .sessionDate(LocalDate.of(2026, 4, 1)).totalMatches(3).build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(LocalDate.of(2026, 4, 1)))
                .thenReturn(Optional.of(session));
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(1L))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(1L, 1))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke(
                "http://example.com", LocalDate.of(2026, 4, 1), 10L);

        // 4/1だけが処理され、4/8はスキップされる
        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceSessionRepository, never()).findBySessionDate(LocalDate.of(2026, 4, 8));
    }

    @Test
    @DisplayName("createdByがセッション作成時に正しく設定される")
    void testCreatedByIsPassedToSession() throws IOException {
        DensukeData data = createSampleData();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerRepository.findAll()).thenReturn(List.of(player1, player2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDate(any())).thenReturn(Optional.empty());
        when(practiceSessionRepository.save(any())).thenAnswer(inv -> {
            PracticeSession s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });
        when(lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(anyLong()))
                .thenReturn(Optional.empty());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());

        densukeImportService.importFromDensuke("http://example.com", null, 42L);

        verify(practiceSessionRepository).save(argThat(session ->
                session.getCreatedBy().equals(42L) && session.getUpdatedBy().equals(42L)));
    }

    @Test
    @DisplayName("SYSTEM_USER_IDが0Lとして定義されている")
    void testSystemUserIdConstant() {
        assertThat(DensukeImportService.SYSTEM_USER_ID).isEqualTo(0L);
    }
}
