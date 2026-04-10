package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorRelationshipCreateRequest {

    @NotNull(message = "メンターIDは必須です")
    private Long mentorId;

    @NotNull(message = "団体IDは必須です")
    private Long organizationId;
}
