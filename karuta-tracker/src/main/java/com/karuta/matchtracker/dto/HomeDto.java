package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ホーム画面用統合DTO
 * 次の練習情報・参加率TOP3を1レスポンスにまとめる
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeDto {
    private NextParticipationDto nextPractice;
    private List<ParticipationRateDto> participationTop3;
    private ParticipationRateDto myParticipationRate;

    // 抽選関連
    private Long unreadNotificationCount;
    private Boolean hasPendingOffer;
}
