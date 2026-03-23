package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 抽選結果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotteryResultDto {

    private Long sessionId;
    private LocalDate sessionDate;
    private String venueName;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer capacity;
    private Map<Integer, MatchResult> matchResults;

    /**
     * 試合ごとの抽選結果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchResult {
        private int matchNumber;
        private int capacity;
        private boolean lotteryRequired;
        private List<ParticipantResult> winners;
        private List<ParticipantResult> waitlisted;
    }

    /**
     * 参加者の抽選結果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantResult {
        private Long playerId;
        private String playerName;
        private Player.KyuRank kyuRank;
        private Player.DanRank danRank;
        private ParticipantStatus status;
        private Integer waitlistNumber;
    }
}
