package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.Organization;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationDto {

    private Long id;
    private String code;
    private String name;
    private String color;
    private DeadlineType deadlineType;

    public static OrganizationDto fromEntity(Organization organization) {
        if (organization == null) return null;
        return OrganizationDto.builder()
                .id(organization.getId())
                .code(organization.getCode())
                .name(organization.getName())
                .color(organization.getColor())
                .deadlineType(organization.getDeadlineType())
                .build();
    }
}
