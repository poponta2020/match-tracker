package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Player;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 選手情報更新リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerUpdateRequest {

    @Size(max = 100, message = "選手名は100文字以内で入力してください")
    private String name;

    @Size(min = 8, message = "パスワードは8文字以上で入力してください")
    private String password;

    private Player.Gender gender;
    private Player.DominantHand dominantHand;
    private Player.DanRank danRank;
    private Player.KyuRank kyuRank;

    @Size(max = 200, message = "所属かるた会は200文字以内で入力してください")
    private String karutaClub;

    private String remarks;

    /**
     * 更新対象のフィールドのみエンティティに適用
     *
     * パスワードは呼び出し元（サービス層）が {@code PasswordEncoder} でハッシュ化した値を渡す。
     * DTO に encoder を注入せず、引数で受け取ることでハッシュ化の漏れをコンパイルエラーにする。
     *
     * @param player          更新対象の選手
     * @param encodedPassword BCrypt でハッシュ化済みのパスワード。
     *                        パスワードを変更しない場合は null（{@code getPassword()} が null のとき）
     */
    public void applyTo(Player player, String encodedPassword) {
        if (name != null) {
            player.setName(name);
        }
        if (encodedPassword != null) {
            player.setPassword(encodedPassword);
            player.setRequirePasswordChange(false);
        }
        if (gender != null) {
            player.setGender(gender);
        }
        if (dominantHand != null) {
            player.setDominantHand(dominantHand);
        }
        if (danRank != null) {
            player.setDanRank(danRank);
        }
        if (kyuRank != null) {
            player.setKyuRank(kyuRank);
        }
        if (karutaClub != null) {
            player.setKarutaClub(karutaClub);
        }
        if (remarks != null) {
            player.setRemarks(remarks);
        }
    }
}
