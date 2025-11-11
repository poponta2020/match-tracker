package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Player;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 選手登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerCreateRequest {

    @NotBlank(message = "選手名は必須です")
    @Size(max = 100, message = "選手名は100文字以内で入力してください")
    private String name;

    @NotBlank(message = "パスワードは必須です")
    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String password;

    @NotNull(message = "性別は必須です")
    private Player.Gender gender;

    @NotNull(message = "利き手は必須です")
    private Player.DominantHand dominantHand;

    private Player.DanRank danRank;
    private Player.KyuRank kyuRank;

    @Size(max = 200, message = "所属かるた会は200文字以内で入力してください")
    private String karutaClub;

    private String remarks;

    /**
     * リクエストからエンティティへ変換（デフォルトロール: PLAYER）
     */
    public Player toEntity() {
        return Player.builder()
                .name(name)
                .password(password)  // 実際はハッシュ化が必要
                .gender(gender)
                .dominantHand(dominantHand)
                .danRank(danRank)
                .kyuRank(kyuRank)
                .karutaClub(karutaClub)
                .remarks(remarks)
                .role(Player.Role.PLAYER)
                .build();
    }
}
