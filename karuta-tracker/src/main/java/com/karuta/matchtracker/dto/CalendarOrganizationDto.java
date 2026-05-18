package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * iCalフィード設定における所属団体エントリのDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CalendarOrganizationDto {

    /**
     * 所属団体ID
     */
    private Long organizationId;

    /**
     * 団体名（Organization.name）
     */
    private String organizationName;

    /**
     * カレンダー表示名のユーザー個別オーバーライド
     * 未設定の場合 null
     */
    private String displayName;
}
