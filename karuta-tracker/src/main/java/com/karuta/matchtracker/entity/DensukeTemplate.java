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
 * 伝助テンプレート管理エンティティ（団体単位）
 */
@Entity
@Table(name = "densuke_templates", uniqueConstraints = {
    @UniqueConstraint(name = "densuke_templates_organization_id_key", columnNames = {"organization_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DensukeTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private Long organizationId;

    @Column(name = "title_template", nullable = false, length = 200)
    private String titleTemplate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
