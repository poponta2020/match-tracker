package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 所属団体ごとのiCalフィード情報DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationFeedDto {

    private Long organizationId;

    private String organizationName;

    private String displayName;

    private String url;
}
