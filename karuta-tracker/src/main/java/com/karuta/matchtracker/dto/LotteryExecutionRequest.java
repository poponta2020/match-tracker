package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手動抽選実行リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotteryExecutionRequest {

    @NotNull(message = "年は必須です")
    private Integer year;

    @NotNull(message = "月は必須です")
    @Min(value = 1, message = "月は1以上である必要があります")
    @Max(value = 12, message = "月は12以下である必要があります")
    private Integer month;
}
