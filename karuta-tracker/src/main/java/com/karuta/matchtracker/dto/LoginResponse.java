package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ログインレスポンスDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private Long id;
    private String name;
    private Player.Gender gender;
    private Player.DominantHand dominantHand;
    private Player.DanRank danRank;
    private Player.KyuRank kyuRank;
    private String karutaClub;
    private Player.Role role;

    /**
     * エンティティからレスポンスへ変換
     */
    public static LoginResponse fromEntity(Player player) {
        if (player == null) {
            return null;
        }
        return LoginResponse.builder()
                .id(player.getId())
                .name(player.getName())
                .gender(player.getGender())
                .dominantHand(player.getDominantHand())
                .danRank(player.getDanRank())
                .kyuRank(player.getKyuRank())
                .karutaClub(player.getKarutaClub())
                .role(player.getRole())
                .build();
    }
}
