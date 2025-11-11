package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AutoMatchingResult {
    private List<PairingSuggestion> pairings;
    private List<PlayerInfo> waitingPlayers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PairingSuggestion {
        private Long player1Id;
        private String player1Name;
        private Long player2Id;
        private String player2Name;
        private Double score;
        private List<MatchHistory> recentMatches;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchHistory {
        private LocalDate matchDate;
        private Integer daysAgo;
        private Integer matchNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerInfo {
        private Long id;
        private String name;
    }
}
