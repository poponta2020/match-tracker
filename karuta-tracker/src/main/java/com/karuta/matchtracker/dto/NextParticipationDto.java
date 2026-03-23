package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 次の参加予定練習DTO（ホーム画面用・軽量）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NextParticipationDto {
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String venueName;
    private List<Integer> matchNumbers;
    private boolean isToday;
    private boolean registered; // ログインユーザーが参加登録済みか
    private List<ParticipantInfo> participants;

    // 抽選関連
    private Map<Integer, ParticipantStatus> matchStatuses;  // 試合番号ごとの自分のステータス
    private Map<Integer, Integer> matchWaitlistNumbers;     // 試合番号ごとのキャンセル待ち番号
    private boolean hasLottery;  // 抽選が実行された練習か
    private boolean hasPendingOffer; // 未応答の繰り上げ通知があるか

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantInfo {
        private Long id;
        private String name;
        private Player.KyuRank kyuRank;
        private Player.DanRank danRank;
        private ParticipantStatus status;
    }
}
