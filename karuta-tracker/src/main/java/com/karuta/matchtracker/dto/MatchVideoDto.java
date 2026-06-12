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
 * 試合動画のDTO
 *
 * 動画台帳の1レコードを表す。選手名は players テーブルから解決して設定する。
 * matchId / winnerId / scoreDifference は、同じ自然キーの試合結果（matches）が
 * 存在する場合のみ設定する（結果未入力＝組み合わせのみの段階では null）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchVideoDto {

    private Long id;
    private LocalDate matchDate;
    private Integer matchNumber;
    private Long player1Id;
    private String player1Name;
    private Long player2Id;
    private String player2Name;
    private String videoUrl;
    private String youtubeVideoId;
    private String title;
    private Long createdBy;
    private LocalDateTime createdAt;

    // 同一自然キーの試合結果（matches）が存在する場合のみ設定（未入力なら null）
    private Long matchId;
    private Long winnerId;
    private Integer scoreDifference;

    /**
     * エンティティからDTOへ変換
     *
     * @param video      動画エンティティ
     * @param player1Name 選手1の名前（呼び出し側で解決）
     * @param player2Name 選手2の名前（呼び出し側で解決）
     * @param match      同一自然キーの試合結果（存在しない場合は null）
     * @return 動画DTO（video が null の場合は null）
     */
    public static MatchVideoDto fromEntity(MatchVideo video, String player1Name, String player2Name, Match match) {
        if (video == null) {
            return null;
        }
        MatchVideoDto dto = MatchVideoDto.builder()
                .id(video.getId())
                .matchDate(video.getMatchDate())
                .matchNumber(video.getMatchNumber())
                .player1Id(video.getPlayer1Id())
                .player1Name(player1Name)
                .player2Id(video.getPlayer2Id())
                .player2Name(player2Name)
                .videoUrl(video.getVideoUrl())
                .youtubeVideoId(video.getYoutubeVideoId())
                .title(video.getTitle())
                .createdBy(video.getCreatedBy())
                .createdAt(video.getCreatedAt())
                .build();
        if (match != null) {
            dto.setMatchId(match.getId());
            dto.setWinnerId(match.getWinnerId());
            dto.setScoreDifference(match.getScoreDifference());
        }
        return dto;
    }
}
