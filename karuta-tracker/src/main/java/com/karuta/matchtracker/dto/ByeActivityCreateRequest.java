package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ActivityType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ByeActivityCreateRequest {

    @NotNull
    private LocalDate sessionDate;

    @NotNull
    private Integer matchNumber;

    @NotNull
    private Long playerId;

    @NotNull
    private ActivityType activityType;

    private String freeText;
}
