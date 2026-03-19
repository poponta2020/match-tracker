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
    private int participatedMatches; // 参加試合数（matchParticipantsに含まれる結果入力済み試合数）
    private int totalCompletedMatches; // その月の結果入力済み試合数（分母）
    private double rate; // 参加率（0.0〜1.0）
}
