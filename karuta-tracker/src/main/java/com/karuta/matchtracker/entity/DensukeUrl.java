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
 * 伝助URL管理エンティティ（月別）
 */
@Entity
@Table(name = "densuke_urls", uniqueConstraints = {
    @UniqueConstraint(name = "densuke_urls_year_month_org_key", columnNames = {"year", "month", "organization_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DensukeUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer year;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /**
     * 伝助の編集用シークレット (sd)。
     * アプリから自動作成した伝助ページでのみ値が入り、手動登録 URL は NULL。
     * 将来の編集・削除 API で必要になる想定で保存する。
     */
    @Column(name = "densuke_sd", length = 32)
    private String densukeSd;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
