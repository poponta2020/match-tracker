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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * dispatch 直後の listRecentRuns 呼び出しでサーバ・GitHub 間のクロックずれを
     * 吸収するためのバッファ秒数。triggered_at - 5s 以降の run を候補にする。
     */
    private static final int RUN_LOOKUP_BUFFER_SECONDS = 5;

    private final KaderuSyncTriggerEventRepository eventRepository;
    private final OrganizationRepository organizationRepository;
    private final GitHubActionsClient gitHubActionsClient;

    /**
     * 手動同期を起動する。
     *
     * @param triggeredByPlayerId 押下者のプレイヤーID
     * @param organizationId      対象団体ID（Controller 側で実効ID解決済み）
     * @return 作成されたイベントの DTO（status=PENDING、run_id は埋まっている or null）
     * @throws DuplicateResourceException 同一団体の PENDING が既に存在する場合
     * @throws ResourceNotFoundException  organizationId が見つからない場合
     */
    @Transactional
    public KaderuSyncTriggerEventDto triggerSync(Long triggeredByPlayerId, Long organizationId) {
        if (organizationId == null) {
            // SUPER_ADMIN が organizationId を指定しなかったケース。Controller で
            // 防いでいるが多重防御として残す。
            throw new IllegalArgumentException("organizationId は必須です");
        }

        // 1. 重複起動チェック
        if (eventRepository.findFirstByOrganizationIdAndStatusOrderByTriggeredAtDesc(organizationId, SyncStatus.PENDING).isPresent()) {
            throw new DuplicateResourceException("同一団体の同期が既に実行中です");
        }

        // 2. 団体取得（code 解決 / 存在チェック）
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("団体が見つかりません: id=" + organizationId));
        String orgCode = organization.getCode();

        // 3. GitHub Actions に dispatch（失敗は ResponseStatusException / RuntimeException として伝播）
        LocalDateTime triggeredAt = JstDateTimeUtil.now();
        Instant lookupFrom = triggeredAt.atZone(JstDateTimeUtil.JST).toInstant()
                .minusSeconds(RUN_LOOKUP_BUFFER_SECONDS);
        gitHubActionsClient.dispatchWorkflow(WORKFLOW_FILE, WORKFLOW_REF, Map.of("org", orgCode));

        // 4. dispatch 直後に run_id を解決（取得失敗時は null のまま scheduler に委ねる）
        Long resolvedRunId = resolveRunId(lookupFrom);

        // 5. イベント保存
        KaderuSyncTriggerEvent saved = eventRepository.save(KaderuSyncTriggerEvent.builder()
                .organizationId(organizationId)
                .triggeredByPlayerId(triggeredByPlayerId)
                .triggeredAt(triggeredAt)
                .status(SyncStatus.PENDING)
                .githubRunId(resolvedRunId)
                .build());

        log.info("Kaderu manual sync triggered: eventId={}, organizationId={}, orgCode={}, runId={}",
                saved.getId(), organizationId, orgCode, resolvedRunId);
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
     * dispatch 直後に listRecentRuns を1回試し、未割当の oldest run を返す。
     * GitHub Actions が run を登録する前だと候補が空になり得る — その場合は null を返し、
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
}
