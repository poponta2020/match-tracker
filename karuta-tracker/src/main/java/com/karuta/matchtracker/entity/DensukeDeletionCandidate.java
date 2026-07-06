package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 伝助側で削除された試合（日付×試合番号）の削除候補エンティティ。
 * 承認されるまでは何もデータを変更しない（欠番方式・totalMatches は変更しない）。
 */
@Entity
@Table(name = "densuke_deletion_candidates", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"densuke_url_id", "session_date", "match_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DensukeDeletionCandidate {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "densuke_url_id", nullable = false)
    private Long densukeUrlId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "match_number", nullable = false)
    private Integer matchNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = JstDateTimeUtil.now();
        }
    }
}
