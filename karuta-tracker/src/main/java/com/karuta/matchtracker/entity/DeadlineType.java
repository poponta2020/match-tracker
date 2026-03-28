package com.karuta.matchtracker.entity;

/**
 * 締め切りタイプ
 */
public enum DeadlineType {
    /** 当日時刻ベース（例: 練習当日12:00） */
    SAME_DAY,
    /** 月単位ベース（例: 前月N日） */
    MONTHLY
}
