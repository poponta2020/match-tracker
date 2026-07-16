package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineBroadcastGroup;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全体LINE配信グループのDTO（管理画面一覧・詳細）。
 */
@Data
@Builder
public class LineBroadcastGroupDto {

    private Long id;
    private Long organizationId;
    private String organizationName;
    private String name;
    private boolean enabled;
    private Integer expectedRecipientCount;
    /** 割り当て済みbot数 */
    private int botCount;
    /** 配信可能bot数（有効＋グループID捕捉済み） */
    private int readyBotCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LineBroadcastGroupDto fromEntity(LineBroadcastGroup group, String organizationName,
                                                   int botCount, int readyBotCount) {
        return LineBroadcastGroupDto.builder()
                .id(group.getId())
                .organizationId(group.getOrganizationId())
                .organizationName(organizationName)
                .name(group.getName())
                .enabled(Boolean.TRUE.equals(group.getEnabled()))
                .expectedRecipientCount(group.getExpectedRecipientCount())
                .botCount(botCount)
                .readyBotCount(readyBotCount)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }
}
