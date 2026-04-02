package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LineNotificationType 通知ルーティングテスト")
class LineNotificationRoutingTest {

    @ParameterizedTest
    @EnumSource(value = LineNotificationType.class, names = {
        "LOTTERY_RESULT", "WAITLIST_OFFER", "OFFER_EXPIRED", "MATCH_PAIRING",
        "PRACTICE_REMINDER", "DEADLINE_REMINDER",
        "SAME_DAY_CONFIRMATION", "SAME_DAY_CANCEL", "SAME_DAY_VACANCY"
    })
    @DisplayName("選手向け通知はPLAYERチャネルにルーティングされる")
    void playerNotificationsShouldRouteToPlayerChannel(LineNotificationType type) {
        assertThat(type.getRequiredChannelType()).isEqualTo(ChannelType.PLAYER);
    }

    @ParameterizedTest
    @EnumSource(value = LineNotificationType.class, names = {
        "ADMIN_WAITLIST_UPDATE", "ADMIN_SAME_DAY_CONFIRMATION"
    })
    @DisplayName("管理者向け通知はADMINチャネルにルーティングされる")
    void adminNotificationsShouldRouteToAdminChannel(LineNotificationType type) {
        assertThat(type.getRequiredChannelType()).isEqualTo(ChannelType.ADMIN);
    }

    @Test
    @DisplayName("全通知種別がルーティング対象になっている")
    void allNotificationTypesShouldHaveRouting() {
        for (LineNotificationType type : LineNotificationType.values()) {
            ChannelType channelType = type.getRequiredChannelType();
            assertThat(channelType)
                .as("通知種別 %s にルーティング先が設定されている", type.name())
                .isNotNull();
        }
    }
}
