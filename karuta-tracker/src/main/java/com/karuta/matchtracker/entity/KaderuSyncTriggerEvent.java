package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Kaderu予約取り込み手動トリガーイベント。
 *
 * ADMIN+ ユーザーが /practice 画面のボタンから GitHub Actions workflow を起動した記録。
 * スケジューラーが status=PENDING を巡回し、workflow run の完了結果に応じて
 * COMPLETED / FAILED に確定する。完了時には押下者本人へ LINE 通知を送る。
 */
@Entity
@Table(name = "kaderu_sync_trigger_events", indexes = {
    @Index(name = "idx_kaderu_sync_status_triggered", columnList = "status, triggered_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KaderuSyncTriggerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "triggered_by_player_id", nullable = false)
    private Long triggeredByPlayerId;

    @Column(name = "triggered_at", nullable = false)
    private LocalDateTime triggeredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SyncStatus status;

    /** GitHub Actions workflow run の ID。dispatch 直後は null で、スケジューラーが補完する場合がある。 */
    @Column(name = "github_run_id")
    private Long githubRunId;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 同期結果のサマリー（例: "新規:3 拡張:1 スキップ:5"）。ログ抽出に失敗した場合は null。 */
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    /** FAILED 時の失敗理由（workflow conclusion / 例外メッセージ / "タイムアウト" 等）。 */
    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum SyncStatus {
        PENDING,
        COMPLETED,
        FAILED
    }
}
