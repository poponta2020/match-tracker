package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * 簡易試合記録登録リクエスト
 * フロントエンドから対戦相手名と結果で登録
 */
@Data
public class MatchSimpleCreateRequest {

    @NotNull(message = "試合日は必須です")
    private LocalDate matchDate;

    @NotNull(message = "試合番号は必須です")
    @Min(value = 1, message = "試合番号は1以上で入力してください")
    private Integer matchNumber;

    @NotNull(message = "選手IDは必須です")
    private Long playerId;

    @NotBlank(message = "対戦相手名は必須です")
    @Size(max = 100, message = "対戦相手名は100文字以内で入力してください")
    private String opponentName;

    @NotBlank(message = "試合結果は必須です")
    @Pattern(regexp = "勝ち|負け|引き分け", message = "試合結果は「勝ち」「負け」「引き分け」のいずれかで入力してください")
    private String result;

    @NotNull(message = "札差は必須です")
    @Min(value = -25, message = "札差は-25以上で入力してください")
    @Max(value = 25, message = "札差は25以下で入力してください")
    private Integer scoreDifference;

    private String notes;
}
