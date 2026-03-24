package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LINE連携状態DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineStatusDto {

    private boolean enabled;
    private boolean linked;
    private String friendAddUrl;
    private String qrCodeUrl;
}
