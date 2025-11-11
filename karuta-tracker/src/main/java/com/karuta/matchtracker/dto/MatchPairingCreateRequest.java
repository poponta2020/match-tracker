package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPairingCreateRequest {
    private LocalDate sessionDate;
    private Integer matchNumber;
    private Long player1Id;
    private Long player2Id;
}
