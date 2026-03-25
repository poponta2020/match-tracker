package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.InviteToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 招待トークンレスポンスDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InviteTokenResponse {

    private String token;
    private String type;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public static InviteTokenResponse fromEntity(InviteToken entity) {
        if (entity == null) return null;
        return InviteTokenResponse.builder()
                .token(entity.getToken())
                .type(entity.getType().name())
                .expiresAt(entity.getExpiresAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
