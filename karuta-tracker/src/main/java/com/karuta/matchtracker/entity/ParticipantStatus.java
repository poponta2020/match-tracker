package com.karuta.matchtracker.entity;

/**
 * 練習参加者のステータス
 */
public enum ParticipantStatus {
    /** 参加希望（抽選前） */
    PENDING,
    /** 当選（参加確定） */
    WON,
    /** キャンセル待ち */
    WAITLISTED,
    /** 繰り上げ通知済み（応答待ち） */
    OFFERED,
    /** 繰り上げ辞退（明示的辞退または応答期限切れ） */
    DECLINED,
    /** 当選後キャンセル */
    CANCELLED,
    /** キャンセル待ち辞退 */
    WAITLIST_DECLINED;

    /**
     * 参加確定しているステータスかどうかを判定する。
     * 対戦組み合わせ候補や参加者数カウントに含めるべきステータスを示す。
     */
    public boolean isActive() {
        return this == WON || this == PENDING;
    }

    /**
     * キャンセル待ち系のステータスかどうかを判定する。
     * 抽選結果のキャンセル待ち一覧に含めるべきステータスを示す。
     */
    public boolean isWaitlisted() {
        return this == WAITLISTED || this == OFFERED || this == DECLINED || this == WAITLIST_DECLINED;
    }
}
