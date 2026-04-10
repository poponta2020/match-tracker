package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.dto.AdjacentRoomStatusDto;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.AdjacentRoomNotificationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.AdjacentRoomService;
import com.karuta.matchtracker.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdjacentRoomNotificationScheduler テスト")
class AdjacentRoomNotificationSchedulerTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private AdjacentRoomNotificationRepository adjacentRoomNotificationRepository;
    @Mock
    private AdjacentRoomService adjacentRoomService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AdjacentRoomNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        // TransactionTemplateのexecuteをコールバック即実行に設定
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private PracticeSession buildKaderuSession(Long id, int capacity, int totalMatches) {
        return PracticeSession.builder()
                .id(id)
                .sessionDate(LocalDate.now().plusDays(3))
                .venueId(3L) // すずらん
                .capacity(capacity)
                .totalMatches(totalMatches)
                .organizationId(1L)
                .build();
    }

    private Player buildAdmin(Long id) {
        return Player.builder().id(id).name("Admin" + id).role(Player.Role.SUPER_ADMIN).build();
    }

    @Test
    @DisplayName("定員まで残り3人で隣室空き → 通知送信")
    void notify_whenCapacityNearAndAdjacentAvailable() {
        PracticeSession session = buildKaderuSession(1L, 14, 1);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        // 11人参加中 → 残り3人
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(11L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.PENDING))
                .thenReturn(0L);

        AdjacentRoomStatusDto status = AdjacentRoomStatusDto.builder()
                .adjacentRoomName("はまなす").status("○").available(true)
                .expandedVenueId(7L).expandedVenueName("すずらん・はまなす").expandedCapacity(24)
                .build();
        when(adjacentRoomService.getAdjacentRoomAvailability(3L, session.getSessionDate())).thenReturn(status);

        Player admin = buildAdmin(10L);
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(List.of(admin));
        when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L)).thenReturn(List.of());

        scheduler.checkCapacityAndNotify();

        verify(notificationService).createAndPush(eq(10L), eq(NotificationType.ADJACENT_ROOM_AVAILABLE),
                contains("残り3人"), anyString(), eq("PRACTICE_SESSION"), eq(1L), eq("/practice"), eq(1L));
        verify(adjacentRoomNotificationRepository).save(any());
    }

    @Test
    @DisplayName("既に通知済みの段階 → 通知しない")
    void noNotify_whenAlreadyNotified() {
        PracticeSession session = buildKaderuSession(1L, 14, 1);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(11L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.PENDING))
                .thenReturn(0L);

        // 残り3人で既に通知済み → save時に一意制約違反
        when(adjacentRoomNotificationRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        scheduler.checkCapacityAndNotify();

        verify(notificationService, never()).createAndPush(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("隣室が予約済み → 通知しない")
    void noNotify_whenAdjacentNotAvailable() {
        PracticeSession session = buildKaderuSession(1L, 14, 1);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(12L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.PENDING))
                .thenReturn(0L);

        AdjacentRoomStatusDto status = AdjacentRoomStatusDto.builder()
                .adjacentRoomName("はまなす").status("×").available(false).build();
        when(adjacentRoomService.getAdjacentRoomAvailability(3L, session.getSessionDate())).thenReturn(status);

        scheduler.checkCapacityAndNotify();

        verify(notificationService, never()).createAndPush(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("残り5人以上 → 閾値未満なので通知しない")
    void noNotify_whenRemainingAboveThreshold() {
        PracticeSession session = buildKaderuSession(1L, 14, 1);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        // 8人参加 → 残り6人
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(8L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.PENDING))
                .thenReturn(0L);

        scheduler.checkCapacityAndNotify();

        verify(notificationService, never()).createAndPush(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("定員到達（残り0人）で通知")
    void notify_whenCapacityReached() {
        PracticeSession session = buildKaderuSession(1L, 14, 1);
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.WON))
                .thenReturn(14L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(1L, 1, ParticipantStatus.PENDING))
                .thenReturn(0L);

        AdjacentRoomStatusDto status = AdjacentRoomStatusDto.builder()
                .adjacentRoomName("はまなす").status("○").available(true)
                .expandedVenueId(7L).expandedVenueName("すずらん・はまなす").expandedCapacity(24)
                .build();
        when(adjacentRoomService.getAdjacentRoomAvailability(3L, session.getSessionDate())).thenReturn(status);

        Player admin = buildAdmin(10L);
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(List.of(admin));
        when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L)).thenReturn(List.of());

        scheduler.checkCapacityAndNotify();

        verify(notificationService).createAndPush(eq(10L), eq(NotificationType.ADJACENT_ROOM_AVAILABLE),
                contains("定員到達"), anyString(), eq("PRACTICE_SESSION"), eq(1L), eq("/practice"), eq(1L));
    }

    @Test
    @DisplayName("かでる和室以外のセッションはスキップ")
    void skip_nonKaderuSessions() {
        PracticeSession session = PracticeSession.builder()
                .id(1L).sessionDate(LocalDate.now().plusDays(3))
                .venueId(1L) // かでる和室以外
                .capacity(14).totalMatches(1).organizationId(1L)
                .build();
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        scheduler.checkCapacityAndNotify();

        verify(notificationService, never()).createAndPush(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
