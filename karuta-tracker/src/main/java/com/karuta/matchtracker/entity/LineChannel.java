package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * LINEチャネル情報エンティティ
 */
@Entity
@Table(name = "line_channels", indexes = {
    @Index(name = "idx_line_channel_status", columnList = "status"),
    @Index(name = "idx_line_channel_line_id", columnList = "line_channel_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 管理用表示名 */
    @Column(name = "channel_name", length = 100)
    private String channelName;

    /** LINE発行のチャネルID */
    @Column(name = "line_channel_id", nullable = false, unique = true, length = 50)
    private String lineChannelId;

    /** チャネルシークレット（暗号化保存） */
    @Column(name = "channel_secret", nullable = false, length = 255)
    private String channelSecret;

    /** チャネルアクセストークン（暗号化保存） */
    @Column(name = "channel_access_token", nullable = false, columnDefinition = "TEXT")
    private String channelAccessToken;

    /** ステータス */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ChannelStatus status = ChannelStatus.AVAILABLE;

    /** ベーシックID（例: @111aaaaa） */
    @Column(name = "basic_id", length = 30)
    private String basicId;

    /** 当月送信数 */
    @Column(name = "monthly_message_count", nullable = false)
    @Builder.Default
    private Integer monthlyMessageCount = 0;

    /** 送信数リセット日時 */
    @Column(name = "message_count_reset_at")
    private LocalDateTime messageCountResetAt;

    /** 作成日時 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 更新日時 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ChannelStatus {
        AVAILABLE,
        ASSIGNED,
        LINKED,
        DISABLED
    }

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
        updatedAt = JstDateTimeUtil.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = JstDateTimeUtil.now();
    }
}
