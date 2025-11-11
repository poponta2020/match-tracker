package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 練習日情報エンティティ
 *
 * 練習日とその日の予定試合数を管理します。
 */
@Entity
@Table(name = "practice_sessions", indexes = {
    @Index(name = "idx_session_date", columnList = "session_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 練習日
     */
    @Column(name = "session_date", nullable = false, unique = true)
    private LocalDate sessionDate;

    /**
     * その日の予定試合数
     */
    @Column(name = "total_matches", nullable = false)
    private Integer totalMatches;

    /**
     * 練習場所
     */
    @Column(name = "location", length = 200)
    private String location;

    /**
     * メモ・備考
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * 練習開始時刻
     */
    @Column(name = "start_time")
    private LocalTime startTime;

    /**
     * 練習終了時刻
     */
    @Column(name = "end_time")
    private LocalTime endTime;

    /**
     * 定員
     */
    @Column(name = "capacity")
    private Integer capacity;

    /**
     * 作成者のID
     */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    /**
     * 最終更新者のID
     */
    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    /**
     * 作成日時
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新日時
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * エンティティ保存前の処理
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * エンティティ更新前の処理
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
