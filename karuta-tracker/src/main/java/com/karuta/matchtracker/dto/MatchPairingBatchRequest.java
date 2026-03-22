package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchPairingBatchRequest {
    private List<MatchPairingCreateRequest> pairings;
    /** 抜け番（対戦が組まれなかった）選手のIDリスト */
    private List<Long> waitingPlayerIds;
}
