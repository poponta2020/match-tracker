package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 伝助行IDキャッシュエンティティ（日付×試合番号の join-{id} を保存）
 */
@Entity
@Table(name = "densuke_row_ids", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"densuke_url_id", "session_date", "match_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DensukeRowId {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "densuke_url_id", nullable = false)
    private Long densukeUrlId;

    @Column(name = "densuke_row_id", nullable = false, length = 50)
    private String densukeRowId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "match_number", nullable = false)
    private Integer matchNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
    }
}
