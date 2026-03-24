package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineNotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LINE通知設定DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineNotificationPreferenceDto {

    private Long playerId;
    private Boolean lotteryResult;
    private Boolean waitlistOffer;
    private Boolean offerExpired;
    private Boolean matchPairing;
    private Boolean practiceReminder;
    private Boolean deadlineReminder;

    public static LineNotificationPreferenceDto fromEntity(LineNotificationPreference pref) {
        return LineNotificationPreferenceDto.builder()
                .playerId(pref.getPlayerId())
                .lotteryResult(pref.getLotteryResult())
                .waitlistOffer(pref.getWaitlistOffer())
                .offerExpired(pref.getOfferExpired())
                .matchPairing(pref.getMatchPairing())
                .practiceReminder(pref.getPracticeReminder())
                .deadlineReminder(pref.getDeadlineReminder())
                .build();
    }
}
