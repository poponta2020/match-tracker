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
 * 招待トークンを使った公開登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicRegisterRequest {

    @NotBlank(message = "招待トークンは必須です")
    private String token;

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

    /**
     * リクエストからエンティティへ変換
     *
     * パスワードは呼び出し元（サービス層）が {@code PasswordEncoder} でハッシュ化した値を渡す。
     * DTO に encoder を注入せず、引数で受け取ることでハッシュ化の漏れをコンパイルエラーにする。
     *
     * @param encodedPassword BCrypt でハッシュ化済みのパスワード
     */
    public Player toEntity(String encodedPassword) {
        return Player.builder()
                .name(name)
                .password(encodedPassword)
                .gender(gender)
                .dominantHand(dominantHand)
                .role(Player.Role.PLAYER)
                .build();
    }
}
