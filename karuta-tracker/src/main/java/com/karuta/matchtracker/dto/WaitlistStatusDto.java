package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.ParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * キャンセル待ち状況DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistStatusDto {

    private List<WaitlistEntry> entries;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WaitlistEntry {
        private Long participantId;
        private Long sessionId;
        private LocalDate sessionDate;
        private String venueName;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer matchNumber;
        private Integer waitlistNumber;
        private ParticipantStatus status;
        private LocalDateTime offerDeadline;
    }
}
