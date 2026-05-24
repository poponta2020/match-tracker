package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.KaderuSyncStatusResponse;
import com.karuta.matchtracker.dto.KaderuSyncTriggerEventDto;
import com.karuta.matchtracker.entity.KaderuSyncTriggerEvent;
import com.karuta.matchtracker.entity.KaderuSyncTriggerEvent.SyncStatus;
import com.karuta.matchtracker.entity.Organization;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.KaderuSyncTriggerEventRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.service.GitHubActionsClient.WorkflowRun;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.mockito.Mockito.inOrder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KaderuSyncTriggerService の単体テスト。
 *
 * GitHub Actions / DB / LINE は全てモックしてビジネスロジックのみ検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KaderuSyncTriggerService 単体テスト")
class KaderuSyncTriggerServiceTest {

    @Mock private KaderuSyncTriggerEventRepository eventRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private GitHubActionsClient gitHubActionsClient;
    @Mock private LineNotificationService lineNotificationService;

    @InjectMocks
    private KaderuSyncTriggerService service;

    private Organization hokudai;

    @BeforeEach
    void setUp() {
        hokudai = Organization.builder().id(1L).code("hokudai").name("北大かるた会").build();
    }

    // =======================
    // triggerSync
    // =======================

    @Test
    @DisplayName("triggerSync: 通常成功で PENDING イベントを saveAndFlush → dispatch (eventId を input に含める)")
    void triggerSync_savesPendingEvent_thenDispatchesWithEventId() {
        when(eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(1L, SyncStatus.PENDING))
                .thenReturn(Optional.empty());
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(hokudai));
        when(eventRepository.saveAndFlush(any(KaderuSyncTriggerEvent.class)))
                .thenAnswer(inv -> {
                    KaderuSyncTriggerEvent e = inv.getArgument(0);
                    e.setId(100L);
                    return e;
                });

        KaderuSyncTriggerEventDto dto = service.triggerSync(7L, 1L);

        assertThat(dto.getId()).isEqualTo(100L);
        assertThat(dto.getOrganizationId()).isEqualTo(1L);
        assertThat(dto.getOrganizationCode()).isEqualTo("hokudai");
        assertThat(dto.getTriggeredByPlayerId()).isEqualTo(7L);
        assertThat(dto.getStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(dto.getGithubRunId()).isNull();

        // 保存 → dispatch の順序を厳密に検証（dispatch が先に走ったら未追跡 workflow が起動する）
        InOrder inOrder = inOrder(eventRepository, gitHubActionsClient);
        inOrder.verify(eventRepository).saveAndFlush(any(KaderuSyncTriggerEvent.class));
        ArgumentCaptor<Map<String, Object>> inputsCaptor = ArgumentCaptor.forClass(Map.class);
        inOrder.verify(gitHubActionsClient).dispatchWorkflow(
                eq("sync-kaderu-reservations-manual.yml"), eq("main"), inputsCaptor.capture());
        assertThat(inputsCaptor.getValue())
                .containsEntry("org", "hokudai")
                .containsEntry("eventId", "100"); // 相関 ID は文字列で渡す

        // triggerSync は run_id 解決を試みず scheduler に委ねる
        verify(gitHubActionsClient, never()).listRecentRuns(any(), any());
    }

