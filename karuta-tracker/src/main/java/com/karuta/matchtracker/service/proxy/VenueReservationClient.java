package com.karuta.matchtracker.service.proxy;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

/**
 * 会場別の HTTP クライアント契約。Phase 1 では {@code KADERU} のみ実装される。
 *
 * <p>各実装は Spring の {@code @Component} として登録され、
 * {@code com.karuta.matchtracker.service.proxy.VenueReservationProxyService}
 * (Task 6 で追加予定) が venue で dispatch する。</p>
 *
 * <p>本 interface は <strong>会場非依存</strong> な契約のみを規定する。
 * 会場固有の URL / DOM / hidden field 等はすべて実装クラスに閉じ込めること。</p>
 */
public interface VenueReservationClient {

    /**
     * この実装が担当する {@link VenueId}。Spring DI で {@code Map<VenueId, VenueReservationClient>}
     * を組み立てる際のキーとして使う。
     */
    VenueId venue();

    /**
     * ログイン → 申込トレイ画面までのナビゲーションを実行し、
     * トレイ画面の HTML を {@link ProxySession#setCachedTrayHtml(String)} に格納する。
     *
     * <p>Cookie や hidden field 等の会場別ステートは {@code session} 内に蓄積する。</p>
     *
     * @param session 対象セッション (cookies/hiddenFields は事前に空で生成済み)
     * @throws VenueReservationProxyException 会場側エラー / ログイン失敗 / タイムアウト等
     */
    void prepareReservationTray(ProxySession session) throws VenueReservationProxyException;

    /**
     * 任意の HTTP リクエストを会場サイトに中継する汎用エントリポイント。
     *
     * <p>セッション内 Cookie を attach した上で会場側にリクエストを送り、その応答を返す。
     * 完了検知や HTML 書き換えは呼び出し側 (Task 6/7 のサービス) が担当するため、本メソッドは
     * 純粋な HTTP リレーのみを行う。</p>
     *
     * <p>応答 Body のストリームは呼び出し側の責任で読み切る/閉じる必要がある。</p>
     *
     * @param session 対象セッション (cookies が attach される)
     * @param request 会場サイトへのリクエスト (URL は事前に {@code VenueConfig.baseUrl} 配下に解決済みであること)
     * @return 会場サイトからの応答
     * @throws VenueReservationProxyException I/O エラー / タイムアウト等
     */
    HttpResponse fetch(ProxySession session, HttpUriRequest request) throws VenueReservationProxyException;
}
