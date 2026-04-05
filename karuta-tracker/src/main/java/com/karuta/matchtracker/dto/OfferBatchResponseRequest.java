package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 繰り上げオファー一括応答リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferBatchResponseRequest {

    @NotNull(message = "セッションIDは必須です")
    private Long sessionId;

    @NotNull(message = "応答は必須です")
    private Boolean accept;
}
