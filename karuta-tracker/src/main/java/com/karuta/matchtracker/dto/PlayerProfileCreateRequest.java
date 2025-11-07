package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PlayerProfile;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 選手プロフィール登録リクエスト
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerProfileCreateRequest {

    @NotNull(message = "選手IDは必須です")
    private Long playerId;

    @NotNull(message = "級は必須です")
    private PlayerProfile.Grade grade;

    @NotNull(message = "段は必須です")
    private PlayerProfile.Dan dan;

    @NotNull(message = "有効開始日は必須です")
    private LocalDate validFrom;

    /**
     * リクエストからエンティティへ変換
     * validToはnull（無期限）
     */
    public PlayerProfile toEntity() {
        return PlayerProfile.builder()
                .playerId(playerId)
                .grade(grade)
                .dan(dan)
                .validFrom(validFrom)
                .validTo(null)  // 新規作成時は無期限
                .build();
    }
}
