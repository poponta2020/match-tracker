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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kaderu 予約取り込み手動トリガーのアプリケーションサービス。
 *
 * <p>{@link #triggerSync(Long, Long)} は GitHub Actions の dispatch を発火し、
 * 結果イベントを {@code kaderu_sync_trigger_events} に PENDING で保存する。
 * dispatch 直後は run_id がすぐ取れないこともあるため、取得失敗時は null で保存し、
 * {@link com.karuta.matchtracker.scheduler.KaderuSyncStatusPollingScheduler}
 * が後追いで補完する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KaderuSyncTriggerService {

    public static final String WORKFLOW_FILE = "sync-kaderu-reservations-manual.yml";
    public static final String WORKFLOW_REF = "main";

    /**
     * scheduler が listRecentRuns を呼び出す際の時刻フィルタのバッファ秒数。
     * サーバ・GitHub 間のクロックずれを吸収するため、triggered_at - 5s 以降の run を候補にする。
     */
    private static final int RUN_LOOKUP_BUFFER_SECONDS = 5;

    /** PENDING が完了しないまま放置される最大時間。これを超えたら fail-safe で FAILED に確定。 */
    private static final long PENDING_TIMEOUT_MINUTES = 30;

    /** sync-reservations.js が出力する集計行のパターン。 */
    private static final Pattern CREATED_PATTERN = Pattern.compile("新規作成:\\s*(\\d+)件");
    private static final Pattern EXPANDED_PATTERN = Pattern.compile("会場拡張:\\s*(\\d+)件");
    private static final Pattern SKIPPED_PATTERN = Pattern.compile("スキップ:\\s*(\\d+)件");

    private final KaderuSyncTriggerEventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final GitHubActionsClient gitHubActionsClient;
    private final LineNotificationService lineNotificationService;

    /**
     * 手動同期を起動する。
     *
     * <p>順序が重要：
     * <ol>
     *   <li>事前重複チェック (409 fast-path)</li>
     *   <li>団体取得 (404)</li>
     *   <li>イベントを PENDING で {@code saveAndFlush} し、UNIQUE 部分インデックス
     *       {@code uk_kaderu_sync_pending} を即座に判定 → 違反なら 409 に変換</li>
     *   <li>{@code dispatchWorkflow} に {@code eventId} を相関 ID として渡す</li>
     *   <li>dispatch 失敗時は {@code @Transactional} により save が rollback される
     *       （未追跡の workflow を起動しない原則）</li>
     * </ol>
     *
     * <p>同時リクエストの挙動 (R1, R2 が同一団体に対しほぼ同時に到達):
     * <ul>
     *   <li>R1: 事前チェック通過 → saveAndFlush で INSERT (UNIQUE 制約取得)</li>
     *   <li>R2: 事前チェック通過 → saveAndFlush は UNIQUE 制約により R1 の commit/rollback まで block</li>
     *   <li>R1: dispatch 成功 → method exit で commit → R2 は unique violation で 409。
     *       R2 は dispatch を呼ばないので、workflow は1本だけ走る</li>
     *   <li>R1: dispatch 失敗 → throw → rollback → R2 は INSERT 成功 → dispatch する</li>
     * </ul>
     *
     * @param triggeredByPlayerId 押下者のプレイヤーID
     * @param organizationId      対象団体ID（Controller 側で実効ID解決済み）
     * @return 作成されたイベントの DTO（status=PENDING、run_id は常に null）
     * @throws DuplicateResourceException 同一団体の PENDING が既に存在する場合（事前チェック or
     *                                    UNIQUE 部分インデックス違反のいずれか）
     * @throws ResourceNotFoundException  organizationId が見つからない場合
     */
    @Transactional
    public KaderuSyncTriggerEventDto triggerSync(Long triggeredByPlayerId, Long organizationId) {
        if (organizationId == null) {
            // SUPER_ADMIN が organizationId を指定しなかったケース。Controller で
            // 防いでいるが多重防御として残す。
            throw new IllegalArgumentException("organizationId は必須です");
        }

        // 1. 事前重複起動チェック（高速 path。確定的な防御は uk_kaderu_sync_pending の UNIQUE 制約）
        if (eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(organizationId, SyncStatus.PENDING).isPresent()) {
            throw new DuplicateResourceException("同一団体の同期が既に実行中です");
        }

        // 2. 団体取得（code 解決 / 存在チェック）
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("団体が見つかりません: id=" + organizationId));
        String orgCode = organization.getCode();

        // 3. PENDING を先に保存（saveAndFlush で UNIQUE 制約を即時判定 → race を確実に検知）
        LocalDateTime triggeredAt = JstDateTimeUtil.now();
        KaderuSyncTriggerEvent saved;
        try {
            saved = eventRepository.saveAndFlush(KaderuSyncTriggerEvent.builder()
                    .organizationId(organizationId)
                    .triggeredByPlayerId(triggeredByPlayerId)
                    .triggeredAt(triggeredAt)
                    .status(SyncStatus.PENDING)
                    .githubRunId(null)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate PENDING detected at insert (race): organizationId={}", organizationId);
            throw new DuplicateResourceException("同一団体の同期が既に実行中です");
        }

        // 4. dispatch（失敗時は @Transactional が save を rollback する。未追跡 workflow を起動しない）
        try {
            gitHubActionsClient.dispatchWorkflow(WORKFLOW_FILE, WORKFLOW_REF, Map.of(
                    "org", orgCode,
                    // eventId は workflow の run-name に埋め込まれ、scheduler が
                    // display_title で event ↔ run を一意に相関させる相関 ID。
                    "eventId", String.valueOf(saved.getId())));
        } catch (RuntimeException dispatchEx) {
            log.warn("Kaderu dispatch failed; transaction will be rolled back: eventId={}, err={}",
                    saved.getId(), dispatchEx.getMessage());
            throw dispatchEx;
        }

        log.info("Kaderu manual sync triggered: eventId={}, organizationId={}, orgCode={}",
                saved.getId(), organizationId, orgCode);
        return KaderuSyncTriggerEventDto.fromEntity(saved, orgCode);
    }

    /**
     * 指定団体の進行中（PENDING）イベントを返す。なければ {@code pendingEvent=null}。
     */
    @Transactional(readOnly = true)
    public KaderuSyncStatusResponse getStatus(Long organizationId) {
        if (organizationId == null) {
            return KaderuSyncStatusResponse.builder().pendingEvent(null).build();
        }
        Optional<KaderuSyncTriggerEvent> pending = eventRepository
                .findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(organizationId, SyncStatus.PENDING);
        if (pending.isEmpty()) {
            return KaderuSyncStatusResponse.builder().pendingEvent(null).build();
        }
        KaderuSyncTriggerEvent event = pending.get();
        String orgCode = organizationRepository.findById(event.getOrganizationId())
                .map(Organization::getCode)
                .orElse(null);
        return KaderuSyncStatusResponse.builder()
                .pendingEvent(KaderuSyncTriggerEventDto.fromEntity(event, orgCode))
                .build();
    }

    /**
     * scheduler から呼ばれる run_id 解決。workflow の run-name に埋め込まれた
     * 相関 ID トークン {@code [event:<id>]} で対応する workflow run を一意に特定する。
     *
     * <p>GitHub Actions が run を登録する前 (= dispatch 直後数秒) は候補に含まれず、
     * 結果として null を返す。scheduler の次回ティックで再試行される。
     *
     * <p>display_title が null の run (旧形式 / 異なるワークフロー) は無視する。
     */
    private Long resolveRunIdForEvent(KaderuSyncTriggerEvent event) {
        Instant lookupFrom = event.getTriggeredAt().atZone(JstDateTimeUtil.JST).toInstant()
                .minusSeconds(RUN_LOOKUP_BUFFER_SECONDS);
        String correlationToken = buildCorrelationToken(event.getId());
        List<WorkflowRun> runs = gitHubActionsClient.listRecentRuns(WORKFLOW_FILE, lookupFrom);
        return runs.stream()
                .filter(r -> r.displayTitle() != null && r.displayTitle().contains(correlationToken))
                .map(WorkflowRun::id)
                .findFirst()
                .orElse(null);
    }

    /** Workflow の run-name に埋め込む相関 ID トークン (例: "[event:123]")。 */
    static String buildCorrelationToken(long eventId) {
        return "[event:" + eventId + "]";
    }

    @Transactional(readOnly = true)
    public List<Long> listPendingIds() {
        // triggered_at 昇順で取得。ディスパッチ順 (= run_id 昇順) と整合した
        // run_id 割当を行うために必須。
        return eventRepository.findAllByStatusOrderByTriggeredAtAsc(SyncStatus.PENDING).stream()
                .map(KaderuSyncTriggerEvent::getId)
                .toList();
    }

    /**
     * 1つの PENDING イベントを処理する：
     * <ol>
     *   <li>{@link #PENDING_TIMEOUT_MINUTES} 超過なら FAILED 確定 + 失敗通知</li>
     *   <li>github_run_id が未解決なら listRecentRuns で補完</li>
     *   <li>workflow run の status/conclusion を取得し、completed なら COMPLETED/FAILED に確定 + 通知</li>
     * </ol>
     */
    @Transactional
    public void processPendingEvent(Long eventId) {
        KaderuSyncTriggerEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != SyncStatus.PENDING) {
            return;
        }
        LocalDateTime now = JstDateTimeUtil.now();

        // 1. タイムアウト fail-safe
        if (Duration.between(event.getTriggeredAt(), now).toMinutes() >= PENDING_TIMEOUT_MINUTES) {
            finalizeFailed(event, "30分タイムアウト");
            return;
        }

        // 2. run_id 補完（run-name の相関 ID トークンで一意特定）
        if (event.getGithubRunId() == null) {
            Long runId = resolveRunIdForEvent(event);
            if (runId == null) {
                return; // 次回ティックで再試行
            }
            event.setGithubRunId(runId);
            eventRepository.save(event);
        }

        // 3. run の状態取得
        Optional<WorkflowRun> runOpt = gitHubActionsClient.getWorkflowRun(event.getGithubRunId());
        if (runOpt.isEmpty()) {
            return; // 取得失敗。次回ティックで再試行
        }
        WorkflowRun run = runOpt.get();
        if (!run.isCompleted()) {
            return; // まだ queued / in_progress
        }

        if (run.isSuccess()) {
            String summary = extractSummary(event.getGithubRunId());
            finalizeCompleted(event, summary);
        } else {
            String reason = "workflow " + (run.conclusion() != null ? run.conclusion() : "failure");
            finalizeFailed(event, reason);
        }
    }

    private void finalizeCompleted(KaderuSyncTriggerEvent event, String summary) {
        event.setStatus(SyncStatus.COMPLETED);
        event.setCompletedAt(JstDateTimeUtil.now());
        event.setSummary(summary);
        eventRepository.save(event);

        String orgCode = organizationRepository.findById(event.getOrganizationId())
                .map(Organization::getCode).orElse(null);
        log.info("KaderuSync event {} COMPLETED (orgCode={}, summary={})",
                event.getId(), orgCode, summary);
        lineNotificationService.sendKaderuSyncCompletedNotification(
                event.getTriggeredByPlayerId(), orgCode, summary);
    }

    private void finalizeFailed(KaderuSyncTriggerEvent event, String reason) {
        event.setStatus(SyncStatus.FAILED);
        event.setCompletedAt(JstDateTimeUtil.now());
        event.setFailureReason(reason);
        eventRepository.save(event);

        String orgCode = organizationRepository.findById(event.getOrganizationId())
                .map(Organization::getCode).orElse(null);
        log.warn("KaderuSync event {} FAILED (orgCode={}, reason={})",
                event.getId(), orgCode, reason);
        lineNotificationService.sendKaderuSyncFailedNotification(
                event.getTriggeredByPlayerId(), orgCode, reason);
    }

    /**
     * workflow run のログを取得し、{@code 新規作成: X件 / 会場拡張: X件 / スキップ: X件}
     * 形式の集計行を抽出してサマリー文字列に整形する。取得や解析に失敗したら null。
     */
    String extractSummary(long runId) {
        Optional<String> logOpt = gitHubActionsClient.fetchWorkflowLogText(runId);
        if (logOpt.isEmpty()) return null;
        String logText = logOpt.get();
        String created = firstMatch(logText, CREATED_PATTERN);
        String expanded = firstMatch(logText, EXPANDED_PATTERN);
        String skipped = firstMatch(logText, SKIPPED_PATTERN);
        if (created == null && expanded == null && skipped == null) {
            return null;
        }
        return String.format("新規 %s件 / 拡張 %s件 / スキップ %s件",
                created != null ? created : "?",
                expanded != null ? expanded : "?",
                skipped != null ? skipped : "?");
    }

    private static String firstMatch(String text, Pattern pattern) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
