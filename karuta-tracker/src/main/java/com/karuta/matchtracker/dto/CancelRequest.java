package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 参加キャンセルリクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelRequest {

    /**
     * 単一キャンセル用（後方互換性のため残す）
     */
    private Long participantId;

    /**
     * 複数キャンセル用
     */
    private List<Long> participantIds;

    /**
     * キャンセル理由コード（必須）
     */
    @NotBlank(message = "キャンセル理由は必須です")
    private String cancelReason;

    /**
     * キャンセル理由詳細（「その他」の場合）
     */
    private String cancelReasonDetail;

    /**
     * 有効なparticipantIdリストを取得する
     */
    public List<Long> getEffectiveParticipantIds() {
        if (participantIds != null && !participantIds.isEmpty()) {
            return participantIds;
        }
        if (participantId != null) {
            return List.of(participantId);
        }
        return List.of();
    }
}
