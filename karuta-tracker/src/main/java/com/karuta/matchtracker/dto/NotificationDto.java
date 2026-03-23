package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通知DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {

    private Long id;
    private NotificationType type;
    private String title;
    private String message;
    private String referenceType;
    private Long referenceId;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static NotificationDto fromEntity(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
