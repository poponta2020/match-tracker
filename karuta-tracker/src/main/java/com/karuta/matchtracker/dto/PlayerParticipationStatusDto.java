package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 選手の月間参加状況（抽選ステータス付き）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerParticipationStatusDto {

    /**
     * セッションIDごとの参加情報
     */
    private Map<Long, List<MatchParticipation>> participations;

    /**
     * セッションIDごとに抽選実行済みかどうか
     */
    private Map<Long, Boolean> lotteryExecuted;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchParticipation {
        private Long participantId;
        private Integer matchNumber;
        private ParticipantStatus status;
        private Integer waitlistNumber;
    }
}
