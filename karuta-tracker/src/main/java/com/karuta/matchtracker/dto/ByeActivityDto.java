package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.ByeActivity;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ByeActivityDto {

    private Long id;
    private LocalDate sessionDate;
    private Integer matchNumber;
    private Long playerId;
    private String playerName;
    private ActivityType activityType;
    private String activityTypeDisplay;
    private String freeText;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ByeActivityDto fromEntity(ByeActivity entity, String playerName) {
        return ByeActivityDto.builder()
                .id(entity.getId())
                .sessionDate(entity.getSessionDate())
                .matchNumber(entity.getMatchNumber())
                .playerId(entity.getPlayerId())
                .playerName(playerName)
                .activityType(entity.getActivityType())
                .activityTypeDisplay(entity.getActivityType().getDisplayName())
                .freeText(entity.getFreeText())
                .createdBy(entity.getCreatedBy())
                .updatedBy(entity.getUpdatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
