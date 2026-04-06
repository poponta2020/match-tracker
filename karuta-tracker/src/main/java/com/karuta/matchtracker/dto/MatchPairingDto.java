package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPairingDto {
    private Long id;
    private LocalDate sessionDate;
    private Integer matchNumber;
    private Long player1Id;
    private String player1Name;
    private Long player2Id;
    private String player2Name;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AutoMatchingResult.MatchHistory> recentMatches;

    // ロック関連（結果入力済み判定用）
    private boolean hasResult;
    private String winnerName;
    private Integer scoreDifference;
    private Long matchId;
}
