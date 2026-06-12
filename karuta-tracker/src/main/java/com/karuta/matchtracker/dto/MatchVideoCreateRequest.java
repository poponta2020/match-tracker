package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * 試合動画 登録リクエスト
 *
 * 対象試合（自然キー: 日付・試合番号・両選手）とYouTube動画URLを受け取る。
 * player1Id < player2Id の正規化はサービス層で行う。
 */
@Data
public class MatchVideoCreateRequest {

    @NotNull(message = "試合日は必須です")
    private LocalDate matchDate;

    @NotNull(message = "試合番号は必須です")
    @Min(value = 1, message = "試合番号は1以上で入力してください")
    private Integer matchNumber;

    @NotNull(message = "選手1のIDは必須です")
    private Long player1Id;

    @NotNull(message = "選手2のIDは必須です")
    private Long player2Id;

    @NotBlank(message = "動画URLは必須です")
    private String videoUrl;
}
