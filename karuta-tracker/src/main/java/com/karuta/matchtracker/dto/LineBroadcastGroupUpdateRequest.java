package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 全体LINE配信グループ更新リクエスト（有効化・名称・想定受信数）。
 * null のフィールドは更新しない（部分更新）。
 */
@Data
public class LineBroadcastGroupUpdateRequest {

    private String name;
    private Boolean enabled;
    @Min(1)
    private Integer expectedRecipientCount;
    /**
     * 想定受信数を「未設定」に戻すフラグ。true なら expectedRecipientCount を null に更新する
     * （送信時に実グループ人数APIで解決する挙動へ復帰させる）。
     * expectedRecipientCount の「省略（更新しない）」と「明示的な未設定化」を区別するために使う。
     */
    private Boolean clearExpectedRecipientCount;
}
