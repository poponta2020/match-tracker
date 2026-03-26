package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * LINEチャネル登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineChannelCreateRequest {
    private String channelName;

    @NotBlank(message = "チャネルIDは必須です")
    private String lineChannelId;

    @NotBlank(message = "チャネルシークレットは必須です")
    private String channelSecret;

    @NotBlank(message = "アクセストークンは必須です")
    private String channelAccessToken;

    private String basicId;
}
