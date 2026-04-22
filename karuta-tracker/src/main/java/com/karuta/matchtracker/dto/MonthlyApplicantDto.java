package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 月次参加希望者一覧の1件分DTO（優先選手指定UIで使用）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyApplicantDto {

    /** 選手ID */
    private Long playerId;

    /** 選手名 */
    private String name;
}
