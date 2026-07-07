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
    private Long organizationId;
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

    // 隣室空き状況
    private AdjacentRoomStatusDto adjacentRoomStatus;

    // 隣室予約確認日時
    private LocalDateTime reservationConfirmedAt;

    // 抽選関連フィールド
    private Boolean lotteryExecuted;  // 抽選実行済みか
    private java.util.Map<Integer, MatchLotteryInfo> matchLotteryInfo;  // 試合番号ごとの抽選情報

    // 組み合わせ対象ステータス: 抽選なし運用では PENDING も組み合わせ対象（true）、
    // 抽選あり運用では WON のみ（false）。フロント側の参加者フィルタで使用する。
    private Boolean pairingIncludesPending;

    // 試合単位の定員到達状況（サマリーAPI のみで設定される）。
    // matchCapacityStatuses[i] が第 (i+1) 試合のステータス。長さは min(totalMatches, 9)。
    private List<CapacityStatus> matchCapacityStatuses;

    // 伝助側で削除が検知され、管理者の承認待ちの試合番号一覧（未承認のみ・空なら null/空リスト）。
    // 選手側UIで「伝助側で削除されました」バッジ・カレンダーの灰色×表示に使う。
    private List<Integer> densukeDeletionCandidateMatchNumbers;

    /**
     * 試合ごとの定員到達状況
     */
    public enum CapacityStatus {
        AVAILABLE,
        NEARLY_FULL,
        FULL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchParticipantInfo {
        // 管理者の手動繰り上げ（キャンセル待ち→当選）でステータス変更対象を一意に特定するため公開する。
        private Long participantId;
        // A-1: モーダルの初期選択を名前一致ではなくplayerId基準にするため公開する。
        // 同姓同名・改名でも取りこぼしなくWON/PENDINGを特定できる。
        private Long playerId;
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
                .organizationId(session.getOrganizationId())
                .createdBy(session.getCreatedBy())
                .updatedBy(session.getUpdatedBy())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .reservationConfirmedAt(session.getReservationConfirmedAt())
                .build();
    }
}
