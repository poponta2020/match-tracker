package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineChatReservation;
import com.karuta.matchtracker.entity.LineChatReservation.ReservationStatus;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.repository.LineChatReservationRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LineChatReservationService の単体テスト（AC-1 の生成・AC-5 の変更検知/取消・再作成）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LineChatReservationService")
class LineChatReservationServiceTest {

    @Mock private LineChatReservationRepository reservationRepository;
    @Mock private LineBroadcastGroupRepository groupRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private CardDivisionTextService cardDivisionTextService;
    @Mock private CardDivisionScheduleResolver scheduleResolver;
    @Mock private LineNotificationService lineNotificationService;

    private LineChatReservationService service() {
        return new LineChatReservationService(reservationRepository, groupRepository,
                practiceSessionRepository, cardDivisionTextService, scheduleResolver, lineNotificationService);
    }

    private static final Long ORG = 2L;
    private static final Long GROUP_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 17);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 7, 18);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 20, 0);
    private static final LocalDateTime SEND_AT = LocalDateTime.of(2026, 7, 18, 8, 0);

    private LineBroadcastGroup group(Long org) {
        return LineBroadcastGroup.builder().id(GROUP_ID).organizationId(org).name("g").enabled(true).build();
    }

    private PracticeSession session(Long id, Long org, LocalDate date) {
        return PracticeSession.builder().id(id).sessionDate(date).organizationId(org).totalMatches(3).build();
    }

    private LineChatReservation reservation(Long session, ReservationStatus status, String text, LocalDateTime sendAt) {
        return LineChatReservation.builder()
                .id(session).broadcastGroupId(GROUP_ID).sessionId(session)
                .status(status).messageText(text).scheduledSendAt(sendAt).attemptCount(0).build();
    }

    // ===== generateReservations（AC-1）=====

    @Test
    @DisplayName("有効グループ×当該団体セッションに PENDING を生成する（本文・送信時刻つき）")
    void generatesForMatchingSession() {
        PracticeSession s = session(100L, ORG, TOMORROW);
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group(ORG)));
        when(practiceSessionRepository.findAllBySessionDate(TOMORROW)).thenReturn(List.of(s));
        when(cardDivisionTextService.buildTextForSession(s)).thenReturn("札分けテキスト");
        when(scheduleResolver.resolveScheduledSendAt(s)).thenReturn(SEND_AT);
        when(reservationRepository.tryInsertPendingReservation(GROUP_ID, 100L, "札分けテキスト", SEND_AT, NOW))
                .thenReturn(1);

        int created = service().generateReservations(TOMORROW, NOW);

        assertThat(created).isEqualTo(1);
        verify(reservationRepository).tryInsertPendingReservation(GROUP_ID, 100L, "札分けテキスト", SEND_AT, NOW);
    }

    @Test
    @DisplayName("他団体のセッションには予約を作らない（団体分離）")
    void skipsOtherOrgSession() {
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group(ORG)));
        when(practiceSessionRepository.findAllBySessionDate(TOMORROW))
                .thenReturn(List.of(session(101L, 999L, TOMORROW)));

        int created = service().generateReservations(TOMORROW, NOW);

        assertThat(created).isZero();
        verify(reservationRepository, never()).tryInsertPendingReservation(any(), any(), any(), any(), any());
    }

    // ===== promoteStaleReserving =====

    @Test
    @DisplayName("RESERVING 滞留 → MANUAL_REVIEW_REQUIRED へ昇格しアラートする")
    void promotesStaleReserving() {
        LineChatReservation stale = reservation(100L, ReservationStatus.RESERVING, "t", SEND_AT);
        when(reservationRepository.findByStatusAndUpdatedAtBefore(eq(ReservationStatus.RESERVING), any()))
                .thenReturn(List.of(stale));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));

        service().promoteStaleReserving(NOW);

        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.MANUAL_REVIEW_REQUIRED);
        assertThat(stale.getErrorCode()).isEqualTo("RESERVING_TIMEOUT");
        verify(reservationRepository).save(stale);
        verify(lineNotificationService).sendChatReserveAlert(eq(ORG), any());
    }

    // ===== detectChanges（AC-5）=====

    @Test
    @DisplayName("セッション削除 × RESERVED → CANCEL_PENDING（ワーカーがLINE側予約を削除）")
    void sessionRemovedReservedBecomesCancelPending() {
        LineChatReservation r = reservation(100L, ReservationStatus.RESERVED, "t", SEND_AT);
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(r));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));

        service().detectChanges(NOW);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
        assertThat(r.getErrorCode()).isEqualTo("SESSION_REMOVED");
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("セッション削除 × PENDING → CANCELLED（LINE側に何も無い）")
    void sessionRemovedPendingBecomesCancelled() {
        LineChatReservation r = reservation(100L, ReservationStatus.PENDING, "t", SEND_AT);
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(r));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.empty());
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));

        service().detectChanges(NOW);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("グループ無効化 × RESERVED → CANCEL_PENDING（無効化後もLINE予約が残るため取消）")
    void disabledGroupReservedBecomesCancelPending() {
        LineChatReservation r = reservation(100L, ReservationStatus.RESERVED, "t", SEND_AT);
        PracticeSession s = session(100L, ORG, TOMORROW);
        LineBroadcastGroup disabled = LineBroadcastGroup.builder()
                .id(GROUP_ID).organizationId(ORG).name("g").enabled(false).build();
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(r));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(s));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(disabled));

        service().detectChanges(NOW);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
        assertThat(r.getErrorCode()).isEqualTo("GROUP_INACTIVE");
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("グループ無効化 × PENDING → CANCELLED（LINE側に無いので直接取消）")
    void disabledGroupPendingBecomesCancelled() {
        LineChatReservation r = reservation(100L, ReservationStatus.PENDING, "t", SEND_AT);
        PracticeSession s = session(100L, ORG, TOMORROW);
        LineBroadcastGroup disabled = LineBroadcastGroup.builder()
                .id(GROUP_ID).organizationId(ORG).name("g").enabled(false).build();
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(r));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(s));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(disabled));

        service().detectChanges(NOW);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(r.getErrorCode()).isEqualTo("GROUP_INACTIVE");
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("内容変更 × RESERVED → CANCEL_PENDING（取消→再予約へ正規化）")
    void contentChangedReservedBecomesCancelPending() {
        LineChatReservation r = reservation(100L, ReservationStatus.RESERVED, "旧本文", SEND_AT);
        PracticeSession s = session(100L, ORG, TOMORROW);
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(r));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(s));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));
        when(cardDivisionTextService.buildTextForSession(s)).thenReturn("新本文");
        when(scheduleResolver.resolveScheduledSendAt(s)).thenReturn(SEND_AT);

        service().detectChanges(NOW);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCEL_PENDING);
        assertThat(r.getErrorCode()).isEqualTo("CONTENT_CHANGED");
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("内容変更 × PENDING → その場で本文/時刻を更新（LINE側未登録のため取消不要）")
    void contentChangedPendingUpdatedInPlace() {
        LineChatReservation r = reservation(100L, ReservationStatus.PENDING, "旧本文",
                LocalDateTime.of(2026, 7, 18, 8, 0));
        PracticeSession s = session(100L, ORG, TOMORROW);
        LocalDateTime newSendAt = LocalDateTime.of(2026, 7, 18, 9, 30);
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(r));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(s));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));
        when(cardDivisionTextService.buildTextForSession(s)).thenReturn("新本文");
        when(scheduleResolver.resolveScheduledSendAt(s)).thenReturn(newSendAt);

        service().detectChanges(NOW);

        assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(r.getMessageText()).isEqualTo("新本文");
        assertThat(r.getScheduledSendAt()).isEqualTo(newSendAt);
        verify(reservationRepository).save(r);
    }

    @Test
    @DisplayName("内容一致 → 何もしない（thrash防止）")
    void noChangeNoSave() {
        LineChatReservation r = reservation(100L, ReservationStatus.RESERVED, "同一本文", SEND_AT);
        PracticeSession s = session(100L, ORG, TOMORROW);
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(r));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(s));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));
        when(cardDivisionTextService.buildTextForSession(s)).thenReturn("同一本文");
        when(scheduleResolver.resolveScheduledSendAt(s)).thenReturn(SEND_AT);

        service().detectChanges(NOW);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("MANUAL_REVIEW_REQUIRED / RESERVING / CANCEL_PENDING は自動で触らない")
    void leavesManualAndInFlightAlone() {
        LineChatReservation manual = reservation(100L, ReservationStatus.MANUAL_REVIEW_REQUIRED, "t", SEND_AT);
        LineChatReservation reserving = reservation(101L, ReservationStatus.RESERVING, "t", SEND_AT);
        LineChatReservation cancelPending = reservation(102L, ReservationStatus.CANCEL_PENDING, "t", SEND_AT);
        when(reservationRepository.findByStatusNotAndScheduledSendAtAfter(eq(ReservationStatus.CANCELLED), any()))
                .thenReturn(List.of(manual, reserving, cancelPending));

        service().detectChanges(NOW);

        verify(reservationRepository, never()).save(any());
        verify(practiceSessionRepository, never()).findById(anyLong());
    }

    // ===== recreateAfterCancellation =====

    @Test
    @DisplayName("取消済み×active無し×余裕あり → 再作成する")
    void recreatesAfterCancellation() {
        PracticeSession s = session(100L, ORG, TODAY);
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group(ORG)));
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(s));
        when(practiceSessionRepository.findAllBySessionDate(TOMORROW)).thenReturn(List.of());
        when(reservationRepository.findFirstByBroadcastGroupIdAndSessionIdAndStatusNot(
                GROUP_ID, 100L, ReservationStatus.CANCELLED)).thenReturn(Optional.empty());
        when(reservationRepository.existsByBroadcastGroupIdAndSessionIdAndStatus(
                GROUP_ID, 100L, ReservationStatus.CANCELLED)).thenReturn(true);
        LocalDateTime sendAt = LocalDateTime.of(2026, 7, 17, 23, 0); // now(20:00)+30min 余裕あり
        when(scheduleResolver.resolveScheduledSendAt(s)).thenReturn(sendAt);
        when(cardDivisionTextService.buildTextForSession(s)).thenReturn("再作成本文");

        service().recreateAfterCancellation(TODAY, NOW);

        verify(reservationRepository).tryInsertPendingReservation(GROUP_ID, 100L, "再作成本文", sendAt, NOW);
    }

    @Test
    @DisplayName("取消履歴が無い（未作成）→ 再作成しない（フォールバックpushに委ねる）")
    void doesNotRecreateNeverCreated() {
        PracticeSession s = session(100L, ORG, TODAY);
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group(ORG)));
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(s));
        when(practiceSessionRepository.findAllBySessionDate(TOMORROW)).thenReturn(List.of());
        when(reservationRepository.findFirstByBroadcastGroupIdAndSessionIdAndStatusNot(
                GROUP_ID, 100L, ReservationStatus.CANCELLED)).thenReturn(Optional.empty());
        when(reservationRepository.existsByBroadcastGroupIdAndSessionIdAndStatus(
                GROUP_ID, 100L, ReservationStatus.CANCELLED)).thenReturn(false);

        service().recreateAfterCancellation(TODAY, NOW);

        verify(reservationRepository, never()).tryInsertPendingReservation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("active 行があれば再作成しない")
    void doesNotRecreateWhenActiveExists() {
        PracticeSession s = session(100L, ORG, TODAY);
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group(ORG)));
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(s));
        when(practiceSessionRepository.findAllBySessionDate(TOMORROW)).thenReturn(List.of());
        when(reservationRepository.findFirstByBroadcastGroupIdAndSessionIdAndStatusNot(
                GROUP_ID, 100L, ReservationStatus.CANCELLED))
                .thenReturn(Optional.of(reservation(100L, ReservationStatus.PENDING, "t", SEND_AT)));

        service().recreateAfterCancellation(TODAY, NOW);

        verify(reservationRepository, never()).tryInsertPendingReservation(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("送信まで余裕が無い（マージン不足）→ 再作成しない")
    void doesNotRecreateWithoutMargin() {
        PracticeSession s = session(100L, ORG, TODAY);
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(group(ORG)));
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(s));
        when(practiceSessionRepository.findAllBySessionDate(TOMORROW)).thenReturn(List.of());
        when(reservationRepository.findFirstByBroadcastGroupIdAndSessionIdAndStatusNot(
                GROUP_ID, 100L, ReservationStatus.CANCELLED)).thenReturn(Optional.empty());
        when(reservationRepository.existsByBroadcastGroupIdAndSessionIdAndStatus(
                GROUP_ID, 100L, ReservationStatus.CANCELLED)).thenReturn(true);
        // now=20:00, sendAt=20:20 → sendAt-30min=19:50 < now → 余裕なし
        when(scheduleResolver.resolveScheduledSendAt(s)).thenReturn(LocalDateTime.of(2026, 7, 17, 20, 20));

        service().recreateAfterCancellation(TODAY, NOW);

        verify(reservationRepository, never()).tryInsertPendingReservation(any(), any(), any(), any(), any());
    }

    // ===== applyWorkerResult / 遷移検証（AC-3）=====

    @Test
    @DisplayName("PENDING→RESERVING は試行回数を加算して更新する")
    void applyResultPendingToReserving() {
        LineChatReservation r = reservation(100L, ReservationStatus.PENDING, "t", SEND_AT);
        r.setAttemptCount(0);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LineChatReservation out = service().applyWorkerResult(100L, ReservationStatus.RESERVING, null, null);

        assertThat(out.getStatus()).isEqualTo(ReservationStatus.RESERVING);
        assertThat(out.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("RESERVING→RESERVED は成功として更新（error はクリア）")
    void applyResultReservingToReserved() {
        LineChatReservation r = reservation(100L, ReservationStatus.RESERVING, "t", SEND_AT);
        r.setErrorCode("PREV");
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LineChatReservation out = service().applyWorkerResult(100L, ReservationStatus.RESERVED, null, null);

        assertThat(out.getStatus()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(out.getErrorCode()).isNull();
        // 成功はアラートしない
        verify(lineNotificationService, never()).sendChatReserveAlert(anyLong(), any());
    }

    @Test
    @DisplayName("applyWorkerResult: FAILED/MANUAL_REVIEW_REQUIRED で管理者アラートを発火する（AC-10）")
    void applyResultFailureFiresAlert() {
        LineChatReservation failing = reservation(100L, ReservationStatus.RESERVING, "t", SEND_AT);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(failing));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group(ORG)));

        service().applyWorkerResult(100L, ReservationStatus.FAILED, "LINE_AUTH_EXPIRED", "auth");

        verify(lineNotificationService).sendChatReserveAlert(eq(ORG), any());

        // MANUAL_REVIEW_REQUIRED も同様（別の予約で確認）
        LineChatReservation review = reservation(101L, ReservationStatus.RESERVING, "t", SEND_AT);
        when(reservationRepository.findById(101L)).thenReturn(Optional.of(review));
        service().applyWorkerResult(101L, ReservationStatus.MANUAL_REVIEW_REQUIRED, "CONFIRM_RESULT_UNKNOWN", null);
        verify(lineNotificationService, times(2)).sendChatReserveAlert(eq(ORG), any());
    }

    @Test
    @DisplayName("CANCEL_PENDING→CANCELLED は許可")
    void applyResultCancelPendingToCancelled() {
        LineChatReservation r = reservation(100L, ReservationStatus.CANCEL_PENDING, "t", SEND_AT);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LineChatReservation out = service().applyWorkerResult(100L, ReservationStatus.CANCELLED, null, null);

        assertThat(out.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    @DisplayName("不正遷移（RESERVED→RESERVING）は 409 で拒否し保存しない")
    void applyResultInvalidTransitionConflict() {
        LineChatReservation r = reservation(100L, ReservationStatus.RESERVED, "t", SEND_AT);
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() ->
                service().applyWorkerResult(100L, ReservationStatus.RESERVING, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("存在しない予約への報告は 404")
    void applyResultNotFound() {
        when(reservationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service().applyWorkerResult(999L, ReservationStatus.RESERVING, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===== getWorkerTasks のマージン絞り込み / expireStalePending =====

    @Test
    @DisplayName("getWorkerTasks: PENDINGは送信予定まで余裕(30分)がある行だけ、CANCEL_PENDINGは常に渡す")
    void getWorkerTasksFiltersPendingByMargin() {
        // NOW=2026-07-17 20:00
        LineChatReservation future = reservation(1L, ReservationStatus.PENDING, "t",
                LocalDateTime.of(2026, 7, 17, 23, 0)); // sendAt-30=22:30 > now → 渡す
        LineChatReservation soon = reservation(2L, ReservationStatus.PENDING, "t",
                LocalDateTime.of(2026, 7, 17, 20, 20)); // sendAt-30=19:50 < now → 除外
        LineChatReservation cancel = reservation(3L, ReservationStatus.CANCEL_PENDING, "t",
                LocalDateTime.of(2026, 7, 17, 19, 0)); // 過去でも渡す
        when(reservationRepository.findByStatusInOrderByScheduledSendAtAsc(any()))
                .thenReturn(List.of(cancel, soon, future));

        List<LineChatReservation> tasks = service().getWorkerTasks(NOW);

        assertThat(tasks).extracting(LineChatReservation::getSessionId).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    @DisplayName("expireStalePending: 送信予定まで余裕を切ったPENDINGを FAILED(PENDING_EXPIRED) に落とす")
    void expireStalePendingMarksFailed() {
        LineChatReservation stale = reservation(1L, ReservationStatus.PENDING, "t",
                LocalDateTime.of(2026, 7, 17, 20, 20));
        when(reservationRepository.findByStatusAndScheduledSendAtBefore(eq(ReservationStatus.PENDING), any()))
                .thenReturn(List.of(stale));

        service().expireStalePending(NOW);

        assertThat(stale.getStatus()).isEqualTo(ReservationStatus.FAILED);
        assertThat(stale.getErrorCode()).isEqualTo("PENDING_EXPIRED");
        verify(reservationRepository).save(stale);
    }

    // ===== warnSessionExpiring（AC-5・タスク2）=====

    @Test
    @DisplayName("warnSessionExpiring: 有効グループの各団体（重複排除）へSSO失効警告を送る")
    void warnSessionExpiringAlertsDistinctOrgs() {
        LineBroadcastGroup g1 = LineBroadcastGroup.builder().id(1L).organizationId(ORG).name("a").enabled(true).build();
        LineBroadcastGroup g2 = LineBroadcastGroup.builder().id(2L).organizationId(ORG).name("b").enabled(true).build();
        LineBroadcastGroup g3 = LineBroadcastGroup.builder().id(3L).organizationId(5L).name("c").enabled(true).build();
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of(g1, g2, g3));

        service().warnSessionExpiring(3);

        // org=ORG は2グループあるが distinct で1回だけ、org=5 も1回。文言に残り日数を含む。
        verify(lineNotificationService, times(1)).sendChatReserveAlert(eq(ORG), contains("3日"));
        verify(lineNotificationService, times(1)).sendChatReserveAlert(eq(5L), contains("3日"));
        verify(lineNotificationService, times(2)).sendChatReserveAlert(anyLong(), any());
    }

    @Test
    @DisplayName("warnSessionExpiring: 有効グループが0件なら通知しない")
    void warnSessionExpiringNoGroupsNoop() {
        when(groupRepository.findByEnabledTrue()).thenReturn(List.of());

        service().warnSessionExpiring(2);

        verify(lineNotificationService, never()).sendChatReserveAlert(anyLong(), any());
    }

    @Test
    @DisplayName("warnSessionExpiring: 残り0日以下は『まもなく失効』文言で送る")
    void warnSessionExpiringImminentWording() {
        when(groupRepository.findByEnabledTrue()).thenReturn(
                List.of(LineBroadcastGroup.builder().id(1L).organizationId(ORG).name("a").enabled(true).build()));

        service().warnSessionExpiring(0);

        verify(lineNotificationService).sendChatReserveAlert(eq(ORG), contains("まもなく失効"));
    }

    @Test
    @DisplayName("isValidWorkerTransition: 代表的な許可/不許可")
    void transitionMatrix() {
        assertThat(LineChatReservationService.isValidWorkerTransition(
                ReservationStatus.PENDING, ReservationStatus.RESERVING)).isTrue();
        assertThat(LineChatReservationService.isValidWorkerTransition(
                ReservationStatus.RESERVING, ReservationStatus.MANUAL_REVIEW_REQUIRED)).isTrue();
        assertThat(LineChatReservationService.isValidWorkerTransition(
                ReservationStatus.RESERVING, ReservationStatus.DRY_RUN_SUCCEEDED)).isTrue();
        assertThat(LineChatReservationService.isValidWorkerTransition(
                ReservationStatus.PENDING, ReservationStatus.RESERVED)).isFalse();
        assertThat(LineChatReservationService.isValidWorkerTransition(
                ReservationStatus.RESERVED, ReservationStatus.RESERVING)).isFalse();
        assertThat(LineChatReservationService.isValidWorkerTransition(
                ReservationStatus.CANCELLED, ReservationStatus.PENDING)).isFalse();
    }
}
