package com.karuta.matchtracker.service.proxy.venue;

import com.karuta.matchtracker.service.proxy.VenueId;

/**
 * 会場別の申込完了判定 strategy。
 *
 * <p>会場サイトからのレスポンスが「申込完了画面」に到達したことを判定する。
 * URL / Location ヘッダ / HTML 本文の任意の組み合わせで判定可能。</p>
 *
 * <p>判定の真陽性条件は会場別仕様書 (例: {@code venues/kaderu.md} §5) に従う。
 * 会場非依存コア ({@code VenueReservationCompletionDetector}) が venue で dispatch して
 * 本 strategy に判定を委譲する。</p>
 *
 * <p>各実装は Spring の {@code @Component} として登録され、
 * {@code Map<VenueId, VenueCompletionStrategy>} の構築に利用される。</p>
 */
public interface VenueCompletionStrategy {

    /** この実装が担当する {@link VenueId}。 */
    VenueId venue();

    /**
     * レスポンスが申込完了画面に到達したかを判定する。
     *
     * @param requestUrl       会場サイトに送ったリクエスト URL (null 可)
     * @param responseLocation 会場サイトの応答 {@code Location} ヘッダ値 (null 可)
     * @param responseBody     会場サイトの応答 HTML 本文 (null 可)
     * @return 完了画面に到達したと判定すれば {@code true}
     */
    boolean isCompletion(String requestUrl, String responseLocation, String responseBody);
}
