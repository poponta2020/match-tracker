package com.karuta.matchtracker.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdatePlayerOrganizationsRequest {
    private List<Long> organizationIds;
}
