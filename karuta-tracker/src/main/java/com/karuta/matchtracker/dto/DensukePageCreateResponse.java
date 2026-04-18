package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 伝助ページ作成レスポンス DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DensukePageCreateResponse {
    /** 発行された伝助サイトコード */
    private String cd;
    /** 公開 URL (https://densuke.biz/list?cd=xxx) */
    private String url;
    /** 作成された練習日数 */
    private Integer createdDateCount;
    /** 作成された試合枠の総数 */
    private Integer createdMatchSlotCount;
}
