package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineChannel;
import lombok.*;

/**
 * LINEチャネル管理画面用DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineChannelDto {
    private Long id;
    private String channelName;
    private String lineChannelId;
    private String status;
    private String basicId;
    private String friendAddUrl;
    private Integer monthlyMessageCount;
    private String assignedPlayerName;
    private Long assignedPlayerId;
    private String channelType;

    public static LineChannelDto fromEntity(LineChannel entity) {
        return LineChannelDto.builder()
            .id(entity.getId())
            .channelName(entity.getChannelName())
            .lineChannelId(entity.getLineChannelId())
            .status(entity.getStatus().name())
            .basicId(entity.getBasicId())
            .friendAddUrl(entity.getBasicId() != null
                ? "https://line.me/R/ti/p/" + entity.getBasicId() : null)
            .monthlyMessageCount(entity.getMonthlyMessageCount())
            .channelType(entity.getChannelType().name())
            .build();
    }
}
