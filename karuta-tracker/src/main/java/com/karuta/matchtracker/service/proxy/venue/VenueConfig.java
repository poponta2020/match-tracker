package com.karuta.matchtracker.service.proxy.venue;

import com.karuta.matchtracker.service.proxy.VenueId;

import java.time.Duration;

/**
 * 会場別の静的メタ情報を提供する registry。
 *
 * <p>{@code VenueReservationHtmlRewriter} (Task 5) や
 * {@code VenueReservationProxyService} (Task 6) は本 interface を介して
 * 会場の baseUrl / displayName / セッションタイムアウトを参照する。</p>
 *
 * <p>各実装は Spring の {@code @Component} として登録される。</p>
 */
public interface VenueConfig {

    /** この実装が担当する {@link VenueId}。 */
    VenueId venue();

    /** 会場サイトのオリジン (例: {@code https://k2.p-kashikan.jp})。末尾スラッシュなし。 */
    String baseUrl();

    /** 表示名 (例: {@code かでる2・7})。プロキシ画面ヘッダーバナーに使用。 */
    String displayName();

    /**
     * 会場サイトの「エントリーポイント パス」(baseUrl 配下からの相対)。
     * プロキシ画面 (/view) に返すキャッシュHTMLが取得された URL を再構築するために使用する。
     *
     * <p>例: かでる2・7 では {@code /kaderu27/index.php}。これにより HTML 内の
     * {@code action="index.php"} や {@code src="script/default.js"} のような
     * 相対 URL が、ドメイン直下ではなく {@code /kaderu27/} 基準で解決される。</p>
     *
     * <p>デフォルトは {@code /} (ドメイン直下)。</p>
     */
    default String entryPath() {
        return "/";
    }

    /**
     * プロキシセッションのタイムアウト。デフォルトは要件定義書 §3.2.2 に従い 15 分。
     * 会場別に上書きしたい場合は実装側で override する。
     */
    default Duration sessionTimeout() {
        return Duration.ofMinutes(15);
    }
}
