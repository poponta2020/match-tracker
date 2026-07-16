package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 全体LINE配信グループ作成リクエスト。
 */
@Data
public class LineBroadcastGroupCreateRequest {

    @NotNull
    private Long organizationId;

    @NotBlank
    private String name;

    /** 想定受信数（任意。未設定なら送信時に実グループ人数APIで解決） */
    private Integer expectedRecipientCount;
}
