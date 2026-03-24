package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineNotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LINE通知スケジュール設定DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LineScheduleSettingDto {

    private LineNotificationType notificationType;
    private Boolean enabled;
    private List<Integer> daysBefore;
}
