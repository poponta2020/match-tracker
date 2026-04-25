package com.karuta.matchtracker.service.proxy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.client.CookieStore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * プロキシセッションのインメモリ状態。
 * 会場非依存のフィールドのみを保持し、会場固有のステートは {@link #hiddenFields} と {@link #cookies} に閉じ込める。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxySession {

    /** プロキシセッション識別子 (UUID) */
    private String token;

    /** 対象会場 */
    private VenueId venue;

    /** 紐づく practice_sessions.id */
    private Long practiceSessionId;

    /** 予約対象部屋名 (会場サイト上の表記) */
    private String roomName;

    /** 予約対象日 */
    private LocalDate date;

    /** 予約対象時間帯のスロットインデックス (0=午前 / 1=午後 / 2=夜間 等、会場別) */
    private int slotIndex;

    /** 会場サイトとのセッション維持用 Cookie (Apache HttpClient 4.x) */
    private CookieStore cookies;

    /**
     * 会場別 hidden field の最新値。
     * Phase 2 の東区民センター (ASP.NET WebForms) で {@code __VIEWSTATE} 等を保持するために使用する。
     * Phase 1 の Kaderu では未使用のまま空 Map で構わない。
     */
    private Map<String, String> hiddenFields;

    /** セッション生成時刻 */
    private Instant createdAt;

    /** 最終アクセス時刻 (タイムアウト判定の基準) */
    private Instant lastAccessedAt;

    /** 申込完了が検知済みか */
    private boolean completed;

    /** 申込トレイ画面の事前取得HTML (view エンドポイントが返す元データ) */
    private String cachedTrayHtml;
}
