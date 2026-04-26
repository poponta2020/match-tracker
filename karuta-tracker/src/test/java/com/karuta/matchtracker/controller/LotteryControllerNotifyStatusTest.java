package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.LotteryDeadlineHelper;
import com.karuta.matchtracker.service.LotteryService;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryController notify-status 重複送信ガードテスト")
class LotteryControllerNotifyStatusTest {

    @Mock private LotteryService lotteryService;
    @Mock private WaitlistPromotionService waitlistPromotionService;
    @Mock private LineNotificationService lineNotificationService;
    @Mock private NotificationService notificationService;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;

    @InjectMocks
    private LotteryController controller;

    @Mock
    private HttpServletRequest request;

    @Test
    @DisplayName("対象月・団体に通知済みレコードがある場合 sent=true・正しい sentCount が返る")
    void notifyStatus_withSentNotifications_returnsSentTrue() {
        // SUPER_ADMIN（org強制なし）
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

        PracticeSession s1 = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 20))
                .organizationId(1L).build();
        PracticeSession s2 = PracticeSession.builder()
                .id(200L).sessionDate(LocalDate.of(2026, 4, 27))
                .organizationId(1L).build();

        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 4, 1L))
                .thenReturn(List.of(s1, s2));
        // sessionIds でガード照会 → 3件HIT（WAITLISTED 1件・ALL_WON 1件・REMAINING_WON 1件）
        when(notificationRepository.countByReferenceIdInAndTypeIn(
                eq(List.of(100L, 200L)),
                anyList()))
                .thenReturn(3L);

        ResponseEntity<Map<String, Object>> response =
                controller.getNotifyStatus(2026, 4, 1L, request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .containsEntry("sent", true)
                .containsEntry("sentCount", 3L);

        // sessionIds で照会していること & 渡している通知種別が3種類であることを検証
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationType>> typesCaptor =
                ArgumentCaptor.forClass((Class) List.class);
        verify(notificationRepository).countByReferenceIdInAndTypeIn(
                eq(List.of(100L, 200L)), typesCaptor.capture());
        assertThat(typesCaptor.getValue())
                .containsExactlyInAnyOrder(
                        NotificationType.LOTTERY_WAITLISTED,
                        NotificationType.LOTTERY_ALL_WON,
                        NotificationType.LOTTERY_REMAINING_WON);
    }

    @Test
    @DisplayName("通知未送信の場合 sent=false・sentCount=0")
    void notifyStatus_noNotifications_returnsSentFalse() {
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

        PracticeSession s1 = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 20))
                .organizationId(1L).build();

        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 4, 1L))
                .thenReturn(List.of(s1));
        when(notificationRepository.countByReferenceIdInAndTypeIn(eq(List.of(100L)), anyList()))
                .thenReturn(0L);

        ResponseEntity<Map<String, Object>> response =
                controller.getNotifyStatus(2026, 4, 1L, request);

        assertThat(response.getBody())
                .containsEntry("sent", false)
                .containsEntry("sentCount", 0L);
    }

    @Test
    @DisplayName("対象セッションが空の場合は早期returnで notificationRepository を呼ばない")
    void notifyStatus_noSessions_returnsZeroWithoutQuery() {
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 4, 1L))
                .thenReturn(List.of());

        ResponseEntity<Map<String, Object>> response =
                controller.getNotifyStatus(2026, 4, 1L, request);

        assertThat(response.getBody())
                .containsEntry("sent", false)
                .containsEntry("sentCount", 0);
        verify(notificationRepository, never()).countByReferenceIdInAndTypeIn(anyList(), anyList());
    }

    @Test
    @DisplayName("ADMINロールはリクエストの organizationId に関わらず自団体に強制される")
    void notifyStatus_adminRole_forcesOwnOrganization() {
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(2L);

        // ADMIN なので リクエストの organizationId=99 は無視され adminOrganizationId=2 が使われる
        when(practiceSessionRepository.findByYearAndMonthAndOrganizationId(2026, 4, 2L))
                .thenReturn(List.of());

        controller.getNotifyStatus(2026, 4, 99L, request);

        // ADMIN の adminOrganizationId=2 でクエリされる
        verify(practiceSessionRepository).findByYearAndMonthAndOrganizationId(2026, 4, 2L);
        // 99L では呼ばれない
        verify(practiceSessionRepository, never())
                .findByYearAndMonthAndOrganizationId(2026, 4, 99L);
    }

    @Test
    @DisplayName("organizationId未指定の場合は団体スコープなしクエリ（findByYearAndMonth）を使用")
    void notifyStatus_noOrganizationId_usesGlobalQuery() {
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

        PracticeSession s1 = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 20))
                .organizationId(1L).build();

        when(practiceSessionRepository.findByYearAndMonth(2026, 4))
                .thenReturn(List.of(s1));
        when(notificationRepository.countByReferenceIdInAndTypeIn(eq(List.of(100L)), anyList()))
                .thenReturn(1L);

        ResponseEntity<Map<String, Object>> response =
                controller.getNotifyStatus(2026, 4, null, request);

        assertThat(response.getBody()).containsEntry("sent", true);
        verify(practiceSessionRepository).findByYearAndMonth(2026, 4);
    }
}