    @Test
    @DisplayName("triggerSync: 同一団体の PENDING が存在すれば 409 (事前チェック) — dispatch も save も呼ばない")
    void triggerSync_throws409_whenPendingExists() {
        KaderuSyncTriggerEvent existing = KaderuSyncTriggerEvent.builder()
                .id(99L).organizationId(1L).status(SyncStatus.PENDING).build();
        when(eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(1L, SyncStatus.PENDING))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.triggerSync(7L, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("同期が既に実行中");

        verify(gitHubActionsClient, never()).dispatchWorkflow(any(), any(), any());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("triggerSync: saveAndFlush 時の DataIntegrityViolationException は 409 に変換し、dispatch は呼ばれない")
    void triggerSync_convertsConstraintViolation_to409_andDoesNotDispatch() {
        when(eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(1L, SyncStatus.PENDING))
                .thenReturn(Optional.empty());
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(hokudai));
        when(eventRepository.saveAndFlush(any(KaderuSyncTriggerEvent.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uk_kaderu_sync_pending\""));

        assertThatThrownBy(() -> service.triggerSync(7L, 1L))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("同期が既に実行中");

        // race の loser は workflow を起動してはならない
        verify(gitHubActionsClient, never()).dispatchWorkflow(any(), any(), any());
    }

    @Test
    @DisplayName("triggerSync: dispatch 失敗時は @Transactional が rollback するため例外をそのまま伝播する")
    void triggerSync_propagatesDispatchFailure() {
        when(eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(1L, SyncStatus.PENDING))
                .thenReturn(Optional.empty());
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(hokudai));
        when(eventRepository.saveAndFlush(any(KaderuSyncTriggerEvent.class)))
                .thenAnswer(inv -> {
                    KaderuSyncTriggerEvent e = inv.getArgument(0);
                    e.setId(101L);
                    return e;
                });
        org.mockito.Mockito.doThrow(new RuntimeException("GitHub Actions 5xx"))
                .when(gitHubActionsClient).dispatchWorkflow(any(), any(), any());

        assertThatThrownBy(() -> service.triggerSync(7L, 1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("GitHub Actions");
    }

    @Test
    @DisplayName("triggerSync: 団体が見つからなければ ResourceNotFoundException")
    void triggerSync_throws404_whenOrgNotFound() {
        when(eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(99L, SyncStatus.PENDING))
                .thenReturn(Optional.empty());
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.triggerSync(7L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("団体が見つかりません");

        verify(gitHubActionsClient, never()).dispatchWorkflow(any(), any(), any());
    }

    @Test
    @DisplayName("triggerSync: organizationId が null なら IllegalArgumentException")
    void triggerSync_throwsBadRequest_whenOrgIdNull() {
        assertThatThrownBy(() -> service.triggerSync(7L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("organizationId");
    }

    // =======================
    // getStatus
    // =======================

    @Test
    @DisplayName("getStatus: PENDING あり → DTO に詰めて返す (elapsedSeconds は0以上)")
    void getStatus_returnsPending() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).organizationId(1L).triggeredByPlayerId(7L)
                .triggeredAt(JstDateTimeUtil.now().minusMinutes(1))
                .status(SyncStatus.PENDING)
                .build();
        when(eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(1L, SyncStatus.PENDING))
                .thenReturn(Optional.of(event));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(hokudai));

        KaderuSyncStatusResponse res = service.getStatus(1L);

        assertThat(res.getPendingEvent()).isNotNull();
        assertThat(res.getPendingEvent().getId()).isEqualTo(100L);
        assertThat(res.getPendingEvent().getOrganizationCode()).isEqualTo("hokudai");
        assertThat(res.getPendingEvent().getElapsedSeconds()).isGreaterThanOrEqualTo(50);
    }

    @Test
    @DisplayName("getStatus: PENDING なし → pendingEvent: null")
    void getStatus_returnsNull_whenNoPending() {
        when(eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(1L, SyncStatus.PENDING))
                .thenReturn(Optional.empty());

        KaderuSyncStatusResponse res = service.getStatus(1L);

        assertThat(res.getPendingEvent()).isNull();
    }

    @Test
    @DisplayName("getStatus: organizationId が null → pendingEvent: null (DB 参照しない)")
    void getStatus_returnsNull_whenOrgIdNull() {
        KaderuSyncStatusResponse res = service.getStatus(null);

        assertThat(res.getPendingEvent()).isNull();
        verify(eventRepository, never())
                .findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(any(), any());
    }

    // =======================
    // processPendingEvent
    // =======================

    @Test
    @DisplayName("processPendingEvent: workflow success → COMPLETED + 完了通知 (summary はログから抽出)")
    void processPendingEvent_finalizesCompleted_onSuccess() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).organizationId(1L).triggeredByPlayerId(7L)
                .triggeredAt(JstDateTimeUtil.now().minusMinutes(2))
                .status(SyncStatus.PENDING)
                .githubRunId(123456L)
                .build();
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(gitHubActionsClient.getWorkflowRun(123456L))
                .thenReturn(Optional.of(new WorkflowRun(123456L, "completed", "success",
                        "2026-05-24T15:00:00Z", "https://x", "Kaderu sync [event:100] hokudai")));
        when(gitHubActionsClient.fetchWorkflowLogText(123456L))
                .thenReturn(Optional.of("[hokudai] 処理結果:\n  新規作成: 3件\n  会場拡張: 1件\n  スキップ: 5件"));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(hokudai));

        service.processPendingEvent(100L);

        assertThat(event.getStatus()).isEqualTo(SyncStatus.COMPLETED);
        assertThat(event.getCompletedAt()).isNotNull();
        assertThat(event.getSummary()).isEqualTo("新規 3件 / 拡張 1件 / スキップ 5件");
        verify(eventRepository).save(event);
        verify(lineNotificationService).sendKaderuSyncCompletedNotification(7L, "hokudai",
                "新規 3件 / 拡張 1件 / スキップ 5件");
    }

