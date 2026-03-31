package com.karuta.matchtracker.entity;

/**
 * 抜け番の活動種別
 */
public enum ActivityType {
    READING("読み"),
    SOLO_PICK("一人取り"),
    OBSERVING("見学"),
    ASSIST_OBSERVING("見学対応"),
    OTHER("その他"),
    ABSENT("休み");

    private final String displayName;

    ActivityType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
