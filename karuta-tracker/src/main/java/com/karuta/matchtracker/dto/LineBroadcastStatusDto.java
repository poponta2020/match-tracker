package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.service.CardDivisionBroadcastService.BotQuota;
import com.karuta.matchtracker.service.CardDivisionBroadcastService.RotationStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 配信グループのローテーション稼働状況DTO（次配信bot・各bot残枠・当月残り可能回数・枯渇アラート）。
 */
@Data
@Builder
public class LineBroadcastStatusDto {

    /** 次に配信する bot（残枠最大の1体）。枯渇なら null */
    private Long nextBotChannelId;
    /** 会計に使った想定受信数（設定値または実測。特定不可なら -1） */
    private int expectedRecipientCount;
    /** 今月あと何回配信できるか */
    private int remainingBroadcasts;
    /** 全bot枯渇（これ以上配信不可）＝アラート状態（AC-9） */
    private boolean exhausted;
    private List<BotQuota> bots;

    public static LineBroadcastStatusDto fromRotationStatus(RotationStatus status) {
        boolean exhausted = status.nextBotChannelId() == null && status.remainingBroadcasts() == 0;
        return LineBroadcastStatusDto.builder()
                .nextBotChannelId(status.nextBotChannelId())
                .expectedRecipientCount(status.expectedRecipientCount())
                .remainingBroadcasts(status.remainingBroadcasts())
                .exhausted(exhausted)
                .bots(status.bots())
                .build();
    }
}
