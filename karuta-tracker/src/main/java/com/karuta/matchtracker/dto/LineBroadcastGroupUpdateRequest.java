package com.karuta.matchtracker.dto;

import lombok.Data;

/**
 * 全体LINE配信グループ更新リクエスト（有効化・名称・想定受信数）。
 * null のフィールドは更新しない（部分更新）。
 */
@Data
public class LineBroadcastGroupUpdateRequest {

    private String name;
    private Boolean enabled;
    private Integer expectedRecipientCount;
}
