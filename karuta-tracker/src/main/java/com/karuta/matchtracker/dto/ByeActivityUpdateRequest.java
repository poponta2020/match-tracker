package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ActivityType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ByeActivityUpdateRequest {

    @NotNull
    private ActivityType activityType;

    private String freeText;
}
