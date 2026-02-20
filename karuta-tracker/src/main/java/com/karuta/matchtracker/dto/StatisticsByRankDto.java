package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 級別統計レスポンスDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsByRankDto {
    /**
     * 総計統計
     */
    private RankStatisticsDto total;

    /**
     * 級別統計マップ（キー: 級名、値: 統計）
     */
    private Map<String, RankStatisticsDto> byRank;
}
