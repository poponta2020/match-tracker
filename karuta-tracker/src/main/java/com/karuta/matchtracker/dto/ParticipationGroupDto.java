package com.karuta.matchtracker.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipationGroupDto {
    private Long organizationId;
    private String organizationName;
    private List<ParticipationRateDto> top3;
    private ParticipationRateDto myRate;
}
