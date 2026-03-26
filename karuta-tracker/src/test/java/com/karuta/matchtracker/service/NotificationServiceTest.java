package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 通知まとめテスト")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PushNotificationService pushNotificationService;

    @InjectMocks
    private NotificationService service;

    @Captor
    private ArgumentCaptor<List<Notification>> notificationsCaptor;

    private PracticeSession createSession(Long id, LocalDate date) {
        PracticeSession s = new PracticeSession();
        s.setId(id);
        s.setSessionDate(date);
        return s;
    }

    @Test
    @DisplayName("全当選の場合 LOTTERY_ALL_WON 1レコードが作成される")
    void allWon() {
        PracticeSession session = createSession(100L, LocalDate.of(2025, 4, 5));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        List<PracticeParticipant> participants = List.of(
                PracticeParticipant.builder().id(1L).sessionId(100L).playerId(10L)
                        .matchNumber(1).status(ParticipantStatus.WON).build(),
                PracticeParticipant.builder().id(2L).sessionId(100L).playerId(10L)
                        .matchNumber(2).status(ParticipantStatus.WON).build()
        );

        int count = service.createLotteryResultNotifications(participants);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        List<Notification> saved = notificationsCaptor.getValue();

        assertThat(count).isEqualTo(1);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getType()).isEqualTo(NotificationType.LOTTERY_ALL_WON);
        assertThat(saved.get(0).getPlayerId()).isEqualTo(10L);
        assertThat(saved.get(0).getMessage()).contains("すべて当選");
    }

    @Test
    @DisplayName("一部落選の場合 LOTTERY_WAITLISTED + LOTTERY_REMAINING_WON が作成される")
    void partialWaitlisted() {
        PracticeSession s1 = createSession(100L, LocalDate.of(2025, 4, 5));
        PracticeSession s2 = createSession(101L, LocalDate.of(2025, 4, 12));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(s1));
        when(practiceSessionRepository.findById(101L)).thenReturn(Optional.of(s2));

        List<PracticeParticipant> participants = List.of(
                PracticeParticipant.builder().id(1L).sessionId(100L).playerId(10L)
                        .matchNumber(1).status(ParticipantStatus.WON).build(),
                PracticeParticipant.builder().id(2L).sessionId(101L).playerId(10L)
                        .matchNumber(1).status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build()
        );

        int count = service.createLotteryResultNotifications(participants);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        List<Notification> saved = notificationsCaptor.getValue();

        assertThat(count).isEqualTo(2); // WAITLISTED + REMAINING_WON
        assertThat(saved).hasSize(2);

        Notification waitlisted = saved.stream()
                .filter(n -> n.getType() == NotificationType.LOTTERY_WAITLISTED).findFirst().orElseThrow();
        assertThat(waitlisted.getMessage()).contains("試合1: キャンセル待ち2番");
        assertThat(waitlisted.getReferenceType()).isEqualTo("PRACTICE_SESSION");
        assertThat(waitlisted.getReferenceId()).isEqualTo(101L);

        Notification remaining = saved.stream()
                .filter(n -> n.getType() == NotificationType.LOTTERY_REMAINING_WON).findFirst().orElseThrow();
        assertThat(remaining.getMessage()).contains("すべて当選");
    }

    @Test
    @DisplayName("全落選の場合 LOTTERY_WAITLISTED のみでREMAINING_WONは作成されない")
    void allWaitlisted() {
        PracticeSession session = createSession(100L, LocalDate.of(2025, 4, 5));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        List<PracticeParticipant> participants = List.of(
                PracticeParticipant.builder().id(1L).sessionId(100L).playerId(10L)
                        .matchNumber(1).status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build(),
                PracticeParticipant.builder().id(2L).sessionId(100L).playerId(10L)
                        .matchNumber(2).status(ParticipantStatus.WAITLISTED).waitlistNumber(3).build()
        );

        int count = service.createLotteryResultNotifications(participants);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        List<Notification> saved = notificationsCaptor.getValue();

        assertThat(count).isEqualTo(1); // 1セッション分のWAITLISTEDのみ
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getType()).isEqualTo(NotificationType.LOTTERY_WAITLISTED);
        assertThat(saved.stream().noneMatch(n -> n.getType() == NotificationType.LOTTERY_REMAINING_WON)).isTrue();
    }

    @Test
    @DisplayName("複数プレイヤーが正しくグルーピングされる")
    void multiplePlayersGrouped() {
        PracticeSession session = createSession(100L, LocalDate.of(2025, 4, 5));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        List<PracticeParticipant> participants = List.of(
                PracticeParticipant.builder().id(1L).sessionId(100L).playerId(10L)
                        .matchNumber(1).status(ParticipantStatus.WON).build(),
                PracticeParticipant.builder().id(2L).sessionId(100L).playerId(20L)
                        .matchNumber(1).status(ParticipantStatus.WON).build()
        );

        int count = service.createLotteryResultNotifications(participants);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        List<Notification> saved = notificationsCaptor.getValue();

        assertThat(count).isEqualTo(2); // 2プレイヤー × LOTTERY_ALL_WON
        assertThat(saved.stream().filter(n -> n.getPlayerId() == 10L).count()).isEqualTo(1);
        assertThat(saved.stream().filter(n -> n.getPlayerId() == 20L).count()).isEqualTo(1);
    }
}
