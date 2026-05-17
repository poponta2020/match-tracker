package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * iCalフィード設定画面用のレスポンスDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedInfoDto {

    /**
     * フィードのフルURL
     */
    private String url;

    /**
     * プレイヤーの所属団体一覧（表示名カスタマイズUI用）
     */
    private List<CalendarOrganizationDto> organizations;
}
