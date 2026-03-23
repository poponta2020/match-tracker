package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Web Pushサブスクリプション登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscriptionRequest {

    @NotNull(message = "プレイヤーIDは必須です")
    private Long playerId;

    @NotBlank(message = "エンドポイントは必須です")
    private String endpoint;

    @NotBlank(message = "P256DHキーは必須です")
    private String p256dhKey;

    @NotBlank(message = "認証キーは必須です")
    private String authKey;

    private String userAgent;
}
