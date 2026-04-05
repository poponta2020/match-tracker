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
}
