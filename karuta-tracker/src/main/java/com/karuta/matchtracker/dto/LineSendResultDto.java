package com.karuta.matchtracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LINE送信結果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineSendResultDto {

    private int sentCount;
    private int failedCount;
    private int skippedCount;
}
