package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.dto.CancelRequest;
import com.karuta.matchtracker.dto.SameDayCancelContext;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeSession;
import org.springframework.transaction.annotation.Transactional;
import java.lang.reflect.Method;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryController cancel tests")
class LotteryControllerCancelTest {

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
    @DisplayName("一括キャンセル途中で例外が起きても成功分の通知集約処理が実行される")
    void cancelParticipation_partialFailure_stillDispatchesSuccessfulCancellations() {
        // SUPER_ADMIN で呼ぶ（PLAYER のセルフチェックをスキップ）
        when(request.getAttribute("currentUserId")).thenReturn(1L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

        // 1件目: 当日キャンセル扱いで SameDayCancelContext 付きデータを返す
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 15)).capacity(6).build();
        SameDayCancelContext ctx = SameDayCancelContext.builder()
                .session(session).playerId(10L).playerName("テスト選手").matchNumber(1).build();
        AdminWaitlistNotificationData firstSuccess = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル（当日補充）")
                .triggerPlayerId(10L)
                .sessionId(100L)
                .matchNumber(1)
                .sameDayCancelContext(ctx)
                .build();

        // 2件目で RuntimeException を発生させる（DB 例外等を想定）
        when(waitlistPromotionService.cancelParticipationSuppressed(101L, "HEALTH", null))
                .thenReturn(firstSuccess);
        when(waitlistPromotionService.cancelParticipationSuppressed(102L, "HEALTH", null))
                .thenThrow(new RuntimeException("DB error"));

        // 通知集約処理: normal 側（繰り上げ通知）は空リストを返す
        // 当日キャンセル分は副作用として afterCommit 登録されるだけ
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList()))
                .thenReturn(List.of());

        CancelRequest req = CancelRequest.builder()
                .participantIds(List.of(101L, 102L))
                .cancelReason("HEALTH")
                .build();

        // 例外は再 throw されることを確認
        assertThatThrownBy(() -> controller.cancelParticipation(req, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        // 例外が起きた後でも finally 節で dispatchSameDayCancelNotifications が呼ばれる
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminWaitlistNotificationData>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(waitlistPromotionService).dispatchSameDayCancelNotifications(captor.capture());

        // 成功済みの 1 件だけが集約処理に渡されていることを確認
        List<AdminWaitlistNotificationData> captured = captor.getValue();
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).getSessionId()).isEqualTo(100L);
        assertThat(captured.get(0).getMatchNumber()).isEqualTo(1);
        assertThat(captured.get(0).getSameDayCancelContext()).isNotNull();
    }

    @Test
    @DisplayName("全件成功時は通知集約処理が一度だけ呼ばれる")
    void cancelParticipation_allSuccess_dispatchesOnce() {
        when(request.getAttribute("currentUserId")).thenReturn(1L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 15)).capacity(6).build();
        SameDayCancelContext ctx1 = SameDayCancelContext.builder()
                .session(session).playerId(10L).playerName("選手A").matchNumber(1).build();
        SameDayCancelContext ctx2 = SameDayCancelContext.builder()
                .session(session).playerId(10L).playerName("選手A").matchNumber(2).build();
        AdminWaitlistNotificationData d1 = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル（当日補充）").triggerPlayerId(10L)
                .sessionId(100L).matchNumber(1).sameDayCancelContext(ctx1).build();
        AdminWaitlistNotificationData d2 = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル（当日補充）").triggerPlayerId(10L)
                .sessionId(100L).matchNumber(2).sameDayCancelContext(ctx2).build();

        when(waitlistPromotionService.cancelParticipationSuppressed(101L, "HEALTH", null))
                .thenReturn(d1);
        when(waitlistPromotionService.cancelParticipationSuppressed(102L, "HEALTH", null))
                .thenReturn(d2);
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList()))
                .thenReturn(List.of());

        CancelRequest req = CancelRequest.builder()
                .participantIds(List.of(101L, 102L))
                .cancelReason("HEALTH")
                .build();

        controller.cancelParticipation(req, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminWaitlistNotificationData>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(waitlistPromotionService).dispatchSameDayCancelNotifications(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("3件中2件目で例外でも、1件目と3件目相当の通知集約は finally で実行される")
    void cancelParticipation_threeItemsMiddleFailure_dispatchesSuccessful() {
        when(request.getAttribute("currentUserId")).thenReturn(1L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 4, 15)).capacity(6).build();
        SameDayCancelContext ctx1 = SameDayCancelContext.builder()
                .session(session).playerId(10L).playerName("選手A").matchNumber(1).build();
        AdminWaitlistNotificationData d1 = AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル（当日補充）").triggerPlayerId(10L)
                .sessionId(100L).matchNumber(1).sameDayCancelContext(ctx1).build();

        // 1件目成功 → 2件目で例外 → 3件目はそもそも呼ばれない（ループが中断するため）
        // 個別TX契約が守られていれば 1件目の DB 更新は確定する。
        // 通知集約処理は finally で必ず実行され、成功分のみが渡る。
        when(waitlistPromotionService.cancelParticipationSuppressed(101L, "HEALTH", null))
                .thenReturn(d1);
        when(waitlistPromotionService.cancelParticipationSuppressed(102L, "HEALTH", null))
                .thenThrow(new RuntimeException("DB error"));
        when(waitlistPromotionService.dispatchSameDayCancelNotifications(anyList()))
                .thenReturn(List.of());

        CancelRequest req = CancelRequest.builder()
                .participantIds(List.of(101L, 102L, 103L))
                .cancelReason("HEALTH")
                .build();

        assertThatThrownBy(() -> controller.cancelParticipation(req, request))
                .isInstanceOf(RuntimeException.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminWaitlistNotificationData>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(waitlistPromotionService).dispatchSameDayCancelNotifications(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getMatchNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelParticipation には @Transactional を付けてはならない（個別TX契約のセンチネル）")
    void cancelParticipation_mustNotHaveTransactionalAnnotation() throws NoSuchMethodException {
        // 個別TXコミットの契約は、本メソッドに @Transactional が付与されていないことに依存する。
        // 上流TXが存在すると cancelParticipationSuppressed が REQUIRED で参加し、ループ全件が
        // 単一TXに化けて途中の例外で全件ロールバックされる（成功分のキャンセルが消える）。
        // この sentinel テストにより、誤って @Transactional を付けた変更が CI で即検出される。
        Method method = LotteryController.class.getMethod(
                "cancelParticipation", CancelRequest.class,
                jakarta.servlet.http.HttpServletRequest.class);
        assertThat(method.isAnnotationPresent(Transactional.class))
                .as("LotteryController#cancelParticipation must NOT be @Transactional " +
                        "(see WaitlistPromotionService#cancelParticipationSuppressed Javadoc)")
                .isFalse();
        assertThat(LotteryController.class.isAnnotationPresent(Transactional.class))
                .as("LotteryController class must NOT be @Transactional")
                .isFalse();
    }
}
