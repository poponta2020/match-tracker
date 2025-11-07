package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Player;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 選手情報のDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDto {

    private Long id;
    private String name;
    private Player.Gender gender;
    private Player.DominantHand dominantHand;
    private Player.Role role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /**
     * エンティティからDTOへ変換
     */
    public static PlayerDto fromEntity(Player player) {
        if (player == null) {
            return null;
        }
        return PlayerDto.builder()
                .id(player.getId())
                .name(player.getName())
                .gender(player.getGender())
                .dominantHand(player.getDominantHand())
                .role(player.getRole())
                .createdAt(player.getCreatedAt())
                .updatedAt(player.getUpdatedAt())
                .deletedAt(player.getDeletedAt())
                .build();
    }

    /**
     * アクティブ（削除されていない）かどうか
     */
    public boolean isActive() {
        return deletedAt == null;
    }
}
