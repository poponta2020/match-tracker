package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDateTime;

/**
 * 伝助メンバーIDマッピングエンティティ（URL単位）
 */
@Entity
@Table(name = "densuke_member_mappings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"densuke_url_id", "player_id"}),
    @UniqueConstraint(columnNames = {"densuke_url_id", "densuke_member_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DensukeMemberMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "densuke_url_id", nullable = false)
    private Long densukeUrlId;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "densuke_member_id", nullable = false, length = 50)
    private String densukeMemberId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
    }
}
