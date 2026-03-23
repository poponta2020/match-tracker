package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ParticipantStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理者による参加者手動編集リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminEditParticipantsRequest {

    @NotNull(message = "セッションIDは必須です")
    private Long sessionId;

    @NotNull(message = "試合番号は必須です")
    private Integer matchNumber;

    /** 参加者の追加 */
    private List<AddParticipant> additions;

    /** ステータス変更 */
    private List<StatusChange> statusChanges;

    /** キャンセル待ち順番変更 */
    private List<WaitlistReorder> waitlistReorders;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddParticipant {
        private Long playerId;
        private ParticipantStatus status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatusChange {
        private Long participantId;
        private ParticipantStatus newStatus;
        private Integer waitlistNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WaitlistReorder {
        private Long participantId;
        private Integer newWaitlistNumber;
    }
}
