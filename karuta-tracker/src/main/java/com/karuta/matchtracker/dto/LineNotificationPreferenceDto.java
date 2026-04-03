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
    private boolean adminWaitlistUpdate;
    private boolean sameDayConfirmation;
    private boolean sameDayCancel;
    private boolean sameDayVacancy;
    private boolean adminSameDayConfirmation;
    private boolean adminSameDayCancel;

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
            .adminWaitlistUpdate(entity.getAdminWaitlistUpdate())
            .sameDayConfirmation(entity.getSameDayConfirmation())
            .sameDayCancel(entity.getSameDayCancel())
            .sameDayVacancy(entity.getSameDayVacancy())
            .adminSameDayConfirmation(entity.getAdminSameDayConfirmation())
            .adminSameDayCancel(entity.getAdminSameDayCancel())
            .build();
    }
}
