package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.VenueMatchSchedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 会場試合時間割のDTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VenueMatchScheduleDto {

    private Long id;
    private Integer matchNumber;
    private LocalTime startTime;
    private LocalTime endTime;

    public static VenueMatchScheduleDto fromEntity(VenueMatchSchedule entity) {
        if (entity == null) {
            return null;
        }
        return VenueMatchScheduleDto.builder()
                .id(entity.getId())
                .matchNumber(entity.getMatchNumber())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .build();
    }
}
