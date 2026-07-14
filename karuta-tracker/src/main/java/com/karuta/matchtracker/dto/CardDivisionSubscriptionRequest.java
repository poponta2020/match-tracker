package com.karuta.matchtracker.dto;

import lombok.*;

/**
 * 札分けリマインダー LINE 通知の購読トグル更新リクエスト。
 * per-(player, org) で {@code card_division_reminder} のみを部分更新する。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardDivisionSubscriptionRequest {
    private Long playerId;
    private Long organizationId;
    private boolean enabled;
}
