package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 試合統計情報のDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchStatisticsDto {

    private Long playerId;
    private String playerName;
    private Long totalMatches;      // 総試合数
    private Long wins;               // 勝利数
    private Double winRate;          // 勝率

    /**
     * 勝率を計算して設定
     */
    public static MatchStatisticsDto create(Long playerId, String playerName, Long totalMatches, Long wins) {
        double winRate = totalMatches > 0 ? (wins * 100.0 / totalMatches) : 0.0;
        return MatchStatisticsDto.builder()
                .playerId(playerId)
                .playerName(playerName)
                .totalMatches(totalMatches)
                .wins(wins)
                .winRate(Math.round(winRate * 10.0) / 10.0)  // 小数点第1位まで
                .build();
    }
}
