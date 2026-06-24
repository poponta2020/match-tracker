package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Match;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 試合結果登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCreateRequest {

    @NotNull(message = "試合日は必須です")
    private LocalDate matchDate;

    @NotNull(message = "試合番号は必須です")
    @Min(value = 1, message = "試合番号は1以上で入力してください")
    private Integer matchNumber;

    @NotNull(message = "選手1のIDは必須です")
    private Long player1Id;

    @NotNull(message = "選手2のIDは必須です")
    private Long player2Id;

    @NotNull(message = "勝者のIDは必須です")
    private Long winnerId;

    // 指導試合（isLesson=true）では枚数差を持たないため null 許容。
    // 通常試合の枚数差未入力チェックはフロントエンドで実施する（既存仕様どおり）。
    @Min(value = -25, message = "点差は-25以上で入力してください")
    @Max(value = 25, message = "点差は25以下で入力してください")
    private Integer scoreDifference;

    /**
     * 指導試合フラグ（true=指導試合。winnerId=指導した側。scoreDifference は null 保存）
     */
    private Boolean isLesson;

    @NotNull(message = "登録者のIDは必須です")
    private Long createdBy;

    // 個人メモ・お手付き（任意）
    private String personalNotes;

    @Min(value = 0, message = "お手付き回数は0以上で入力してください")
    @Max(value = 20, message = "お手付き回数は20以下で入力してください")
    private Integer otetsukiCount;

    /**
     * リクエストからエンティティへ変換
     * player1Idとplayer2Idは自動的にソートされます
     */
    public Match toEntity() {
        // player1Id < player2Idを保証
        Long smallerId = Math.min(player1Id, player2Id);
        Long largerId = Math.max(player1Id, player2Id);

        boolean lesson = Boolean.TRUE.equals(isLesson);
        return Match.builder()
                .matchDate(matchDate)
                .matchNumber(matchNumber)
                .player1Id(smallerId)
                .player2Id(largerId)
                .winnerId(winnerId)
                // 指導試合では枚数差を保持しない（null）
                .scoreDifference(lesson ? null : scoreDifference)
                .isLesson(lesson)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
    }
}
