package com.karuta.matchtracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * 試合動画エンティティ
 *
 * 練習試合の動画（YouTube限定公開）のURLを試合と紐付けて管理する「動画台帳」。
 * matches / match_pairings とは FK を持たず、
 * (match_date, match_number, player1_id, player2_id) の自然キーで対応付きます。
 * player1_id < player2_id の制約をアプリケーション層で保証します。
 */
@Entity
@Table(name = "match_videos",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_match_videos_match",
            columnNames = {"match_date", "match_number", "player1_id", "player2_id"})
    },
    indexes = {
        @Index(name = "idx_match_videos_player1", columnList = "player1_id"),
        @Index(name = "idx_match_videos_player2", columnList = "player2_id")
    })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchVideo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 対戦日
     */
    @Column(name = "match_date", nullable = false)
    private LocalDate matchDate;

    /**
     * その日の第何試合目か
     */
    @Column(name = "match_number", nullable = false)
    private Integer matchNumber;

    /**
     * 選手1のID（player1_id < player2_id を保証）
     */
    @Column(name = "player1_id", nullable = false)
    private Long player1Id;

    /**
     * 選手2のID
     */
    @Column(name = "player2_id", nullable = false)
    private Long player2Id;

    /**
     * 動画プロバイダ（現状は YOUTUBE のみ）
     */
    @Builder.Default
    @Column(name = "provider", nullable = false, length = 20)
    private String provider = "YOUTUBE";

    /**
     * 動画URL
     */
    @Column(name = "video_url", nullable = false, columnDefinition = "TEXT")
    private String videoUrl;

    /**
     * YouTube動画ID（video_urlから抽出した埋め込み用ID）
     */
    @Column(name = "youtube_video_id", length = 20)
    private String youtubeVideoId;

    /**
     * 動画タイトル
     */
    @Column(name = "title", length = 255)
    private String title;

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
        createdAt = JstDateTimeUtil.now();
        updatedAt = JstDateTimeUtil.now();
        ensurePlayer1LessThanPlayer2();
    }

    /**
     * エンティティ更新前の処理
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = JstDateTimeUtil.now();
        ensurePlayer1LessThanPlayer2();
    }

    /**
     * player1_id < player2_id を保証
     * 必要に応じて player1 と player2 を入れ替える
     */
    private void ensurePlayer1LessThanPlayer2() {
        if (player1Id != null && player2Id != null && player1Id > player2Id) {
            Long temp = player1Id;
            player1Id = player2Id;
            player2Id = temp;
        }
    }
}
