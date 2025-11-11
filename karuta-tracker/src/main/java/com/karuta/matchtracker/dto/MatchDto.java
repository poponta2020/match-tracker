package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Match;
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
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;

    // フロントエンド用の追加フィールド
    private String opponentName;  // 対戦相手名（簡易表示用）
    private String result;         // 結果（勝ち/負け/引き分け）

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
                .opponentName(match.getOpponentName())
                .notes(match.getNotes())
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
