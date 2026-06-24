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

    /**
     * 指導回数（指導試合で勝ち＝指導した側だった試合数）。
     * 総計と同じ期間・性別・利き手フィルタ適用後の値。級別には含めない。
     */
    private Long lessonGivenCount;

    /**
     * 被指導回数（指導試合で負け＝指導された側だった試合数）。
     * 総計と同じ期間・性別・利き手フィルタ適用後の値。級別には含めない。
     */
    private Long lessonReceivedCount;
}