    @Test
    @DisplayName("processPendingEvent: workflow failure → FAILED + 失敗通知")
    void processPendingEvent_finalizesFailed_onFailureConclusion() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).organizationId(1L).triggeredByPlayerId(7L)
                .triggeredAt(JstDateTimeUtil.now().minusMinutes(2))
                .status(SyncStatus.PENDING)
                .githubRunId(123456L)
                .build();
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(gitHubActionsClient.getWorkflowRun(123456L))
                .thenReturn(Optional.of(new WorkflowRun(123456L, "completed", "failure",
                        "2026-05-24T15:00:00Z", "https://x", "Kaderu sync [event:100] hokudai")));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(hokudai));

        service.processPendingEvent(100L);

        assertThat(event.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(event.getFailureReason()).isEqualTo("workflow failure");
        verify(lineNotificationService).sendKaderuSyncFailedNotification(7L, "hokudai", "workflow failure");
        verify(gitHubActionsClient, never()).fetchWorkflowLogText(anyLong());
    }

    @Test
    @DisplayName("processPendingEvent: 30分超過 PENDING → FAILED + タイムアウト通知 (run取得もしない)")
    void processPendingEvent_finalizesFailed_onTimeout() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).organizationId(1L).triggeredByPlayerId(7L)
                .triggeredAt(JstDateTimeUtil.now().minusMinutes(31))
                .status(SyncStatus.PENDING)
                .githubRunId(null)
                .build();
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(hokudai));

        service.processPendingEvent(100L);

        assertThat(event.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(event.getFailureReason()).isEqualTo("30分タイムアウト");
        verify(gitHubActionsClient, never()).getWorkflowRun(anyLong());
        verify(lineNotificationService).sendKaderuSyncFailedNotification(7L, "hokudai", "30分タイムアウト");
    }

    @Test
    @DisplayName("processPendingEvent: github_run_id 未解決なら run-name の [event:<id>] トークンで一意特定して補完")
    void processPendingEvent_resolvesRunId_whenNull() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).organizationId(1L).triggeredByPlayerId(7L)
                .triggeredAt(JstDateTimeUtil.now().minusSeconds(20))
                .status(SyncStatus.PENDING)
                .githubRunId(null)
                .build();
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        // 別団体の run と混在しても displayTitle トークンで誤割当しないことを検証する
        when(gitHubActionsClient.listRecentRuns(eq("sync-kaderu-reservations-manual.yml"), any(Instant.class)))
                .thenReturn(List.of(
                        new WorkflowRun(999L, "queued", null, "2026-05-24T15:00:00Z", "https://x",
                                "Kaderu sync [event:99] wasura"),
                        new WorkflowRun(123L, "queued", null, "2026-05-24T15:00:10Z", "https://x",
                                "Kaderu sync [event:100] hokudai")));
        when(gitHubActionsClient.getWorkflowRun(123L))
                .thenReturn(Optional.of(new WorkflowRun(123L, "queued", null, "2026-05-24T15:00:10Z", "https://x",
                        "Kaderu sync [event:100] hokudai")));

        service.processPendingEvent(100L);

        assertThat(event.getGithubRunId()).isEqualTo(123L);
        assertThat(event.getStatus()).isEqualTo(SyncStatus.PENDING); // まだ queued
        verify(eventRepository, times(1)).save(event); // run_id 補完の1回のみ
        verify(lineNotificationService, never())
                .sendKaderuSyncCompletedNotification(any(), any(), any());
    }

    @Test
    @DisplayName("processPendingEvent: listRecentRuns に自分の eventId を含む run がなければ何もせず次回ティック待ち")
    void processPendingEvent_skipsWhenRunIdUnresolved() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).organizationId(1L).triggeredByPlayerId(7L)
                .triggeredAt(JstDateTimeUtil.now().minusSeconds(10))
                .status(SyncStatus.PENDING)
                .githubRunId(null)
                .build();
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        // 別 event のみ含まれており自分の token はない
        when(gitHubActionsClient.listRecentRuns(any(), any(Instant.class)))
                .thenReturn(List.of(new WorkflowRun(999L, "queued", null,
                        "2026-05-24T15:00:00Z", "https://x", "Kaderu sync [event:99] wasura")));

        service.processPendingEvent(100L);

        assertThat(event.getStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(event.getGithubRunId()).isNull();
        verify(eventRepository, never()).save(any());
        verify(gitHubActionsClient, never()).getWorkflowRun(anyLong());
    }

    @Test
    @DisplayName("processPendingEvent: 既に PENDING でないイベントは無視 (idempotent)")
    void processPendingEvent_ignoresNonPending() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).status(SyncStatus.COMPLETED).build();
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));

        service.processPendingEvent(100L);

        verify(gitHubActionsClient, never()).getWorkflowRun(anyLong());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("processPendingEvent: workflow まだ in_progress → 何もしない (次回ティック待ち)")
    void processPendingEvent_skipsWhenNotCompleted() {
        KaderuSyncTriggerEvent event = KaderuSyncTriggerEvent.builder()
                .id(100L).organizationId(1L).triggeredByPlayerId(7L)
                .triggeredAt(JstDateTimeUtil.now().minusMinutes(1))
                .status(SyncStatus.PENDING)
                .githubRunId(123L)
                .build();
        when(eventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(gitHubActionsClient.getWorkflowRun(123L))
                .thenReturn(Optional.of(new WorkflowRun(123L, "in_progress", null, "2026-05-24T15:00:00Z",
                        "https://x", "Kaderu sync [event:100] hokudai")));

        service.processPendingEvent(100L);

        assertThat(event.getStatus()).isEqualTo(SyncStatus.PENDING);
        verify(eventRepository, never()).save(any());
        verify(lineNotificationService, never())
                .sendKaderuSyncCompletedNotification(any(), any(), any());
    }

    // =======================
    // pollPendingEvents (外側ループ)
    // =======================

    @Test
    @DisplayName("pollPendingEvents: 各イベントの例外は他に波及せず、リスト全件処理する")
    void pollPendingEvents_catchesPerEventExceptions() {
        KaderuSyncTriggerEvent ev1 = KaderuSyncTriggerEvent.builder()
                .id(1L).organizationId(1L).status(SyncStatus.PENDING)
                .triggeredAt(JstDateTimeUtil.now().minusMinutes(1))
                .githubRunId(11L).triggeredByPlayerId(7L).build();
        KaderuSyncTriggerEvent ev2 = KaderuSyncTriggerEvent.builder()
                .id(2L).organizationId(2L).status(SyncStatus.PENDING)
                .triggeredAt(JstDateTimeUtil.now().minusMinutes(1))
                .githubRunId(22L).triggeredByPlayerId(7L).build();
        when(eventRepository.findAllByStatusOrderByTriggeredAtAsc(SyncStatus.PENDING))
                .thenReturn(List.of(ev1, ev2));
        when(eventRepository.findById(1L)).thenThrow(new RuntimeException("boom"));
        when(eventRepository.findById(2L)).thenReturn(Optional.of(ev2));
        when(gitHubActionsClient.getWorkflowRun(22L))
                .thenReturn(Optional.of(new WorkflowRun(22L, "in_progress", null, null, null, null)));

        service.pollPendingEvents();

        // 2件目もちゃんと処理されたこと（getWorkflowRun が呼ばれた）
        verify(gitHubActionsClient).getWorkflowRun(22L);
    }

    // =======================
    // extractSummary regex
    // =======================

    @Test
    @DisplayName("extractSummary: 集計3行から所定フォーマットの文字列を組み立てる")
    void extractSummary_parsesAllThreeNumbers() {
        when(gitHubActionsClient.fetchWorkflowLogText(99L)).thenReturn(Optional.of(
                "ログ前半 \n  新規作成: 12件\n  会場拡張: 0件\n  スキップ: 4件\nログ後半"));
        String summary = service.extractSummary(99L);
        assertThat(summary).isEqualTo("新規 12件 / 拡張 0件 / スキップ 4件");
    }

    @Test
    @DisplayName("extractSummary: 一部だけマッチするケースは '?' で埋める")
    void extractSummary_partialMatch_fillsWithQuestionMark() {
        when(gitHubActionsClient.fetchWorkflowLogText(99L)).thenReturn(Optional.of(
                "  新規作成: 5件\n"));
        String summary = service.extractSummary(99L);
        assertThat(summary).isEqualTo("新規 5件 / 拡張 ?件 / スキップ ?件");
    }

    @Test
    @DisplayName("extractSummary: 全くマッチしないログは null を返す")
    void extractSummary_noMatch_returnsNull() {
        when(gitHubActionsClient.fetchWorkflowLogText(99L)).thenReturn(Optional.of(
                "completely unrelated log text"));
        assertThat(service.extractSummary(99L)).isNull();
    }

    @Test
    @DisplayName("extractSummary: ログ取得自体に失敗 → null")
    void extractSummary_logFetchFailed_returnsNull() {
        when(gitHubActionsClient.fetchWorkflowLogText(99L)).thenReturn(Optional.empty());
        assertThat(service.extractSummary(99L)).isNull();
    }
}
