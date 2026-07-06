package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 伝助削除候補 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DensukeDeletionCandidateDto {
    private Long id;
    private LocalDate sessionDate;
    private Integer matchNumber;
    private String status;
    private LocalDateTime detectedAt;

    public static DensukeDeletionCandidateDto fromEntity(DensukeDeletionCandidate entity) {
        return DensukeDeletionCandidateDto.builder()
                .id(entity.getId())
                .sessionDate(entity.getSessionDate())
                .matchNumber(entity.getMatchNumber())
                .status(entity.getStatus().name())
                .detectedAt(entity.getDetectedAt())
                .build();
    }
}
