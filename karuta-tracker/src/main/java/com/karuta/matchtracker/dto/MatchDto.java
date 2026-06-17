package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchVideo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 試合結果のDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchDto {

    private Long id;
    private LocalDate matchDate;
    private Integer matchNumber;
    private Long player1Id;
    private String player1Name;
    private Long player2Id;
    private String player2Name;
    private Long winnerId;
    private String winnerName;
    private Integer scoreDifference;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    // 対戦時の級位
    private String player1KyuRank;
    private String player2KyuRank;

    // 会場（venueName は enrichment で別途設定）
    private Long venueId;
    private String venueName;

    // フロントエンド用の追加フィールド
    private String opponentName;  // 対戦相手名（簡易表示用）
    private String result;         // 結果（勝ち/負け/引き分け）

    // 個人メモ・お手付き（リクエストユーザー自身のデータ）
    private String myPersonalNotes;
    private Integer myOtetsukiCount;

    // メンティーのメモ・お手付き（メンター関係がある場合のみ設定）
    private String menteePersonalNotes;
    private Integer menteeOtetsukiCount;

    // 試合動画（同一自然キーの match_videos が存在する場合のみ設定。なければ null）
    private Video video;

    /**
     * 試合に紐付く動画情報（ネスト構造）
     *
     * 同一自然キー（試合日・試合番号・両選手）の {@code match_videos} レコードが
     * 存在する場合のみ {@link MatchDto#video} に設定される。動画なしの試合では null。
     * 再生・サムネイル表示と、編集・削除ボタンの表示判定（登録者本人チェック）に
     * 必要な項目のみを持つ。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Video {
        private Long id;
        private String videoUrl;
        private String youtubeVideoId;
        private String title;
        private Long createdBy;

        /**
         * 動画エンティティからネストDTOへ変換
         *
         * @param video 動画エンティティ
         * @return 動画ネストDTO（video が null の場合は null）
         */
        public static Video fromEntity(MatchVideo video) {
            if (video == null) {
                return null;
            }
            return Video.builder()
                    .id(video.getId())
                    .videoUrl(video.getVideoUrl())
                    .youtubeVideoId(video.getYoutubeVideoId())
                    .title(video.getTitle())
                    .createdBy(video.getCreatedBy())
                    .build();
        }
    }

    /**
     * エンティティからDTOへ変換
     * 注意: 選手名は別途設定する必要があります
     */
    public static MatchDto fromEntity(Match match) {
        if (match == null) {
            return null;
        }
        return MatchDto.builder()
                .id(match.getId())
                .matchDate(match.getMatchDate())
                .matchNumber(match.getMatchNumber())
                .player1Id(match.getPlayer1Id())
                .player2Id(match.getPlayer2Id())
                .winnerId(match.getWinnerId())
                .scoreDifference(match.getScoreDifference())
                .player1KyuRank(match.getPlayer1KyuRank())
                .player2KyuRank(match.getPlayer2KyuRank())
                .opponentName(match.getOpponentName())
                .venueId(match.getVenueId())
                .createdAt(match.getCreatedAt())
                .updatedAt(match.getUpdatedAt())
                .createdBy(match.getCreatedBy())
                .updatedBy(match.getUpdatedBy())
                .build();
    }

    /**
     * player1が勝者かどうか
     */
    public boolean isPlayer1Winner() {
        return winnerId != null && winnerId.equals(player1Id);
    }

    /**
     * player2が勝者かどうか
     */
    public boolean isPlayer2Winner() {
        return winnerId != null && winnerId.equals(player2Id);
    }
}
