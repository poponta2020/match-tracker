package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.LineChatReservation;

import java.time.ZoneOffset;

/**
 * ワーカーに渡す予約タスク DTO（line-chat-reserve-broadcast タスク3）。
 *
 * <p>グループの照合用識別情報（{@code chatRoomId}/{@code chatRoomName}）を同梱する。
 * 送信予定時刻は ISO 8601（{@code +09:00} オフセット明示）でシリアライズする。
 */
public record LineChatWorkerTaskDto(
        Long id,
        Long broadcastGroupId,
        Long sessionId,
        String status,
        String chatRoomId,
        String chatRoomName,
        String scheduledSendAt,
        String messageText) {

    /** JST（+09:00）固定オフセット。TIMESTAMP(JST wall-clock) を明示オフセット付き ISO 文字列にする。 */
    private static final ZoneOffset JST = ZoneOffset.ofHours(9);

    public static LineChatWorkerTaskDto fromEntity(LineChatReservation r, LineBroadcastGroup group) {
        return new LineChatWorkerTaskDto(
                r.getId(),
                r.getBroadcastGroupId(),
                r.getSessionId(),
                r.getStatus().name(),
                group != null ? group.getChatRoomId() : null,
                group != null ? group.getChatRoomName() : null,
                r.getScheduledSendAt().atOffset(JST).toString(),
                r.getMessageText());
    }
}
