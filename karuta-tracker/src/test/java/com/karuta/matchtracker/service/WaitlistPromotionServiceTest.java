package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitlistPromotionService 辞退・復帰テスト")
class WaitlistPromotionServiceTest {

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private LineNotificationService lineNotificationService;
    @Mock
    private DensukeSyncService densukeSyncService;

    @InjectMocks
    private WaitlistPromotionService service;

    @Test
    @DisplayName("セッション単位でキャンセル待ちを辞退できる")
    void declineWaitlistBySession_success() {
        PracticeParticipant p1 = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
        PracticeParticipant p2 = PracticeParticipant.builder()
                .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();

        PracticeSession session = PracticeSession.builder().id(100L).sessionDate(LocalDate.of(2026, 5, 1)).build();
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of(p1, p2));
        when(practiceParticipantRepository.findWaitlistedAfterNumber(eq(100L), eq(1), eq(2)))
                .thenReturn(List.of());
        when(practiceParticipantRepository.findWaitlistedAfterNumber(eq(100L), eq(3), eq(1)))
                .thenReturn(List.of());
        when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(eq(100L), anyInt(), eq(ParticipantStatus.WAITLISTED)))
                .thenReturn(List.of());

        int count = service.declineWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(2);
        assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.WAITLIST_DECLINED);
        assertThat(p1.getWaitlistNumber()).isNull();
        assertThat(p2.getStatus()).isEqualTo(ParticipantStatus.WAITLIST_DECLINED);
        verify(practiceParticipantRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("辞退時に後続のキャンセル待ち番号が繰り上がる")
    void declineWaitlistBySession_renumbersSubsequent() {
        PracticeParticipant target = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
        PracticeParticipant next = PracticeParticipant.builder()
                .id(3L).sessionId(100L).playerId(30L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(3).build();

        PracticeSession session = PracticeSession.builder().id(100L).sessionDate(LocalDate.of(2026, 5, 1)).build();
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of(target));
        when(practiceParticipantRepository.findWaitlistedAfterNumber(100L, 1, 2))
                .thenReturn(List.of(next));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));
        when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(eq(100L), eq(1), eq(ParticipantStatus.WAITLISTED)))
                .thenReturn(List.of(next));

        service.declineWaitlistBySession(100L, 10L);

        assertThat(next.getWaitlistNumber()).isEqualTo(2);
        verify(practiceParticipantRepository, times(2)).save(any()); // target + next
    }

    @Test
    @DisplayName("辞退対象がない場合はエラー")
    void declineWaitlistBySession_noTarget() {
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.declineWaitlistBySession(100L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("辞退対象");
    }

    @Test
    @DisplayName("セッション単位でキャンセル待ちに復帰できる（最後尾）")
    void rejoinWaitlistBySession_success() {
        PracticeParticipant p1 = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLIST_DECLINED).build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLIST_DECLINED))
                .thenReturn(List.of(p1));
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1))
                .thenReturn(Optional.of(3));

        int count = service.rejoinWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(1);
        assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(p1.getWaitlistNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("復帰時にキャンセル待ちがいない場合は番号1になる")
    void rejoinWaitlistBySession_emptyWaitlist() {
        PracticeParticipant p1 = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLIST_DECLINED).build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLIST_DECLINED))
                .thenReturn(List.of(p1));
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1))
                .thenReturn(Optional.empty());

        service.rejoinWaitlistBySession(100L, 10L);

        assertThat(p1.getWaitlistNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("復帰対象がない場合はエラー")
    void rejoinWaitlistBySession_noTarget() {
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLIST_DECLINED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.rejoinWaitlistBySession(100L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("復帰対象");
    }

    @Nested
    @DisplayName("当日12:00以降キャンセル→補充フロー")
    class SameDayCancelTests {

        @Test
        @DisplayName("12:00以降のキャンセルでキャンセル通知＋空き募集通知が送信される")
        void cancelAfterNoon_triggersVacancyRecruitment() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.WON).build();
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 15)).capacity(6).build();
            Player player = Player.builder().id(10L).name("テスト選手").build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())).thenReturn(true);
            when(playerRepository.findById(10L)).thenReturn(Optional.of(player));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(eq(100L), eq(1), eq(ParticipantStatus.WAITLISTED)))
                    .thenReturn(List.of());

            ParticipantStatus result = service.cancelParticipation(1L, "HEALTH", null);

            assertThat(result).isEqualTo(ParticipantStatus.CANCELLED);
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.CANCELLED);

            // キャンセル通知が送信されたことを検証
            verify(lineNotificationService).sendSameDayCancelNotification(
                    eq(session), eq(1), eq("テスト選手"), eq(10L));
            // 空き募集通知が送信されたことを検証
            verify(lineNotificationService).sendSameDayVacancyNotification(
                    eq(session), eq(1), eq(10L));
            // 従来の繰り上げフローは発動しないことを検証
            verify(lineNotificationService, never()).sendWaitlistOfferNotification(any());
        }

        @Test
        @DisplayName("12:00より前のキャンセルでは従来の繰り上げフローが発動する")
        void cancelBeforeNoon_triggersTraditionalPromotion() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.WON).build();
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 15)).capacity(6).build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())).thenReturn(false);
            when(practiceParticipantRepository.findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                    100L, 1, ParticipantStatus.WAITLISTED)).thenReturn(Optional.empty());
            when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(eq(100L), eq(1), eq(ParticipantStatus.WAITLISTED)))
                    .thenReturn(List.of());

            service.cancelParticipation(1L);

            // 新フロー通知は送信されないことを検証
            verify(lineNotificationService, never()).sendSameDayCancelNotification(any(), anyInt(), any(), any());
            verify(lineNotificationService, never()).sendSameDayVacancyNotification(any(), anyInt(), any());
        }
    }

    @Nested
    @DisplayName("当日補充参加（same_day_join）")
    class SameDayJoinTests {

        @Test
        @DisplayName("空き枠がある場合にWONとして参加できる")
        void handleSameDayJoin_success() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 15))
                    .capacity(6).startTime(LocalTime.of(13, 0)).build();
            Player player = Player.builder().id(20L).name("参加者").build();

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 20L, 1))
                    .thenReturn(List.of());
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(List.of()); // 空き枠あり
            when(playerRepository.findById(20L)).thenReturn(Optional.of(player));

            service.handleSameDayJoin(100L, 1, 20L);

            verify(practiceParticipantRepository).save(any(PracticeParticipant.class));
            verify(lineNotificationService).sendSameDayJoinNotification(eq(session), eq(1), eq("参加者"), eq(20L));
            verify(lineNotificationService).sendSameDayVacancyUpdateNotification(eq(session), eq(1), eq(20L));
        }

        @Test
        @DisplayName("枠が埋まっている場合はエラー")
        void handleSameDayJoin_noVacancy() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 15))
                    .capacity(1).startTime(LocalTime.of(13, 0)).build();
            PracticeParticipant existing = PracticeParticipant.builder()
                    .id(5L).sessionId(100L).playerId(30L).matchNumber(1).status(ParticipantStatus.WON).build();

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(100L, 20L, 1))
                    .thenReturn(List.of());
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(List.of(existing)); // 定員1、既に1人

            assertThatThrownBy(() -> service.handleSameDayJoin(100L, 1, 20L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("先を越されました");
        }
    }
}
