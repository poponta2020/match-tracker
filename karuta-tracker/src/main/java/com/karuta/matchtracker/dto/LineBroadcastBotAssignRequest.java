package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 配信グループへの bot 割り当てリクエスト（未使用チャネルを GROUP に転用）。
 */
@Data
public class LineBroadcastBotAssignRequest {

    @NotNull
    private Long channelId;
}
