package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 級別統計DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankStatisticsDto {
    /**
     * 級位（null = 総計）
     */
    private String rank;

    /**
     * 総試合数
     */
    private Long total;

    /**
     * 勝利数
     */
    private Long wins;

    /**
     * 敗北数
     */
    private Long losses;

    /**
     * 勝率（パーセント）
     */
    private Integer winRate;

    /**
     * 統計データを作成
     */
    public static RankStatisticsDto create(String rank, Long total, Long wins) {
        Long losses = total - wins;
        Integer winRate = total > 0 ? Math.round((float) wins / total * 100) : 0;

        return RankStatisticsDto.builder()
                .rank(rank)
                .total(total)
                .wins(wins)
                .losses(losses)
                .winRate(winRate)
                .build();
    }
}
