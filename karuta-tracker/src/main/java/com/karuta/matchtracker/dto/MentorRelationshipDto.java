package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.MentorRelationship;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorRelationshipDto {

    private Long id;
    private Long mentorId;
    private String mentorName;
    private Long menteeId;
    private String menteeName;
    private Long organizationId;
    private String organizationName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MentorRelationshipDto fromEntity(
            MentorRelationship entity, String mentorName, String menteeName, String organizationName) {
        return MentorRelationshipDto.builder()
                .id(entity.getId())
                .mentorId(entity.getMentorId())
                .mentorName(mentorName)
                .menteeId(entity.getMenteeId())
                .menteeName(menteeName)
                .organizationId(entity.getOrganizationId())
                .organizationName(organizationName)
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
