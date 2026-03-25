package com.karuta.matchtracker.dto;

import lombok.*;

import java.util.List;

/**
 * LINE通知スケジュール設定DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineScheduleSettingDto {
    private String notificationType;
    private boolean enabled;
    private List<Integer> daysBefore;
}
