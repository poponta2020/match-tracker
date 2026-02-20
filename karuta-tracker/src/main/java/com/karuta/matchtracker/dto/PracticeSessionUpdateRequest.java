package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 練習セッション更新リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionUpdateRequest {

    @NotNull(message = "練習日は必須です")
    private LocalDate sessionDate;

    @NotNull(message = "予定試合数は必須です")
    @Min(value = 1, message = "予定試合数は1以上で入力してください")
    @Max(value = 100, message = "予定試合数は100以下で入力してください")
    private Integer totalMatches;

    private Long venueId;

    @Size(max = 1000, message = "メモは1000文字以内で入力してください")
    private String notes;

    private LocalTime startTime;

    private LocalTime endTime;

    @Min(value = 1, message = "定員は1以上で入力してください")
    @Max(value = 100, message = "定員は100以下で入力してください")
    private Integer capacity;

    private List<Long> participantIds;
}
