package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 札ルール再生成カウンタ(nonce)を日付単位でDB共有するエンティティ。
 *
 * 出札50枚は (日付, nonce) から決定論的に生成されるため、nonce を端末ローカル
 * (localStorage) ではなくDBで共有することで、対戦組み合わせ画面と取り札記録画面が
 * 全端末で同じ50枚を参照できる。
 */
@Entity
@Table(name = "card_rule_nonce",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_card_rule_nonce_date", columnNames = {"session_date"})
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardRuleNonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Builder.Default
    @Column(nullable = false)
    private Integer nonce = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
