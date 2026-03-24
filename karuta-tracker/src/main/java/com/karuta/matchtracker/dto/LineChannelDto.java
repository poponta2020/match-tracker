package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LINEチャネルDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineChannelDto {

    private Long id;
    private String channelName;
    private String lineChannelId;
    private LineChannelStatus status;
    private String friendAddUrl;
    private String qrCodeUrl;
    private Integer monthlyMessageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 割り当て情報（結合表示用）
    private Long assignedPlayerId;
    private String assignedPlayerName;

    public static LineChannelDto fromEntity(LineChannel channel) {
        return LineChannelDto.builder()
                .id(channel.getId())
                .channelName(channel.getChannelName())
                .lineChannelId(channel.getLineChannelId())
                .status(channel.getStatus())
                .friendAddUrl(channel.getFriendAddUrl())
                .qrCodeUrl(channel.getQrCodeUrl())
                .monthlyMessageCount(channel.getMonthlyMessageCount())
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .build();
    }
}
