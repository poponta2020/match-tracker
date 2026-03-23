package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 参加キャンセルリクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelRequest {

    @NotNull(message = "参加者IDは必須です")
    private Long participantId;
}
