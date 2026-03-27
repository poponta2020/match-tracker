package com.karuta.matchtracker.dto;

import lombok.*;

/**
 * LINE一括送信結果レスポンス
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineSendResultResponse {
    private int sentPlayerCount;
    private int failedPlayerCount;
    private int skippedPlayerCount;
}
