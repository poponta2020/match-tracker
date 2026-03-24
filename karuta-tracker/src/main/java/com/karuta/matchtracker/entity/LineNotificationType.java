package com.karuta.matchtracker.entity;

/**
 * LINE通知の種別
 */
public enum LineNotificationType {
    /** 抽選結果 */
    LOTTERY_RESULT,
    /** キャンセル待ち連絡（ボタンテンプレート） */
    WAITLIST_OFFER,
    /** オファー期限切れ */
    OFFER_EXPIRED,
    /** 対戦組み合わせ */
    MATCH_PAIRING,
    /** 参加予定リマインダー */
    PRACTICE_REMINDER,
    /** 締め切りリマインダー */
    DEADLINE_REMINDER,
    /** Postback応答の確認メッセージ */
    POSTBACK_RESPONSE
}
