package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.ExpireOfferResult;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;

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
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));
        // 再採番用モック（辞退後の残存キュー）
        when(practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                        eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                .thenReturn(List.of());

        int count = service.declineWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(2);
        assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.WAITLIST_DECLINED);
        assertThat(p1.getWaitlistNumber()).isNull();
        assertThat(p2.getStatus()).isEqualTo(ParticipantStatus.WAITLIST_DECLINED);
        verify(practiceParticipantRepository, times(2)).save(any());
        // 各試合ごとに再採番が呼ばれる（通知処理からも呼ばれるためatLeast）
        verify(practiceParticipantRepository, atLeast(2))
                .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                        eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED)));
    }

    @Test
    @DisplayName("辞退時に残存キューが再採番される")
    void declineWaitlistBySession_renumbersRemaining() {
        PracticeParticipant target = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
        PracticeParticipant remaining = PracticeParticipant.builder()
                .id(2L).sessionId(100L).playerId(20L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(3).build();
        PracticeSession session = PracticeSession.builder().id(100L).sessionDate(LocalDate.of(2026, 5, 1)).build();
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of(target));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));
        // 辞退後の残存: #3のremainingのみ → #1に再採番
        when(practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                        eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                .thenReturn(List.of(remaining));

        service.declineWaitlistBySession(100L, 10L);

        // remainingが#3→#1に再採番される
        assertThat(remaining.getWaitlistNumber()).isEqualTo(1);
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
        when(practiceParticipantRepository.findMaxWaitlistNumberIncludingOffered(100L, 1))
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
        when(practiceParticipantRepository.findMaxWaitlistNumberIncludingOffered(100L, 1))
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
    @DisplayName("繰り上げ時のキャンセル待ち番号更新")
    class PromoteRenumberTests {

        @Test
        @DisplayName("OFFERED時には番号の再採番が行われない（離脱確定時に再採番する）")
        void promoteNextWaitlisted_doesNotRenumberOnOffer() {
            PracticeParticipant waitlist1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.of(waitlist1));
            when(lotteryDeadlineHelper.calculateOfferDeadline(any()))
                    .thenReturn(JstDateTimeUtil.now().plusDays(1));
            Optional<PracticeParticipant> promoted = service.promoteNextWaitlisted(
                    100L, 1, LocalDate.of(2026, 5, 1));

            assertThat(promoted).isPresent();
            assertThat(promoted.get().getStatus()).isEqualTo(ParticipantStatus.OFFERED);
            // OFFERED時点では再採番は呼ばれない
            verify(practiceParticipantRepository, never()).decrementWaitlistNumbersAfter(anyLong(), anyInt(), anyInt());
            verify(practiceParticipantRepository, never())
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(anyLong(), anyInt(), any());
            verify(practiceParticipantRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("キャンセル待ちが1人だけの場合もOFFERED時に再採番は行われない")
        void promoteNextWaitlisted_singleWaitlisted_noRenumber() {
            PracticeParticipant waitlist1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();

            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.of(waitlist1));
            when(lotteryDeadlineHelper.calculateOfferDeadline(any()))
                    .thenReturn(JstDateTimeUtil.now().plusDays(1));
            Optional<PracticeParticipant> promoted = service.promoteNextWaitlisted(
                    100L, 1, LocalDate.of(2026, 5, 1));

            assertThat(promoted).isPresent();
            assertThat(promoted.get().getStatus()).isEqualTo(ParticipantStatus.OFFERED);
            verify(practiceParticipantRepository, never()).decrementWaitlistNumbersAfter(anyLong(), anyInt(), anyInt());
            verify(practiceParticipantRepository, times(1)).save(any());
        }

        @Test
        @DisplayName("複数OFFERED存在時に離脱しても番号が重複しない（再採番で整合性維持）")
        void respondToOffer_multipleOffered_renumbersCorrectly() {
            // OFFERED#1 Aさん, OFFERED#2 Bさん, WAITLISTED#3 Cさん
            // Aさんが承認 → 残存B,CをOFFERED#1, WAITLISTED#2に再採番
            PracticeParticipant offeredA = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offeredAt(JstDateTimeUtil.now())
                    .offerDeadline(JstDateTimeUtil.now().plusDays(1)).build();
            PracticeParticipant offeredB = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(2).build();
            PracticeParticipant waitlistedC = PracticeParticipant.builder()
                    .id(3L).sessionId(100L).playerId(30L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(3).build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(offeredA));
            // 再採番用クエリ: A離脱後の残存はB(OFFERED#2), C(WAITLISTED#3)
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of(offeredB, waitlistedC));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(
                    PracticeSession.builder().id(100L).build()));

            service.respondToOffer(1L, true);

            // Aが離脱確定
            assertThat(offeredA.getStatus()).isEqualTo(ParticipantStatus.WON);
            assertThat(offeredA.getWaitlistNumber()).isNull();
            // 再採番によりB=#1, C=#2
            assertThat(offeredB.getWaitlistNumber()).isEqualTo(1);
            assertThat(waitlistedC.getWaitlistNumber()).isEqualTo(2);
        }
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

            service.cancelParticipation(1L);

            // 新フロー通知は送信されないことを検証
            verify(lineNotificationService, never()).sendSameDayCancelNotification(any(), anyInt(), any(), any());
            verify(lineNotificationService, never()).sendSameDayVacancyNotification(any(), anyInt(), any());
        }
    }


    @Nested
    @DisplayName("オファー期限切れ時の繰り上げLINE通知")
    class ExpireOfferTests {

        @Test
        @DisplayName("繰り上げありの場合、sendWaitlistOfferNotificationが呼ばれる")
        void expireOffer_withPromotion_sendsNotification() {
            PracticeParticipant offered = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 5, 1)).build();
            PracticeParticipant waitlisted = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();

            // 再採番用モック
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            // 繰り上げ対象あり
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.of(waitlisted));
            when(lotteryDeadlineHelper.calculateOfferDeadline(any()))
                    .thenReturn(java.time.LocalDateTime.of(2026, 5, 10, 18, 0));

            service.expireOffer(offered);

            assertThat(offered.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            verify(lineNotificationService).sendWaitlistOfferNotification(waitlisted);
        }

        @Test
        @DisplayName("繰り上げなしの場合、sendWaitlistOfferNotificationが呼ばれない")
        void expireOffer_withoutPromotion_doesNotSendNotification() {
            PracticeParticipant offered = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 5, 1)).build();

            // 再採番用モック
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            // 繰り上げ対象なし
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.empty());

            service.expireOffer(offered);

            assertThat(offered.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            verify(lineNotificationService, never()).sendWaitlistOfferNotification(any());
        }
    }
    @Nested
    @DisplayName("当日12:00確定時のOFFERED期限切れ処理")
    class ExpireOfferedForSameDayConfirmationTests {

        @Test
        @DisplayName("OFFERED参加者がDECLINEDになりdirty=trueが設定される")
        void expireOffered_setsDeclinedAndDirty() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 2)).capacity(14).build();
            PracticeParticipant offered = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(offered));
            when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(13L);

            service.expireOfferedForSameDayConfirmation(session);

            assertThat(offered.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            assertThat(offered.isDirty()).isTrue();
            assertThat(offered.getRespondedAt()).isNotNull();
            verify(practiceParticipantRepository).save(offered);
            verify(notificationService).createOfferExpiredNotification(offered);
            verify(lineNotificationService).sendOfferExpiredNotification(offered);
            verify(densukeSyncService).triggerWriteAsync();
        }

        @Test
        @DisplayName("空き枠がある場合に当日空き募集通知が送信される")
        void expireOffered_triggersVacancyRecruitment() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 2)).capacity(14).build();
            PracticeParticipant offered = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(offered));
            when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(13L); // 13/14 → 1枠空き

            service.expireOfferedForSameDayConfirmation(session);

            verify(lineNotificationService).sendSameDayVacancyNotification(session, 1, null);
        }

        @Test
        @DisplayName("定員に達している場合は空き募集通知が送信されない")
        void expireOffered_noVacancy_noRecruitment() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 2)).capacity(14).build();
            PracticeParticipant offered = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(offered));
            when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                    .thenReturn(14L); // 14/14 → 空きなし

            service.expireOfferedForSameDayConfirmation(session);

            verify(lineNotificationService, never()).sendSameDayVacancyNotification(any(), anyInt(), any());
        }

        @Test
        @DisplayName("OFFERED参加者がいない場合は何もしない")
        void expireOffered_noOffered() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 4, 2)).capacity(14).build();

            when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of());

            service.expireOfferedForSameDayConfirmation(session);

            verify(practiceParticipantRepository, never()).save(any());
            verify(lineNotificationService, never()).sendSameDayVacancyNotification(any(), anyInt(), any());
            verify(densukeSyncService, never()).triggerWriteAsync();
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
            verify(lineNotificationService).sendSameDayVacancyUpdateNotification(eq(session), eq(1), eq("参加者"), eq(20L));
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
                    .hasMessageContaining("定員に達してしまいました");
        }
    }

    @Nested
    @DisplayName("respondToOfferAll: 一括応答テスト")
    class RespondToOfferAllTest {

        @Test
        @DisplayName("全OFFEREDを一括承諾できる")
        void respondToOfferAll_acceptAll() {
            PracticeParticipant p1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0))
                    .build();
            PracticeParticipant p2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0))
                    .build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(p1, p2));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());

            int count = service.respondToOfferAll(100L, 10L, true);

            assertThat(count).isEqualTo(2);
            assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.WON);
            assertThat(p2.getStatus()).isEqualTo(ParticipantStatus.WON);
            assertThat(p1.getWaitlistNumber()).isNull();
            assertThat(p2.getWaitlistNumber()).isNull();
            verify(densukeSyncService).triggerWriteAsync();
        }

        @Test
        @DisplayName("全OFFEREDを一括辞退できる")
        void respondToOfferAll_declineAll() {
            PracticeParticipant p1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .build();
            PracticeParticipant p2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(p1, p2));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            // promoteNextWaitlisted用のモック（各試合で繰り上げ対象なし）
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(ParticipantStatus.WAITLISTED)))
                    .thenReturn(Optional.empty());
            when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));
            // 再採番用モック
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());

            int count = service.respondToOfferAll(100L, 10L, false);

            assertThat(count).isEqualTo(2);
            assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            assertThat(p2.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            verify(densukeSyncService).triggerWriteAsync();
        }

        @Test
        @DisplayName("一括辞退で期限切れOFFEREDはスキップされる")
        void respondToOfferAll_decline_skipsExpired() {
            PracticeParticipant valid = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0)) // 未来
                    .build();
            PracticeParticipant expired = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2020, 1, 1, 0, 0)) // 過去
                    .build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(valid, expired));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(ParticipantStatus.WAITLISTED)))
                    .thenReturn(Optional.empty());
            when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());

            service.respondToOfferAll(100L, 10L, false);

            assertThat(valid.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            assertThat(expired.getStatus()).isEqualTo(ParticipantStatus.OFFERED); // 期限切れはスキップ
        }

        @Test
        @DisplayName("一括辞退で全件期限切れの場合はエラーが発生する")
        void respondToOfferAll_decline_allExpired_throwsException() {
            PracticeParticipant expired1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2020, 1, 1, 0, 0))
                    .build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(expired1));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> service.respondToOfferAll(100L, 10L, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("すべてのオファーが期限切れです");
        }

        @Test
        @DisplayName("OFFEREDがない場合はエラー")
        void respondToOfferAll_noOffers() {
            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> service.respondToOfferAll(100L, 10L, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("応答可能なオファーがありません");
        }
    }

    @Nested
    @DisplayName("respondToOffer: 部分参加後の残りオファー通知テスト")
    class RespondToOfferRemainingTest {

        @Test
        @DisplayName("承諾後に残りOFFEREDがあれば通知が送信される")
        void respondToOffer_accept_sendsRemainingNotification() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0))
                    .build();

            PracticeParticipant remaining = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            // 残りOFFERED
            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(remaining));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(
                    PracticeSession.builder().id(100L).build()));

            service.respondToOffer(1L, true);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.WON);
            verify(lineNotificationService).sendRemainingOfferNotification(List.of(remaining));
            verify(densukeSyncService).triggerWriteAsync();
        }

        @Test
        @DisplayName("承諾時に管理者通知が送信される")
        void respondToOffer_accept_sendsAdminNotification() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0))
                    .build();
            PracticeSession session = PracticeSession.builder().id(100L).build();
            Player triggerPlayer = Player.builder().id(10L).name("テスト選手").build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of());
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(playerRepository.findById(10L)).thenReturn(Optional.of(triggerPlayer));

            service.respondToOffer(1L, true);

            verify(lineNotificationService).sendAdminWaitlistNotification(
                    eq("オファー承諾"), eq(triggerPlayer), eq(session), any(), any(), any());
        }

        @Test
        @DisplayName("承諾後に残りOFFEREDがなければ通知は送信されない")
        void respondToOffer_accept_noRemainingNotification() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0))
                    .build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            // 残りOFFEREDなし
            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of());
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(
                    PracticeSession.builder().id(100L).build()));

            service.respondToOffer(1L, true);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.WON);
            verify(lineNotificationService, never()).sendRemainingOfferNotification(any());
        }
    }

    @Nested
    @DisplayName("辞退起点の繰り上げLINE通知テスト")
    class DeclinePromotionLineNotificationTests {

        @Test
        @DisplayName("respondToOffer辞退時に繰り上げ先へLINE通知が送信される")
        void respondToOffer_decline_sendsLineToPromoted() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0))
                    .build();
            PracticeParticipant nextWaitlisted = PracticeParticipant.builder()
                    .id(5L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            // 再採番用
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            // 繰り上げ候補
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.of(nextWaitlisted));
            when(lotteryDeadlineHelper.calculateOfferDeadline(any()))
                    .thenReturn(java.time.LocalDateTime.of(2026, 5, 10, 18, 0));
            when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));

            service.respondToOffer(1L, false);

            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            // 繰り上げ先へLINE通知が送信される
            verify(lineNotificationService).sendWaitlistOfferNotification(nextWaitlisted);
        }

        @Test
        @DisplayName("respondToOfferAll辞退時に各試合の繰り上げ先へLINE通知が送信される")
        void respondToOfferAll_decline_sendsLineToPromoted() {
            PracticeParticipant p1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeParticipant p2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeParticipant nextForMatch1 = PracticeParticipant.builder()
                    .id(5L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
            PracticeParticipant nextForMatch3 = PracticeParticipant.builder()
                    .id(6L).sessionId(100L).playerId(30L).matchNumber(3)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(p1, p2));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            // 再採番用
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            // 繰り上げ候補（試合別）
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.of(nextForMatch1));
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 3, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.of(nextForMatch3));
            when(lotteryDeadlineHelper.calculateOfferDeadline(any()))
                    .thenReturn(java.time.LocalDateTime.of(2026, 5, 10, 18, 0));
            when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));

            service.respondToOfferAll(100L, 10L, false);

            // 繰り上げ先プレイヤーごとに統合LINE通知が送信される（プレイヤーが異なるので2回）
            verify(lineNotificationService, times(2)).sendConsolidatedWaitlistOfferNotification(
                    anyList(), any(PracticeSession.class), eq("オファー辞退"), eq(10L));
        }

        @Test
        @DisplayName("辞退時に繰り上げ対象がいなければLINE通知は送信されない")
        void respondToOffer_decline_noWaitlisted_noLineNotification() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0))
                    .build();
            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.empty());
            when(playerRepository.findById(10L)).thenReturn(Optional.of(Player.builder().id(10L).name("テスト選手").build()));

            service.respondToOffer(1L, false);

            verify(lineNotificationService, never()).sendWaitlistOfferNotification(any());
        }
    }

    @Nested
    @DisplayName("respondToOfferAll: 期限切れスキップ時の件数テスト")
    class RespondToOfferAllExpiredCountTests {

        @Test
        @DisplayName("一括承諾で期限切れがスキップされた場合、実処理件数が返される")
        void respondToOfferAll_accept_skipsExpired_returnsActualCount() {
            PracticeParticipant valid = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2026, 5, 10, 18, 0)) // 未来
                    .build();
            PracticeParticipant expired = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2020, 1, 1, 0, 0)) // 過去
                    .build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(valid, expired));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), anyInt(), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());

            int count = service.respondToOfferAll(100L, 10L, true);

            assertThat(count).isEqualTo(1); // 2件中1件のみ承諾
            assertThat(valid.getStatus()).isEqualTo(ParticipantStatus.WON);
            assertThat(expired.getStatus()).isEqualTo(ParticipantStatus.OFFERED); // 変更なし
        }

        @Test
        @DisplayName("一括承諾で全件期限切れの場合はエラーが発生する")
        void respondToOfferAll_accept_allExpired_throwsException() {
            PracticeParticipant expired1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2020, 1, 1, 0, 0))
                    .build();
            PracticeParticipant expired2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                    .offerDeadline(java.time.LocalDateTime.of(2020, 1, 1, 0, 0))
                    .build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(expired1, expired2));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> service.respondToOfferAll(100L, 10L, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("すべてのオファーが期限切れです");
        }
    }

    @Test
    @DisplayName("promoteNextWaitlistedはLINE通知を送信しない")
    void promoteNextWaitlisted_doesNotSendLineNotification() {
        PracticeParticipant waitlisted = PracticeParticipant.builder()
                .id(5L).sessionId(100L).playerId(20L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();

        when(practiceParticipantRepository
                .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                        100L, 1, ParticipantStatus.WAITLISTED))
                .thenReturn(Optional.of(waitlisted));
        when(lotteryDeadlineHelper.calculateOfferDeadline(any()))
                .thenReturn(java.time.LocalDateTime.of(2026, 5, 10, 18, 0));

        Optional<PracticeParticipant> result = service.promoteNextWaitlisted(
                100L, 1, LocalDate.of(2026, 5, 5));

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        // アプリ内通知は送信される
        verify(notificationService).createOfferNotification(any());
        // LINE通知は送信されない（呼び出し元でバッチ送信するため）
        verify(lineNotificationService, never()).sendWaitlistOfferNotification(any());
    }

    @Nested
    @DisplayName("expireOfferSuppressed: 通知抑制版オファー期限切れ処理")
    class ExpireOfferSuppressedTest {

        @Test
        @DisplayName("OFFERED状態の参加者を渡すとDECLINEDに変更されExpireOfferResultが返る")
        void expireOfferSuppressed_offeredParticipant_returnResult() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeParticipant nextWaitlisted = PracticeParticipant.builder()
                    .id(5L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.of(nextWaitlisted));
            when(lotteryDeadlineHelper.calculateOfferDeadline(any()))
                    .thenReturn(java.time.LocalDateTime.of(2026, 5, 10, 18, 0));

            ExpireOfferResult result = service.expireOfferSuppressed(participant);

            assertThat(result).isNotNull();
            assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
            assertThat(participant.getWaitlistNumber()).isNull();
            assertThat(result.getPromotedParticipant()).isEqualTo(nextWaitlisted);
            assertThat(result.getNotificationData()).isNotNull();
            assertThat(result.getNotificationData().getTriggerAction()).isEqualTo("オファー期限切れ");
            assertThat(result.getNotificationData().getSessionId()).isEqualTo(100L);
            assertThat(result.getNotificationData().getMatchNumber()).isEqualTo(1);
            verify(practiceParticipantRepository).save(participant);
        }

        @Test
        @DisplayName("OFFERED状態の参加者を渡した場合、sendWaitlistOfferNotificationが呼ばれない（通知抑制）")
        void expireOfferSuppressed_doesNotSendWaitlistOfferNotification() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.empty());

            service.expireOfferSuppressed(participant);

            // 繰り上げ先へのLINE通知は送信されない（呼び出し元でバッチ送信するため）
            verify(lineNotificationService, never()).sendWaitlistOfferNotification(any());
            verify(lineNotificationService, never()).sendConsolidatedWaitlistOfferNotification(
                    anyList(), any(PracticeSession.class), any(), any(Player.class));
            verify(lineNotificationService, never()).sendConsolidatedWaitlistOfferNotification(
                    anyList(), any(PracticeSession.class), any(), anyLong());
        }

        @Test
        @DisplayName("OFFERED状態の参加者を渡した場合、sendBatchedAdminWaitlistNotificationsが呼ばれない（通知抑制）")
        void expireOfferSuppressed_doesNotSendBatchedAdminNotifications() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.empty());

            service.expireOfferSuppressed(participant);

            // 管理者通知は送信されない（呼び出し元でバッチ送信するため）
            // sendBatchedAdminWaitlistNotificationsはservice自身のメソッドなのでspy不要、
            // 代わりにpracticeParticipantRepositoryの管理者通知関連呼び出しがないことを確認
            verify(playerRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("OFFERED以外の状態を渡した場合、nullが返る")
        void expireOfferSuppressed_nonOfferedStatus_returnsNull() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();

            ExpireOfferResult result = service.expireOfferSuppressed(participant);

            assertThat(result).isNull();
            verify(practiceParticipantRepository, never()).save(any());
        }

        @Test
        @DisplayName("DECLINED状態を渡した場合、nullが返る")
        void expireOfferSuppressed_declinedStatus_returnsNull() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.DECLINED).build();

            ExpireOfferResult result = service.expireOfferSuppressed(participant);

            assertThat(result).isNull();
            verify(practiceParticipantRepository, never()).save(any());
        }

        @Test
        @DisplayName("繰り上げ対象がいない場合もExpireOfferResultが返る（promotedParticipantはnull）")
        void expireOfferSuppressed_noWaitlisted_resultWithNullPromoted() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).waitlistNumber(1).build();
            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                            eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                    .thenReturn(List.of());
            when(practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            100L, 1, ParticipantStatus.WAITLISTED))
                    .thenReturn(Optional.empty());

            ExpireOfferResult result = service.expireOfferSuppressed(participant);

            assertThat(result).isNotNull();
            assertThat(result.getPromotedParticipant()).isNull();
            assertThat(result.getNotificationData().getPromotedParticipant()).isNull();
        }
    }
}
