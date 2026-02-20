package com.karuta.matchtracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * 会場更新リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueUpdateRequest {

    @NotBlank(message = "会場名は必須です")
    @Size(max = 200, message = "会場名は200文字以内で入力してください")
    private String name;

    @NotNull(message = "標準試合数は必須です")
    @Min(value = 1, message = "標準試合数は1以上で入力してください")
    @Max(value = 20, message = "標準試合数は20以下で入力してください")
    private Integer defaultMatchCount;

    @Valid
    @NotNull(message = "試合時間割は必須です")
    @Size(min = 1, message = "試合時間割を少なくとも1つ設定してください")
    private List<MatchScheduleRequest> schedules;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MatchScheduleRequest {

        @NotNull(message = "試合番号は必須です")
        @Min(value = 1, message = "試合番号は1以上で入力してください")
        private Integer matchNumber;

        @NotNull(message = "開始時刻は必須です")
        private LocalTime startTime;

        @NotNull(message = "終了時刻は必須です")
        private LocalTime endTime;
    }
}
