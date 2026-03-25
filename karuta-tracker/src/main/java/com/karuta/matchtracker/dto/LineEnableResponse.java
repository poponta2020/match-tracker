package com.karuta.matchtracker.dto;

import lombok.*;

/**
 * LINE通知有効化レスポンス
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineEnableResponse {
    private String friendAddUrl;
    private String linkingCode;
    private String codeExpiresAt;
    private String status;
}
