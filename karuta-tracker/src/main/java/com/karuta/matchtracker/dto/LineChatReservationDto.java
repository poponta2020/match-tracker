package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineChatReservation;
import com.karuta.matchtracker.entity.LineChatReservation.ReservationStatus;
import com.karuta.matchtracker.service.LineChatReservationService;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LINEチャット予約送信の予約DTO（管理画面の予約状況セクション。line-chat-reserve-broadcast タスク5）。
 */
@Data
@Builder
public class LineChatReservationDto {

    private Long id;
    private Long sessionId;
    private String status;
    private LocalDateTime scheduledSendAt;
    private String errorCode;
    private String errorMessage;
    private Integer attemptCount;
    private LocalDateTime updatedAt;
    /** FAILED かつ送信予定時刻まで再試行の安全マージンがある場合のみ true（管理画面の再試行ボタン活性条件）。 */
    private boolean retryable;

    public static LineChatReservationDto fromEntity(LineChatReservation entity, LocalDateTime now) {
        boolean retryable = entity.getStatus() == ReservationStatus.FAILED
                && now.isBefore(entity.getScheduledSendAt().minusMinutes(
                        LineChatReservationService.RESERVE_MARGIN_MINUTES));
        return LineChatReservationDto.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .scheduledSendAt(entity.getScheduledSendAt())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .attemptCount(entity.getAttemptCount())
                .updatedAt(entity.getUpdatedAt())
                .retryable(retryable)
                .build();
    }
}
