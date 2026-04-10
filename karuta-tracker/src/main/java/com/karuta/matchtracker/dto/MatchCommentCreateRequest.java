package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCommentCreateRequest {

    @NotNull(message = "メンティーIDは必須です")
    private Long menteeId;

    @NotBlank(message = "コメント内容は必須です")
    private String content;
}
