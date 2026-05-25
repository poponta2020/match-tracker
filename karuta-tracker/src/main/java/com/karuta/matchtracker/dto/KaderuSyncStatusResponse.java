package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kaderu 同期ステータス API のレスポンス。
 * 進行中の PENDING イベントが存在すればその DTO を、なければ null を返す。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KaderuSyncStatusResponse {

    private KaderuSyncTriggerEventDto pendingEvent;
}
