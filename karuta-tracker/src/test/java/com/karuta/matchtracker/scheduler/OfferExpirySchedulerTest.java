package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.dto.ExpireOfferResult;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OfferExpiryScheduler テスト")
class OfferExpirySchedulerTest {

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private WaitlistPromotionService waitlistPromotionService;
    @Mock
    private LineNotificationService lineNotificationService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private OfferExpiryScheduler scheduler;

    @Nested
    @DisplayName("統合通知テスト")
    class ConsolidatedNotificationTests {

        @Test
        @DisplayName("同一セッション×同一プレイヤーで3試合期限切れ → sendConsolidatedWaitlistOfferNotificationが1回だけ呼ばれる")
        void sameSessionSamePlayer_threeMatches_oneConsolidatedNotification() {
            // 同一セッション（100）×同一プレイヤー（10）の3試合が期限切れ
            PracticeParticipant expired1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();
            PracticeParticipant expired2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(2)
                    .status(ParticipantStatus.OFFERED).build();
            PracticeParticipant expired3 = PracticeParticipant.builder()
                    .id(3L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).build();

            // 繰り上げ先はすべて同一プレイヤー（20）の同一セッション（100）
            PracticeParticipant promoted1 = PracticeParticipant.builder()
                    .id(11L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();
            PracticeParticipant promoted2 = PracticeParticipant.builder()
                    .id(12L).sessionId(100L).playerId(20L).matchNumber(2)
                    .status(ParticipantStatus.OFFERED).build();
            PracticeParticipant promoted3 = PracticeParticipant.builder()
                    .id(13L).sessionId(100L).playerId(20L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED).build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findExpiredOffers(any()))
                    .thenReturn(List.of(expired1, expired2, expired3));
            when(practiceParticipantRepository.findExpiringOffers(any(), any()))
                    .thenReturn(List.of());

            when(waitlistPromotionService.expireOfferSuppressed(expired1))
                    .thenReturn(ExpireOfferResult.builder()
                            .notificationData(AdminWaitlistNotificationData.builder()
                                    .triggerAction("オファー期限切れ").triggerPlayerId(10L)
                                    .sessionId(100L).matchNumber(1).promotedParticipant(promoted1).build())
                            .promotedParticipant(promoted1).build());
            when(waitlistPromotionService.expireOfferSuppressed(expired2))
                    .thenReturn(ExpireOfferResult.builder()
                            .notificationData(AdminWaitlistNotificationData.builder()
                                    .triggerAction("オファー期限切れ").triggerPlayerId(10L)
                                    .sessionId(100L).matchNumber(2).promotedParticipant(promoted2).build())
                            .promotedParticipant(promoted2).build());
            when(waitlistPromotionService.expireOfferSuppressed(expired3))
                    .thenReturn(ExpireOfferResult.builder()
                            .notificationData(AdminWaitlistNotificationData.builder()
                                    .triggerAction("オファー期限切れ").triggerPlayerId(10L)
                                    .sessionId(100L).matchNumber(3).promotedParticipant(promoted3).build())
                            .promotedParticipant(promoted3).build());

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

            scheduler.checkExpiredOffers();

            // 同一セッション×同一プレイヤーなので統合LINE通知は1回だけ
            verify(lineNotificationService, times(1)).sendConsolidatedWaitlistOfferNotification(
                    argThat(list -> list.size() == 3), eq(session), eq("オファー期限切れ"), eq(10L));
        }

