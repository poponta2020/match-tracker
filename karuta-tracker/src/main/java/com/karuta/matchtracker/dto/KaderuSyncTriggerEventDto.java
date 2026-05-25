package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.KaderuSyncTriggerEvent;
import com.karuta.matchtracker.entity.KaderuSyncTriggerEvent.SyncStatus;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Kaderu 同期トリガーイベントの DTO。
 *
 * <p>トリガー API のレスポンスとステータス API の {@code pendingEvent} で共用する。
 * {@code elapsedSeconds} は PENDING 中の経過秒数で、{@link #fromEntity(KaderuSyncTriggerEvent, String)}
 * が呼び出し時点の JST 現在時刻から自動計算する。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KaderuSyncTriggerEventDto {

    private Long id;
    private Long organizationId;
    private String organizationCode;
    private Long triggeredByPlayerId;
    private LocalDateTime triggeredAt;
    private SyncStatus status;
    private Long githubRunId;
    private LocalDateTime completedAt;
    private String summary;
    private String failureReason;
    /** PENDING の場合は triggered_at からの経過秒数。COMPLETED/FAILED でも参考として常に算出する。 */
    private Long elapsedSeconds;

    public static KaderuSyncTriggerEventDto fromEntity(KaderuSyncTriggerEvent entity, String organizationCode) {
        if (entity == null) return null;
        long elapsed = Duration.between(entity.getTriggeredAt(), JstDateTimeUtil.now()).getSeconds();
        if (elapsed < 0) elapsed = 0;
        return KaderuSyncTriggerEventDto.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganizationId())
                .organizationCode(organizationCode)
                .triggeredByPlayerId(entity.getTriggeredByPlayerId())
                .triggeredAt(entity.getTriggeredAt())
                .status(entity.getStatus())
                .githubRunId(entity.getGithubRunId())
                .completedAt(entity.getCompletedAt())
                .summary(entity.getSummary())
                .failureReason(entity.getFailureReason())
                .elapsedSeconds(elapsed)
                .build();
    }
}
