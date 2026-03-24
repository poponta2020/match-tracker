package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LINEチャネル登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineChannelCreateRequest {

    private String channelName;

    @NotBlank
    private String lineChannelId;

    @NotBlank
    private String channelSecret;

    @NotBlank
    private String channelAccessToken;

    private String friendAddUrl;

    private String qrCodeUrl;
}
