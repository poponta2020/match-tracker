package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PracticeSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 練習日のDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PracticeSessionDto {

    private Long id;
    private LocalDate sessionDate;
    private Integer totalMatches;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * エンティティからDTOへ変換
     */
    public static PracticeSessionDto fromEntity(PracticeSession session) {
        if (session == null) {
            return null;
        }
        return PracticeSessionDto.builder()
                .id(session.getId())
                .sessionDate(session.getSessionDate())
                .totalMatches(session.getTotalMatches())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
