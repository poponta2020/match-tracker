package com.karuta.matchtracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCommentUpdateRequest {

    @NotBlank(message = "コメント内容は必須です")
    private String content;
}
