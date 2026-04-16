package com.karuta.matchtracker.dto;

import com.karuta.matchtracker.entity.PracticeParticipant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理者向けキャンセル待ち状況通知のバッチ送信用データ。
 * 1試合分の通知情報を保持する。同一セッション×トリガー×プレイヤーの
 * 複数試合分をリストにまとめて送信する。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminWaitlistNotificationData {

    /** 発生イベント（例: "キャンセル", "オファー期限切れ", "オファー辞退", "降格"） */
    private String triggerAction;

    /** イベントを起こしたプレイヤーID */
    private Long triggerPlayerId;

    /** 対象セッションID */
    private Long sessionId;

    /** 対象試合番号 */
    private int matchNumber;

    /** 繰り上げオファーを送った参加者（null=繰り上げ対象なし） */
    private PracticeParticipant promotedParticipant;

    /**
     * 当日12:00以降キャンセル時に設定される通知コンテキスト。
     * 非nullの場合、このレコードは「通常の繰り上げ通知」ではなく
     * 「当日キャンセル発生通知／空き枠通知」用であることを表す。
     * 呼び出し元で (sessionId, playerId) 単位に集約し、
     * {@code handleSameDayCancelAndRecruitBatch} にまとめて渡す。
     */
    private SameDayCancelContext sameDayCancelContext;
}
