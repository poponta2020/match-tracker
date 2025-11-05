package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player1_id", nullable = false)
    private Player player1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id", nullable = false)
    private Player player2;

    @Column(name = "match_date", nullable = false)
    private LocalDate matchDate;

    @Column(length = 200)
    private String location;

    @Column(name = "player1_score", nullable = false)
    private Integer player1Score;

    @Column(name = "player2_score", nullable = false)
    private Integer player2Score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Player winner;

    @Column(name = "match_type", length = 50)
    private String matchType;  // 例：練習試合、公式戦、トーナメントなど

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // 勝者を自動判定
        if (player1Score > player2Score) {
            winner = player1;
        } else if (player2Score > player1Score) {
            winner = player2;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        // 勝者を自動判定
        if (player1Score > player2Score) {
            winner = player1;
        } else if (player2Score > player1Score) {
            winner = player2;
        }
    }
}
