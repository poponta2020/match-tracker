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
    CANCELLED
}
