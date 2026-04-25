package com.karuta.matchtracker.service.proxy.venue;

import com.karuta.matchtracker.service.proxy.VenueId;

/**
 * 会場別 HTML 書き換え strategy。
 *
 * <p>標準のコア書き換え (URL/バナー/注入スクリプト) は
 * {@code VenueReservationHtmlRewriter} が会場非依存に行う。
 * 本 interface の役割は、会場固有の事前/事後処理 (例: 特殊な hidden field の echo)、
 * および会場固有 JS フック (Phase 2 higashi の {@code __doPostBack} 等) を
 * 注入スクリプトに追記することの 2 点に限定する。</p>
 *
 * <p>各実装は Spring の {@code @Component} として登録される。</p>
 */
public interface VenueRewriteStrategy {

    /** この実装が担当する {@link VenueId}。 */
    VenueId venue();

    /**
     * 会場固有の HTML 事前/事後書き換え。
     *
     * <p>標準のコア書き換えは {@code VenueReservationHtmlRewriter} が行うため、
     * ここでは会場固有の書き換えのみ実装する (Kaderu では原則 no-op)。
     * 呼び出しタイミングはコア処理の前 (前処理) を想定。</p>
     *
     * @param html        書き換え前 HTML
     * @param proxyToken  プロキシセッショントークン
     * @return 書き換え後 HTML
     */
    String rewriteHtml(String html, String proxyToken);

    /**
     * コア注入スクリプトに追記する会場固有 JavaScript。
     *
     * <p>戻り値は {@code <script>} タグの中身 (タグ自体は含まない) としてインライン化される。
     * Kaderu では空文字でよい。Phase 2 higashi では {@code __doPostBack} のフックを返す予定。</p>
     */
    String injectScript();
}
