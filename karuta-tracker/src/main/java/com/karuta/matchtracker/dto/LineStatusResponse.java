package com.karuta.matchtracker.dto;

import lombok.*;

/**
 * LINE連携状態レスポンス
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineStatusResponse {
    private boolean enabled;
    private boolean linked;
    private String friendAddUrl;
}
