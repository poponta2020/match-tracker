package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineNotificationPreference;
import lombok.*;

/**
 * LINE通知設定DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineNotificationPreferenceDto {
    private Long playerId;
    private Long organizationId;
    private boolean lotteryResult;
    private boolean waitlistOffer;
    private boolean offerExpired;
    private boolean matchPairing;
    private boolean practiceReminder;
    private boolean deadlineReminder;

    public static LineNotificationPreferenceDto fromEntity(LineNotificationPreference entity) {
        return LineNotificationPreferenceDto.builder()
            .playerId(entity.getPlayerId())
            .organizationId(entity.getOrganizationId())
            .lotteryResult(entity.getLotteryResult())
            .waitlistOffer(entity.getWaitlistOffer())
            .offerExpired(entity.getOfferExpired())
            .matchPairing(entity.getMatchPairing())
            .practiceReminder(entity.getPracticeReminder())
            .deadlineReminder(entity.getDeadlineReminder())
            .build();
    }
}
