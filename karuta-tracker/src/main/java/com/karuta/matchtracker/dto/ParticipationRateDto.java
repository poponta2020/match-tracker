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
    private int participatedMatches; // 参加試合数（PracticeParticipant登録数、抜け番含む）
    private int totalScheduledMatches; // その月の予定試合数（各セッションのtotalMatchesの合計）
    private double rate; // 参加率（0.0〜1.0）
}
