package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参加率DTO（TOP3表示用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipationRateDto {
    private Long playerId;
    private String playerName;
    private int participatedMatches; // 有効参加(WON/PENDING)の試合数。抜け番(matchNumber=null・非ABSENT)も含むが、各セッションで totalMatches を上限にキャップ
    private int totalScheduledMatches; // その月の予定試合数（当日以前の各セッションのtotalMatchesの合計）
    private double rate; // 参加率（0.0〜1.0、100%を超えない）
}
