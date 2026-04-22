package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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

    private Long organizationId;

    /**
     * 抽選シード値（プレビュー時に生成され、確定時に同じ値を送る）
     */
    private Long seed;

    /**
     * 管理者が指定する優先選手のIDリスト（任意、デフォルト空リスト）
     */
    @Builder.Default
    private List<Long> priorityPlayerIds = new ArrayList<>();
}