        @Test
        @DisplayName("異なるセッションの期限切れ → セッションごとにsendConsolidatedWaitlistOfferNotificationが呼ばれる")
        void differentSessions_separateConsolidatedNotifications() {
            // セッション100の期限切れ
            PracticeParticipant expired1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();
            // セッション200の期限切れ
            PracticeParticipant expired2 = PracticeParticipant.builder()
                    .id(2L).sessionId(200L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            PracticeParticipant promoted1 = PracticeParticipant.builder()
                    .id(11L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();
            PracticeParticipant promoted2 = PracticeParticipant.builder()
                    .id(12L).sessionId(200L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            PracticeSession session100 = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();
            PracticeSession session200 = PracticeSession.builder().id(200L)
                    .sessionDate(LocalDate.of(2026, 5, 17)).build();

            when(practiceParticipantRepository.findExpiredOffers(any()))
                    .thenReturn(List.of(expired1, expired2));
            when(practiceParticipantRepository.findExpiringOffers(any(), any()))
                    .thenReturn(List.of());

            when(waitlistPromotionService.expireOfferSuppressed(expired1))
                    .thenReturn(ExpireOfferResult.builder()
                            .notificationData(AdminWaitlistNotificationData.builder()
                                    .triggerAction("オファー期限切れ").triggerPlayerId(10L)
                                    .sessionId(100L).matchNumber(1).promotedParticipant(promoted1).build())
                            .promotedParticipant(promoted1).build());
            when(waitlistPromotionService.expireOfferSuppressed(expired2))
                    .thenReturn(ExpireOfferResult.builder()
                            .notificationData(AdminWaitlistNotificationData.builder()
                                    .triggerAction("オファー期限切れ").triggerPlayerId(10L)
                                    .sessionId(200L).matchNumber(1).promotedParticipant(promoted2).build())
                            .promotedParticipant(promoted2).build());

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session100));
            when(practiceSessionRepository.findById(200L)).thenReturn(Optional.of(session200));

            scheduler.checkExpiredOffers();

            // セッション100への統合通知
            verify(lineNotificationService).sendConsolidatedWaitlistOfferNotification(
                    argThat(list -> list.size() == 1 && list.get(0).getSessionId() == 100L),
                    eq(session100), eq("オファー期限切れ"), eq(10L));
            // セッション200への統合通知
            verify(lineNotificationService).sendConsolidatedWaitlistOfferNotification(
                    argThat(list -> list.size() == 1 && list.get(0).getSessionId() == 200L),
                    eq(session200), eq("オファー期限切れ"), eq(10L));
            // 合計2回
            verify(lineNotificationService, times(2)).sendConsolidatedWaitlistOfferNotification(
                    anyList(), any(PracticeSession.class), anyString(), anyLong());
        }

        @Test
        @DisplayName("期限切れオファーがない場合、通知は送信されない")
        void noExpiredOffers_noNotifications() {
            when(practiceParticipantRepository.findExpiredOffers(any()))
                    .thenReturn(List.of());
            when(practiceParticipantRepository.findExpiringOffers(any(), any()))
                    .thenReturn(List.of());

            scheduler.checkExpiredOffers();

            verify(waitlistPromotionService, never()).expireOfferSuppressed(any());
            verify(lineNotificationService, never()).sendConsolidatedWaitlistOfferNotification(
                    anyList(), any(PracticeSession.class), anyString(), anyLong());
        }

        @Test
        @DisplayName("繰り上げ対象がいない場合、統合LINE通知は送信されない")
        void noPromoted_noConsolidatedNotification() {
            PracticeParticipant expired1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            PracticeSession session = PracticeSession.builder().id(100L)
                    .sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(practiceParticipantRepository.findExpiredOffers(any()))
                    .thenReturn(List.of(expired1));
            when(practiceParticipantRepository.findExpiringOffers(any(), any()))
                    .thenReturn(List.of());

            when(waitlistPromotionService.expireOfferSuppressed(expired1))
                    .thenReturn(ExpireOfferResult.builder()
                            .notificationData(AdminWaitlistNotificationData.builder()
                                    .triggerAction("オファー期限切れ").triggerPlayerId(10L)
                                    .sessionId(100L).matchNumber(1).promotedParticipant(null).build())
                            .promotedParticipant(null).build());

            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

            scheduler.checkExpiredOffers();

            // 繰り上げ対象なしなので統合LINE通知は送信されない
            verify(lineNotificationService, never()).sendConsolidatedWaitlistOfferNotification(
                    anyList(), any(PracticeSession.class), anyString(), anyLong());
            // 管理者通知はバッチ送信される
            verify(waitlistPromotionService).sendBatchedAdminWaitlistNotifications(anyList(), eq(session));
        }

        @Test
        @DisplayName("expireOfferSuppressedがnullを返した場合（OFFERED以外）、結果リストに含まれない")
        void expireOfferSuppressedReturnsNull_skippedInResults() {
            PracticeParticipant expired1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            when(practiceParticipantRepository.findExpiredOffers(any()))
                    .thenReturn(List.of(expired1));
            when(practiceParticipantRepository.findExpiringOffers(any(), any()))
                    .thenReturn(List.of());

            when(waitlistPromotionService.expireOfferSuppressed(expired1)).thenReturn(null);

            scheduler.checkExpiredOffers();

            verify(lineNotificationService, never()).sendConsolidatedWaitlistOfferNotification(
                    anyList(), any(PracticeSession.class), anyString(), anyLong());
            verify(waitlistPromotionService, never()).sendBatchedAdminWaitlistNotifications(
                    anyList(), any(PracticeSession.class));
        }
    }
}
