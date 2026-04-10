package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.MatchComment;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchCommentDto {

    private Long id;
    private Long matchId;
    private Long menteeId;
    private Long authorId;
    private String authorName;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MatchCommentDto fromEntity(MatchComment entity, String authorName) {
        return MatchCommentDto.builder()
                .id(entity.getId())
                .matchId(entity.getMatchId())
                .menteeId(entity.getMenteeId())
                .authorId(entity.getAuthorId())
                .authorName(authorName)
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
