package com.karuta.matchtracker.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 日本標準時（JST）ベースの日時ユーティリティ。
 *
 * サーバーのデフォルトタイムゾーンに依存せず、
 * 常に Asia/Tokyo タイムゾーンで現在日時を取得する。
 */
public final class JstDateTimeUtil {

    public static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private JstDateTimeUtil() {
    }

    /**
     * JST での現在日時を取得する
     */
    public static LocalDateTime now() {
        return LocalDateTime.now(JST);
    }

    /**
     * JST での現在日付を取得する
     */
    public static LocalDate today() {
        return LocalDate.now(JST);
    }
}
