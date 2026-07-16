package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineBroadcastSend;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全体配信ログのDTO（管理画面の配信履歴）。
 */
@Data
@Builder
public class LineBroadcastSendDto {

    private Long id;
    private Long sessionId;
    private Long lineChannelId;
    private Integer recipientCount;
    private String status;
    private String errorMessage;
    private LocalDateTime sentAt;

    public static LineBroadcastSendDto fromEntity(LineBroadcastSend send) {
        return LineBroadcastSendDto.builder()
                .id(send.getId())
                .sessionId(send.getSessionId())
                .lineChannelId(send.getLineChannelId())
                .recipientCount(send.getRecipientCount())
                .status(send.getStatus() != null ? send.getStatus().name() : null)
                .errorMessage(send.getErrorMessage())
                .sentAt(send.getSentAt())
                .build();
    }
}
