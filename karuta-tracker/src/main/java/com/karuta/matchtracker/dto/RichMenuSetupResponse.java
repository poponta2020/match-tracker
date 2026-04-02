package com.karuta.matchtracker.dto;

import lombok.*;
import java.util.List;

/**
 * リッチメニュー一括設定結果レスポンス
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RichMenuSetupResponse {
    private int successCount;
    private int failureCount;
    private List<String> failures;
}
