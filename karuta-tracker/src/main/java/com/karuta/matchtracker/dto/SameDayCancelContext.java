package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PracticeSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当日12:00以降キャンセル時の通知情報。
 * {@link AdminWaitlistNotificationData} に載せて呼び出し元に戻し、
 * 呼び出し元が (sessionId, playerId) 単位で集約してから
 * {@code handleSameDayCancelAndRecruitBatch} を呼び出す用途に使う。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SameDayCancelContext {

    private PracticeSession session;

    private Long playerId;

    private String playerName;

    private int matchNumber;
}
