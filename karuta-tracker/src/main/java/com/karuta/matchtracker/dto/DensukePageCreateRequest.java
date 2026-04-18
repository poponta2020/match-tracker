package com.karuta.matchtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 伝助ページ作成リクエスト DTO
 */
@Data
public class DensukePageCreateRequest {

    @NotNull(message = "year は必須です")
    @Min(value = 2000, message = "year が不正です")
    @Max(value = 2100, message = "year が不正です")
    private Integer year;

    @NotNull(message = "month は必須です")
    @Min(value = 1, message = "month は 1〜12 で指定してください")
    @Max(value = 12, message = "month は 1〜12 で指定してください")
    private Integer month;

    @NotNull(message = "organizationId は必須です")
    private Long organizationId;

    @Valid
    private Overrides overrides;

    /**
     * 作成時のテンプレート値オーバーライド。任意指定。
     */
    @Data
    public static class Overrides {
        @Size(max = 200)
        private String title;

        private String description;

        @Size(max = 255)
        private String contactEmail;
    }
}
