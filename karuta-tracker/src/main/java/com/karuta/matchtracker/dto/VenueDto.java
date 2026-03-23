package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Venue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会場のDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueDto {

    private Long id;
    private String name;
    private Integer defaultMatchCount;
    private Integer capacity;
    private List<VenueMatchScheduleDto> schedules;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VenueDto fromEntity(Venue entity) {
        if (entity == null) {
            return null;
        }
        return VenueDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .defaultMatchCount(entity.getDefaultMatchCount())
                .capacity(entity.getCapacity())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
