package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Player;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 選手情報の一括更新リクエスト
 *
 * 複数選手の players 列（性別・級・段位・かるた会）の更新と、
 * 所属練習会（player_organizations）の追加（追加のみ）をまとめて行う。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerBulkUpdateRequest {

    @NotEmpty(message = "更新対象の選手が指定されていません")
    @Valid
    private List<@NotNull(message = "更新対象に不正な(null)要素が含まれています") Item> updates;

    /**
     * 1選手分の更新内容。
     *
     * gender/kyuRank/danRank/karutaClub は null の項目を更新対象外として扱う
     * （PlayerUpdateRequest と同じく「指定された項目のみ反映」セマンティクス）。
     * 級↔段位の整合はフロントエンド側で算出するため、単体更新と同様にバックエンドでは検証しない。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {

        @NotNull(message = "playerId は必須です")
        private Long playerId;

        private Player.Gender gender;
        private Player.KyuRank kyuRank;
        private Player.DanRank danRank;

        @Size(max = 200, message = "所属かるた会は200文字以内で入力してください")
        private String karutaClub;

        /**
         * 追加する所属団体IDのリスト（追加のみ）。
         * 既に所属している団体は無視され、二重登録されない。
         */
        private List<Long> addOrganizationIds;
    }
}
