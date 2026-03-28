package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PushNotificationPreference;
import lombok.*;

/**
 * Web Push通知設定DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushNotificationPreferenceDto {
    private Long playerId;
    private Long organizationId;
    private boolean enabled;
    private boolean lotteryResult;
    private boolean waitlistOffer;
    private boolean offerExpiring;
    private boolean offerExpired;
    private boolean channelReclaimWarning;
    private boolean densukeUnmatched;

    public static PushNotificationPreferenceDto fromEntity(PushNotificationPreference entity) {
        return PushNotificationPreferenceDto.builder()
            .playerId(entity.getPlayerId())
            .organizationId(entity.getOrganizationId())
            .enabled(entity.getEnabled())
            .lotteryResult(entity.getLotteryResult())
            .waitlistOffer(entity.getWaitlistOffer())
            .offerExpiring(entity.getOfferExpiring())
            .offerExpired(entity.getOfferExpired())
            .channelReclaimWarning(entity.getChannelReclaimWarning())
            .densukeUnmatched(entity.getDensukeUnmatched())
            .build();
    }

    /**
     * デフォルト設定を返す（Web Push未登録のプレイヤー用）
     */
    public static PushNotificationPreferenceDto defaultForPlayer(Long playerId) {
        return PushNotificationPreferenceDto.builder()
            .playerId(playerId)
            .enabled(false)
            .lotteryResult(true)
            .waitlistOffer(true)
            .offerExpiring(true)
            .offerExpired(true)
            .channelReclaimWarning(true)
            .densukeUnmatched(true)
            .build();
    }
}
