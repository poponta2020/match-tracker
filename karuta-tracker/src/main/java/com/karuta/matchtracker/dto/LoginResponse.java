package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private Long adminOrganizationId;
    private List<Long> organizationIds;
    private boolean firstLogin;
    private boolean requirePasswordChange;

    /**
     * サーバ発行の認証トークン（生トークン）
     * クライアントはこれを保存し、以降のリクエストの Authorization: Bearer に載せる
     */
    private String token;

    /**
     * エンティティからレスポンスへ変換
     *
     * @param token ログイン時に発行した生トークン
     */
    public static LoginResponse fromEntity(Player player, boolean firstLogin, List<Long> organizationIds,
                                           String token) {
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
                .adminOrganizationId(player.getAdminOrganizationId())
                .organizationIds(organizationIds)
                .firstLogin(firstLogin)
                .requirePasswordChange(Boolean.TRUE.equals(player.getRequirePasswordChange()))
                .token(token)
                .build();
    }
}
