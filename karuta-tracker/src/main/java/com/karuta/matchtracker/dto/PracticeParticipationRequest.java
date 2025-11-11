package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 練習参加登録リクエスト（一括登録用）
 *
 * 選手が月単位で複数の練習日・試合に参加登録する際に使用
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeParticipationRequest {

    @NotNull(message = "選手IDは必須です")
    private Long playerId;

    @NotNull(message = "年は必須です")
    private Integer year;

    @NotNull(message = "月は必須です")
    @Min(value = 1, message = "月は1以上である必要があります")
    @Max(value = 12, message = "月は12以下である必要があります")
    private Integer month;

    @NotNull(message = "参加情報は必須です")
    private List<SessionMatchParticipation> participations;

    /**
     * セッション・試合参加情報
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionMatchParticipation {

        @NotNull(message = "セッションIDは必須です")
        private Long sessionId;

        @NotNull(message = "試合番号は必須です")
        @Min(value = 1, message = "試合番号は1以上である必要があります")
        @Max(value = 7, message = "試合番号は7以下である必要があります")
        private Integer matchNumber;
    }
}
