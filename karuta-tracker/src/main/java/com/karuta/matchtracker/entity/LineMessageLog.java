package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * LINE送信ログエンティティ
 */
@Entity
@Table(name = "line_message_log", indexes = {
    @Index(name = "idx_line_log_channel", columnList = "line_channel_id"),
    @Index(name = "idx_line_log_player", columnList = "player_id"),
    @Index(name = "idx_line_log_type_sent", columnList = "notification_type, sent_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineMessageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LINEチャネルID（FK） */
    @Column(name = "line_channel_id", nullable = false)
    private Long lineChannelId;

    /** プレイヤーID（FK） */
    @Column(name = "player_id", nullable = false)
    private Long playerId;

    /** 通知種別 */
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private LineNotificationType notificationType;

    /** 送信メッセージ内容 */
    @Column(name = "message_content", nullable = false, columnDefinition = "TEXT")
    private String messageContent;

    /** 送信ステータス */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** 失敗時のエラー内容 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 関連先ID（セッションIDや参加者IDなど） */
    @Column(name = "reference_id")
    private Long referenceId;

    /** 送信日時 */
    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
