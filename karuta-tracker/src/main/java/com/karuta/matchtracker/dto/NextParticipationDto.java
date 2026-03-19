package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.karuta.matchtracker.entity.Player;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantInfo {
        private Long id;
        private String name;
        private Player.KyuRank kyuRank;
        private Player.DanRank danRank;
    }
}
