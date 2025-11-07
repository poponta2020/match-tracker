package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PlayerProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 選手プロフィールのDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerProfileDto {

    private Long id;
    private Long playerId;
    private String playerName;
    private PlayerProfile.Grade grade;
    private PlayerProfile.Dan dan;
    private LocalDate validFrom;
    private LocalDate validTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * エンティティからDTOへ変換
     * 注意: 選手名は別途設定する必要があります
     */
    public static PlayerProfileDto fromEntity(PlayerProfile profile) {
        if (profile == null) {
            return null;
        }
        return PlayerProfileDto.builder()
                .id(profile.getId())
                .playerId(profile.getPlayerId())
                .grade(profile.getGrade())
                .dan(profile.getDan())
                .validFrom(profile.getValidFrom())
                .validTo(profile.getValidTo())
                .createdAt(profile.getCreatedAt())
                .updatedAt(profile.getUpdatedAt())
                .build();
    }

    /**
     * 現在有効なプロフィールかどうか
     */
    public boolean isCurrentlyValid() {
        LocalDate today = LocalDate.now();
        return validFrom != null && !validFrom.isAfter(today) &&
               (validTo == null || !validTo.isBefore(today));
    }

    /**
     * 特定の日付で有効なプロフィールかどうか
     */
    public boolean isValidOn(LocalDate date) {
        return validFrom != null && !validFrom.isAfter(date) &&
               (validTo == null || !validTo.isBefore(date));
    }
}
