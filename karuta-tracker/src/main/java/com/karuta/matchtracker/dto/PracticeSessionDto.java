package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 練習日のDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionDto {

    private Long id;
    private LocalDate sessionDate;
    private Integer totalMatches;
    private Long venueId;
    private String venueName;
    private String notes;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer capacity;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // フロントエンド用の追加フィールド
    private List<PlayerDto> participants;  // 参加者リスト
    private Integer participantCount;      // 参加者数
    private Integer completedMatches;      // 実施済み試合数
    private java.util.Map<Integer, Integer> matchParticipantCounts;  // 試合番号ごとの参加人数
    private java.util.Map<Integer, List<MatchParticipantInfo>> matchParticipants;  // 試合番号ごとの参加者情報リスト
    private List<VenueMatchScheduleDto> venueSchedules;  // 会場の試合時間割

    // 抽選関連フィールド
    private Boolean lotteryExecuted;  // 抽選実行済みか
    private java.util.Map<Integer, MatchLotteryInfo> matchLotteryInfo;  // 試合番号ごとの抽選情報

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchParticipantInfo {
        private String name;
        private Player.KyuRank kyuRank;
        private Player.Role role;
        private ParticipantStatus status;
        private Integer waitlistNumber;
    }

    /**
     * 試合ごとの抽選情報
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchLotteryInfo {
        private int wonCount;          // 当選者数
        private int waitlistedCount;   // キャンセル待ち人数
        private boolean freeRegistrationOpen;  // 自由登録可能か
    }

    /**
     * エンティティからDTOへ変換
     */
    public static PracticeSessionDto fromEntity(PracticeSession session) {
        if (session == null) {
            return null;
        }
        return PracticeSessionDto.builder()
                .id(session.getId())
                .sessionDate(session.getSessionDate())
                .totalMatches(session.getTotalMatches())
                .venueId(session.getVenueId())
                .notes(session.getNotes())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .capacity(session.getCapacity())
                .createdBy(session.getCreatedBy())
                .updatedBy(session.getUpdatedBy())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
