package com.karuta.matchtracker.dto;

import lombok.*;

/**
 * ワンタイムコード再発行レスポンス
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineReissueCodeResponse {
    private String linkingCode;
    private String codeExpiresAt;
}
