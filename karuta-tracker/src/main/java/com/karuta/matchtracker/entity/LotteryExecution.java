package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 抽選実行履歴エンティティ
 */
@Entity
@Table(name = "lottery_executions", indexes = {
    @Index(name = "idx_lottery_target", columnList = "target_year, target_month")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotteryExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 対象年 */
    @Column(name = "target_year", nullable = false)
    private Integer targetYear;

    /** 対象月 */
    @Column(name = "target_month", nullable = false)
    private Integer targetMonth;

    /** 実行種別 */
    @Enumerated(EnumType.STRING)
    @Column(name = "execution_type", nullable = false, length = 20)
    private ExecutionType executionType;

    /** 対象セッションID（再抽選時のみ） */
    @Column(name = "session_id")
    private Long sessionId;

    /** 実行者ID（自動の場合はNULL） */
    @Column(name = "executed_by")
    private Long executedBy;

    /** 実行日時 */
    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    /** 実行結果 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ExecutionStatus status;

    /** 処理詳細（JSON形式） */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    public enum ExecutionType {
        AUTO,
        MANUAL,
        MANUAL_RELOTTERY
    }

    public enum ExecutionStatus {
        SUCCESS,
        FAILED,
        PARTIAL
    }

    @PrePersist
    protected void onCreate() {
        if (executedAt == null) {
            executedAt = JstDateTimeUtil.now();
        }
    }
}
