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
import java.util.Comparator;
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
     * <p>run_id の解決はこのメソッド内では行わず、PENDING 状態で保存して scheduler に
     * 委ねる。これは、ほぼ同時に複数団体（hokudai/wasura）のディスパッチが行われた場合に
     * 「自分が dispatch した run」と「直前の別 dispatch の run」を区別できず、誤って
     * 他団体の run_id を割り当ててしまう race を避けるため。scheduler は triggered_at
     * 昇順で1イベントずつ処理するので、ディスパッチ順と整合した割当ができる。
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

        // 3. GitHub Actions に dispatch（失敗は ResponseStatusException / RuntimeException として伝播）
        LocalDateTime triggeredAt = JstDateTimeUtil.now();
        gitHubActionsClient.dispatchWorkflow(WORKFLOW_FILE, WORKFLOW_REF, Map.of("org", orgCode));

        // 4. イベント保存（run_id は scheduler が後から補完する）
        KaderuSyncTriggerEvent saved;
        try {
            saved = eventRepository.save(KaderuSyncTriggerEvent.builder()
                    .organizationId(organizationId)
                    .triggeredByPlayerId(triggeredByPlayerId)
                    .triggeredAt(triggeredAt)
                    .status(SyncStatus.PENDING)
                    .githubRunId(null)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 事前チェックを潜り抜けた同時リクエストとの race。UNIQUE 部分インデックス
            // (uk_kaderu_sync_pending) が衝突を検知してくれる。
            // この時点で workflow は既に dispatch 済みだが、scheduler は同一 org の
            // 唯一の PENDING（先勝ちした方）にこの run_id を割り当てるため、データ整合性は保たれる。
            log.info("Duplicate PENDING detected at insert (race): organizationId={}", organizationId);
            throw new DuplicateResourceException("同一団体の同期が既に実行中です");
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
     * scheduler から呼ばれる run_id 解決。指定時刻以降に作成された未割当 run のうち
     * 最も古いものを返す。triggered_at 昇順で1イベントずつ処理されるので、
     * ディスパッチ順 (= run_id 昇順) と整合した割当ができる。
     *
     * <p>GitHub Actions が run を登録する前だと候補が空になり得る — その場合は null を返し、
     * scheduler の次回ティックで再試行させる。
     */
    private Long resolveRunId(Instant lookupFrom) {
        List<WorkflowRun> runs = gitHubActionsClient.listRecentRuns(WORKFLOW_FILE, lookupFrom);
        return runs.stream()
                .sorted(Comparator.comparingLong(WorkflowRun::id))
                .filter(r -> !eventRepository.existsByGithubRunId(r.id()))
                .map(WorkflowRun::id)
                .findFirst()
                .orElse(null);
    }

    /**
     * 全 PENDING イベントを巡回し、状態確定 + 通知送信を行う。
     *
     * <p>1イベントごとに独立した tx を張り、外部 API 呼び出し失敗が他イベントに
     * 波及しないよう catch する。Scheduler から30秒間隔で呼ばれる。
     */
    public void pollPendingEvents() {
        List<Long> pendingIds;
        try {
            pendingIds = listPendingIds();
        } catch (Exception e) {
            log.warn("Failed to list pending kaderu sync events: {}", e.getMessage(), e);
            return;
        }
        if (pendingIds.isEmpty()) return;

        log.debug("Polling {} pending kaderu sync event(s)", pendingIds.size());
        for (Long id : pendingIds) {
            try {
                processPendingEvent(id);
            } catch (Exception e) {
                log.warn("Failed to process pending kaderu sync event {}: {}", id, e.getMessage(), e);
            }
        }
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

        // 2. run_id 補完
        if (event.getGithubRunId() == null) {
            Instant lookupFrom = event.getTriggeredAt().atZone(JstDateTimeUtil.JST).toInstant()
                    .minusSeconds(RUN_LOOKUP_BUFFER_SECONDS);
            Long runId = resolveRunId(lookupFrom);
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
