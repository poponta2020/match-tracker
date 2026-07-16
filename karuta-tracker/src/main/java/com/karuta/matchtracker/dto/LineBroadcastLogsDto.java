package com.karuta.matchtracker.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 配信ログ一覧＋枯渇アラート状態（AC-9）。
 */
@Data
@Builder
public class LineBroadcastLogsDto {

    private List<LineBroadcastSendDto> logs;
    /** 直近に SKIPPED（枯渇/未設定）記録があるか＝要注意アラート */
    private boolean hasRecentSkip;
}
