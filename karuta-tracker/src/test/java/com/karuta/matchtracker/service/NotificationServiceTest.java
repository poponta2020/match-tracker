package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.PushNotificationPreference;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PushNotificationPreferenceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private PushNotificationPreferenceRepository pushNotificationPreferenceRepository;

    @InjectMocks
    private NotificationService service;

    @Captor
    private ArgumentCaptor<List<Notification>> notificationsCaptor;

    private PracticeSession createSession(Long id, LocalDate date) {
        PracticeSession s = new PracticeSession();
        s.setId(id);
        s.setSessionDate(date);
        s.setOrganizationId(1L);
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
    @DisplayName("一括削除でsoftDeleteAllByPlayerIdが呼ばれる")
    void deleteAllByPlayerId() {
        when(notificationRepository.softDeleteAllByPlayerId(eq(10L), any(LocalDateTime.class)))
                .thenReturn(5);

        int count = service.deleteAllByPlayerId(10L);

        assertThat(count).isEqualTo(5);
        verify(notificationRepository).softDeleteAllByPlayerId(eq(10L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("createAndPush: 設定ONの場合Web Push送信される")
    void createAndPush_enabledSendsPush() {
        PushNotificationPreference pref = PushNotificationPreference.builder()
                .playerId(10L).enabled(true).lotteryResult(true)
                .waitlistOffer(true).offerExpiring(true).offerExpired(true)
                .channelReclaimWarning(true).densukeUnmatched(true).build();
        when(pushNotificationPreferenceRepository.findByPlayerId(10L)).thenReturn(List.of(pref));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.createAndPush(10L, NotificationType.WAITLIST_OFFER,
                "テスト", "メッセージ", null, null, "/notifications");

        verify(notificationRepository).save(any(Notification.class));
        verify(pushNotificationService).sendPush(eq(10L), eq("テスト"), eq("メッセージ"), eq("/notifications"));
    }

    @Test
    @DisplayName("createAndPush: 設定OFFの場合Web Push送信されない")
    void createAndPush_disabledSkipsPush() {
        PushNotificationPreference pref = PushNotificationPreference.builder()
                .playerId(10L).enabled(true).lotteryResult(true)
                .waitlistOffer(false).offerExpiring(true).offerExpired(true)
                .channelReclaimWarning(true).densukeUnmatched(true).build();
        when(pushNotificationPreferenceRepository.findByPlayerId(10L)).thenReturn(List.of(pref));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.createAndPush(10L, NotificationType.WAITLIST_OFFER,
                "テスト", "メッセージ", null, null, "/notifications");

        verify(notificationRepository).save(any(Notification.class));
        verify(pushNotificationService, never()).sendPush(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("createAndPush: enabled=falseの場合Web Push送信されない")
    void createAndPush_globalDisabledSkipsPush() {
        PushNotificationPreference pref = PushNotificationPreference.builder()
                .playerId(10L).enabled(false).lotteryResult(true)
                .waitlistOffer(true).offerExpiring(true).offerExpired(true)
                .channelReclaimWarning(true).densukeUnmatched(true).build();
        when(pushNotificationPreferenceRepository.findByPlayerId(10L)).thenReturn(List.of(pref));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.createAndPush(10L, NotificationType.WAITLIST_OFFER,
                "テスト", "メッセージ", null, null, "/notifications");

        verify(notificationRepository).save(any(Notification.class));
        verify(pushNotificationService, never()).sendPush(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("createAndPush: 設定レコードなしの場合Web Push送信されない")
    void createAndPush_noPreferenceSkipsPush() {
        when(pushNotificationPreferenceRepository.findByPlayerId(10L)).thenReturn(List.of());
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        service.createAndPush(10L, NotificationType.LOTTERY_ALL_WON,
                "テスト", "メッセージ", null, null, "/practice");

        verify(notificationRepository).save(any(Notification.class));
        verify(pushNotificationService, never()).sendPush(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("createAndPush: 抽選結果3種はlotteryResultでまとめて判定される")
    void createAndPush_lotteryTypesShareSetting() {
        PushNotificationPreference pref = PushNotificationPreference.builder()
                .playerId(10L).enabled(true).lotteryResult(false)
                .waitlistOffer(true).offerExpiring(true).offerExpired(true)
                .channelReclaimWarning(true).densukeUnmatched(true).build();
        when(pushNotificationPreferenceRepository.findByPlayerId(10L)).thenReturn(List.of(pref));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        // LOTTERY_ALL_WON
        service.createAndPush(10L, NotificationType.LOTTERY_ALL_WON,
                "テスト", "メッセージ", null, null, "/practice");
        // LOTTERY_WAITLISTED
        service.createAndPush(10L, NotificationType.LOTTERY_WAITLISTED,
                "テスト", "メッセージ", null, null, "/practice");
        // LOTTERY_REMAINING_WON
        service.createAndPush(10L, NotificationType.LOTTERY_REMAINING_WON,
                "テスト", "メッセージ", null, null, "/practice");

        verify(notificationRepository, times(3)).save(any(Notification.class));
        verify(pushNotificationService, never()).sendPush(anyLong(), anyString(), anyString(), anyString());
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
