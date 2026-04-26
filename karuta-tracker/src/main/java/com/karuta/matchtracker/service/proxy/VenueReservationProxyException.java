package com.karuta.matchtracker.service.proxy;

import lombok.Getter;

/**
 * venue-reservation-proxy で会場サイトとのやり取り中に発生したエラーを表す。
 *
 * <p>{@link #errorCode} は要件定義書 §3.2.5 / §4.1.1 で規定された共通エラーコード、
 * および会場別仕様書 (例 venues/kaderu.md §3) で定義された会場固有コードを保持する。</p>
 */
@Getter
public class VenueReservationProxyException extends RuntimeException {

    /** 共通: 未対応の会場が指定された */
    public static final String VENUE_NOT_SUPPORTED = "VENUE_NOT_SUPPORTED";
    /** 共通: リクエストの会場/日付/部屋名が紐づく practice_sessions と一致しない */
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    /** 共通: 会場サイトへのログインに失敗した */
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    /** 共通: スロットが既に埋まっている等で確保できない */
    public static final String NOT_AVAILABLE = "NOT_AVAILABLE";
    /** 共通: 部屋名が会場サイトの DOM に見つからない */
    public static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    /** 共通: HTTP リクエストがタイムアウトした */
    public static final String TIMEOUT = "TIMEOUT";
    /** 共通: 想定外のスクリプト/ロジックエラー */
    public static final String SCRIPT_ERROR = "SCRIPT_ERROR";
    /** Kaderu: 申込トレイへの遷移ステップで予期しない応答 */
    public static final String TRAY_NAVIGATION_FAILED = "TRAY_NAVIGATION_FAILED";

    private final String errorCode;
    private final VenueId venue;

    public VenueReservationProxyException(String errorCode, VenueId venue, String message) {
        super(message);
        this.errorCode = errorCode;
        this.venue = venue;
    }

    public VenueReservationProxyException(String errorCode, VenueId venue, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.venue = venue;
    }
}
