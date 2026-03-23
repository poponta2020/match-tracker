package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 繰り上げオファーへの応答リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferResponseRequest {

    @NotNull(message = "参加者IDは必須です")
    private Long participantId;

    @NotNull(message = "応答は必須です")
    private Boolean accept;
}
