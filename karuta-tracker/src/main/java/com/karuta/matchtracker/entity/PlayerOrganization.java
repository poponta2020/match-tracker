package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * ユーザーと団体の紐づけエンティティ
 */
@Entity
@Table(name = "player_organizations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"player_id", "organization_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerOrganization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Long playerId;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * カレンダー購読フィード上での団体表示名（ユーザー個別オーバーライド）
     * NULLの場合は Organization.name をそのまま使う
     */
    @Column(name = "calendar_display_name", length = 50)
    private String calendarDisplayName;

    @PrePersist
    protected void onCreate() {
        createdAt = JstDateTimeUtil.now();
    }
}
