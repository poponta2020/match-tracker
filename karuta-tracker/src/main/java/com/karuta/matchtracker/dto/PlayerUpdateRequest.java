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

    /**
     * 更新対象のフィールドのみエンティティに適用
     */
    public void applyTo(Player player) {
        if (name != null) {
            player.setName(name);
        }
        if (password != null) {
            player.setPassword(password);  // 実際はハッシュ化が必要
        }
        if (gender != null) {
            player.setGender(gender);
        }
        if (dominantHand != null) {
            player.setDominantHand(dominantHand);
        }
    }
}
