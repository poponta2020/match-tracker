package com.karuta.matchtracker.service.proxy.venue.kaderu;

import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import com.karuta.matchtracker.service.proxy.ProxySession;
import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.VenueReservationClient;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * かでる2・7 会場のリバースプロキシ用 HTTP クライアント実装。
 *
 * <p>Apache HttpClient 4.5 ベース。{@link #prepareReservationTray(ProxySession)} で
 * scripts/room-checker/open-reserve.js の Playwright 版 6 ステップを HTTP リクエストへ移植する。
 * 詳細は docs/features/venue-reservation-proxy/venues/kaderu.md §2 を参照。</p>
 *
 * <p>kaderu サイトはページ遷移を JavaScript の gotoPage(p) / showCalendar(y, m) /
 * clickDay(d) / setAppStatus(...) で行うが、内部的には PHP の index.php に対する form POST に
 * 等価。本実装はこの form 等価 POST を直接組み立てて送信する。
 * 細部のフィールド名や順序は実機 E2E (Task 4 完了条件の手動検証) で確定する。</p>
 */
@Component
@Slf4j
public class KaderuReservationClient implements VenueReservationClient {

    /** kaderu サイトのエントリポイント パス (baseUrl 直下からの相対) */
    static final String ENTRY_PATH = "/kaderu27/index.php";

    /** ページ識別子 (kaderu の gotoPage(p) と等価) */
    static final String PAGE_LOGIN = "login";
    static final String PAGE_MY_PAGE = "my_page";
    static final String PAGE_AVAILABILITY = "srch_sst";
    static final String PAGE_DATE_SELECT = "date_select";
    static final String PAGE_REQUEST_TRAY = "rsv_search";

    /** 部屋名 → 施設コード (open-reserve.js:24-29 と同等) */
    private static final Map<String, String> ROOM_CODES = Map.of(
            "すずらん", "001|018|01|2|2|0",
            "はまなす", "001|018|02|3|2|0",
            "あかなら", "001|017|02|3|2|0",
            "えぞまつ", "001|017|01|2|2|0"
    );

    /** スロット index → 時間帯文字列 (open-reserve.js:31-35 と同等) */
    private static final Map<Integer, String> TIME_RANGES = Map.of(
            0, "09001200",
            1, "13001600",
            2, "17002100"
    );

    /** Chrome 風 User-Agent。会場側に Bot 認定されないため最低限付ける。 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private final VenueReservationProxyConfig proxyConfig;
    private final KaderuVenueConfig venueConfig;
    private CloseableHttpClient httpClient;

    public KaderuReservationClient(VenueReservationProxyConfig proxyConfig,
                                   KaderuVenueConfig venueConfig) {
        this.proxyConfig = proxyConfig;
        this.venueConfig = venueConfig;
    }

    @PostConstruct
    void initHttpClient() {
        int timeoutMs = proxyConfig.getRequestTimeoutSeconds() * 1000;
        // CookieSpecs.STANDARD (RFC6265 lax) を明示指定。
        // デフォルトの DefaultCookieSpec は expires 属性付き Set-Cookie を NetscapeDraftSpec
        // (2桁年: "EEE, dd-MMM-yy HH:mm:ss z") で処理するため、kaderu が返す
        // expires=Wed, 19 Aug 2082 22:51:22 GMT のような4桁年・RFC1123 形式を拒否し、
        // セッション Cookie (ARKADERU27PC) を保存できず LOGIN_FAILED となる (Issue #559)。
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeoutMs)
                .setSocketTimeout(timeoutMs)
                .setConnectionRequestTimeout(timeoutMs)
                .setCookieSpec(CookieSpecs.STANDARD)
                .build();
        this.httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .setUserAgent(USER_AGENT)
                .build();
    }

    @PreDestroy
    void closeHttpClient() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                log.warn("Failed to close kaderu HTTP client", e);
            }
        }
    }

    @Override
    public VenueId venue() {
        return VenueId.KADERU;
    }

    @Override
    public void prepareReservationTray(ProxySession session) throws VenueReservationProxyException {
        if (session.getVenue() != VenueId.KADERU) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.SCRIPT_ERROR, VenueId.KADERU,
                    "session.venue mismatch: " + session.getVenue());
        }

        VenueReservationProxyConfig.VenueProperties props =
                proxyConfig.getVenues().get(KaderuVenueConfig.VENUE_KEY);
        if (props == null || props.getUserId() == null || props.getUserId().isBlank()
                || props.getPassword() == null || props.getPassword().isBlank()) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.LOGIN_FAILED, VenueId.KADERU,
                    "Kaderu credentials not configured (venue-reservation-proxy.venues.kaderu.user-id/password)");
        }

        String roomName = session.getRoomName();
        if (!ROOM_CODES.containsKey(roomName)) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.ROOM_NOT_FOUND, VenueId.KADERU,
                    "Unknown kaderu room name: " + roomName);
        }
        String facilityCode = ROOM_CODES.get(roomName);

        Integer slotIndex = session.getSlotIndex();
        if (!TIME_RANGES.containsKey(slotIndex)) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.NOT_AVAILABLE, VenueId.KADERU,
                    "Unsupported slot index: " + slotIndex);
        }
        String timeRange = TIME_RANGES.get(slotIndex);

        log.info("Preparing kaderu reservation tray: token={} room={} date={} slot={}",
                session.getToken(), roomName, session.getDate(), slotIndex);

        // ステップ 1: ログイン
        login(session, props.getUserId(), props.getPassword());

        // ステップ 2: マイページ遷移 (HTTP 的にはログイン直後のリダイレクト先と同等。
        //  POST 後の Set-Cookie / セッション確立を確認する位置付け。)
        navigateMyPage(session);

        // ステップ 3: 空き状況ページ遷移 (gotoPage('srch_sst') と等価)
        navigateAvailability(session, session.getDate());

        // ステップ 4: 月合わせ (必要時のみ)
        ensureMonth(session, session.getDate());

        // ステップ 5: 日付クリック (clickDay と等価)
        clickDay(session, session.getDate());

        // ステップ 6: スロット選択 + 申込トレイへ (setAppStatus + requestBtn と等価)
        String trayHtml = enterTray(session, facilityCode, session.getDate(), slotIndex, timeRange);
        session.setCachedTrayHtml(trayHtml);
        log.info("Kaderu reservation tray ready: token={} htmlLength={}",
                session.getToken(), trayHtml.length());
    }

    @Override
    public CloseableHttpResponse fetch(ProxySession session, HttpUriRequest request)
            throws VenueReservationProxyException {
        try {
            return httpClient.execute(request, buildContext(session));
        } catch (SocketTimeoutException | ConnectTimeoutException e) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.TIMEOUT, VenueId.KADERU,
                    "Kaderu fetch timed out: " + request.getURI(), e);
        } catch (IOException e) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.SCRIPT_ERROR, VenueId.KADERU,
                    "Kaderu fetch I/O error: " + request.getURI(), e);
        }
    }

    // ===== private steps =====

    private void login(ProxySession session, String userId, String password) {
        // ログインフォームへ初期 GET (PHPSESSID 取得 + 隠しフィールド取得を兼ねる)
        HttpGet initial = new HttpGet(venueConfig.baseUrl() + ENTRY_PATH);
        executeForHtml(session, initial, VenueReservationProxyException.TRAY_NAVIGATION_FAILED,
                "Failed to fetch kaderu entry page");

        // ログインフォーム POST。kaderu の gotoPage('my_page') 経由で到達するログイン画面の
        // <button name="loginBtn"> 押下と等価。
        List<NameValuePair> form = new ArrayList<>();
        form.add(new BasicNameValuePair("p", PAGE_MY_PAGE));
        form.add(new BasicNameValuePair("loginID", userId));
        form.add(new BasicNameValuePair("loginPwd", password));
        form.add(new BasicNameValuePair("loginBtn", "ログイン"));

        HttpPost post = newFormPost(venueConfig.baseUrl() + ENTRY_PATH, form);
        String body = executeForHtml(session, post, VenueReservationProxyException.LOGIN_FAILED,
                "Kaderu login HTTP error");

        // ログイン成功検知 (open-reserve.js:107-110 と同等。"マイページ" or "ログアウト" が含まれる)
        if (!body.contains("マイページ") && !body.contains("ログアウト")) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.LOGIN_FAILED, VenueId.KADERU,
                    "Kaderu login failed (response did not show マイページ/ログアウト)");
        }
    }

    private void navigateMyPage(ProxySession session) {
        List<NameValuePair> form = List.of(new BasicNameValuePair("p", PAGE_MY_PAGE));
        HttpPost post = newFormPost(venueConfig.baseUrl() + ENTRY_PATH, form);
        executeForHtml(session, post, VenueReservationProxyException.TRAY_NAVIGATION_FAILED,
                "Failed to navigate to kaderu my page");
    }

    private void navigateAvailability(ProxySession session, LocalDate date) {
        // gotoPage('srch_sst') 等価。月パラメータは UseYM=YYYYMM。
        String useYm = String.format(Locale.ROOT, "%04d%02d", date.getYear(), date.getMonthValue());
        List<NameValuePair> form = List.of(
                new BasicNameValuePair("p", PAGE_AVAILABILITY),
                new BasicNameValuePair("UseYM", useYm)
        );
        HttpPost post = newFormPost(venueConfig.baseUrl() + ENTRY_PATH, form);
        String body = executeForHtml(session, post, VenueReservationProxyException.TRAY_NAVIGATION_FAILED,
                "Failed to navigate to kaderu availability page");
        if (!body.contains(session.getRoomName())) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.ROOM_NOT_FOUND, VenueId.KADERU,
                    "Kaderu availability page does not contain room: " + session.getRoomName());
        }
    }

    private void ensureMonth(ProxySession session, LocalDate date) {
        // showCalendar(y, m) 等価。既に同じ月であれば現状の HTML には対象月が表示されている前提で
        // POST 自体を省略してもよいが、ステートレスに毎回送って同月を再確認する方が安全。
        List<NameValuePair> form = List.of(
                new BasicNameValuePair("p", PAGE_AVAILABILITY),
                new BasicNameValuePair("UseYear", String.valueOf(date.getYear())),
                new BasicNameValuePair("UseMonth", String.format(Locale.ROOT, "%02d", date.getMonthValue()))
        );
        HttpPost post = newFormPost(venueConfig.baseUrl() + ENTRY_PATH, form);
        executeForHtml(session, post, VenueReservationProxyException.TRAY_NAVIGATION_FAILED,
                "Failed to switch kaderu month");
    }

    private void clickDay(ProxySession session, LocalDate date) {
        // clickDay(d) 等価。POST p=date_select + UseDate=YYYYMMDD
        String useDate = String.format(Locale.ROOT, "%04d%02d%02d",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        List<NameValuePair> form = List.of(
                new BasicNameValuePair("p", PAGE_DATE_SELECT),
                new BasicNameValuePair("UseDate", useDate)
        );
        HttpPost post = newFormPost(venueConfig.baseUrl() + ENTRY_PATH, form);
        String body = executeForHtml(session, post, VenueReservationProxyException.TRAY_NAVIGATION_FAILED,
                "Failed to select kaderu date");
        if (!body.contains(session.getRoomName())) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.ROOM_NOT_FOUND, VenueId.KADERU,
                    "Kaderu date page does not contain room: " + session.getRoomName());
        }
    }

    private String enterTray(ProxySession session, String facilityCode,
                             LocalDate date, int slotIndex, String timeRange) {
        String dateFormatted = String.format(Locale.ROOT, "%04d/%02d/%02d",
                date.getYear(), date.getMonthValue(), date.getDayOfMonth());

        // setAppStatus 等価。スロット選択フォーム送信。
        List<NameValuePair> selectForm = new ArrayList<>();
        selectForm.add(new BasicNameValuePair("p", PAGE_DATE_SELECT));
        selectForm.add(new BasicNameValuePair("setAppStatus", "1"));
        selectForm.add(new BasicNameValuePair("facilityCode", facilityCode));
        selectForm.add(new BasicNameValuePair("useDate", dateFormatted));
        selectForm.add(new BasicNameValuePair("slotIndex", String.valueOf(slotIndex)));
        selectForm.add(new BasicNameValuePair("timeRange", timeRange));

        HttpPost selectPost = newFormPost(venueConfig.baseUrl() + ENTRY_PATH, selectForm);
        String afterSelect = executeForHtml(session, selectPost,
                VenueReservationProxyException.NOT_AVAILABLE,
                "Failed to select kaderu slot");
        if (containsAvailabilityError(afterSelect)) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.NOT_AVAILABLE, VenueId.KADERU,
                    "Kaderu slot is not available (likely already taken)");
        }

        // requestBtn 押下等価。申込トレイ画面への遷移。
        List<NameValuePair> trayForm = List.of(
                new BasicNameValuePair("p", PAGE_REQUEST_TRAY),
                new BasicNameValuePair("requestBtn", "申込トレイに入れる")
        );
        HttpPost trayPost = newFormPost(venueConfig.baseUrl() + ENTRY_PATH, trayForm);
        String trayHtml = executeForHtml(session, trayPost,
                VenueReservationProxyException.TRAY_NAVIGATION_FAILED,
                "Failed to enter kaderu request tray");
        if (!trayHtml.contains("申込トレイ")) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.TRAY_NAVIGATION_FAILED, VenueId.KADERU,
                    "Kaderu tray page did not contain expected marker");
        }
        return trayHtml;
    }

    private boolean containsAvailabilityError(String html) {
        // 暫定: 空きエラーの代表的な文言。実機検証で精度を上げる。
        return html != null && (
                html.contains("既に予約されています")
                || html.contains("予約できません")
                || html.contains("空きがありません")
        );
    }

    // ===== HTTP helpers =====

    private HttpClientContext buildContext(ProxySession session) {
        HttpClientContext ctx = HttpClientContext.create();
        ctx.setCookieStore(session.getCookies());
        return ctx;
    }

    private HttpPost newFormPost(String url, List<NameValuePair> form) {
        HttpPost post = new HttpPost(url);
        post.setHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        post.setHeader("Accept-Language", "ja,en-US;q=0.7,en;q=0.3");
        post.setHeader("Referer", url);
        post.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));
        return post;
    }

    /**
     * リクエストを送信し、応答 Body 文字列を返す。HTTP エラー / I/O エラーは
     * 指定した errorCode の {@link VenueReservationProxyException} に変換する。
     *
     * <p>応答本文の charset は {@code Content-Type} ヘッダの charset 属性を尊重する
     * ({@link VenueReservationProxyService#toResponseEntity} と同じ方針)。kaderu は現状
     * UTF-8 で安定しているが、将来 SJIS 等に切り替わった場合に「マイページ」「申込トレイ」等の
     * 文字列判定が静かに失敗するのを避けるための保険。</p>
     */
    private String executeForHtml(ProxySession session, HttpUriRequest request,
                                  String errorCodeOnFailure, String message) {
        try (CloseableHttpResponse response =
                     httpClient.execute(request, buildContext(session))) {
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null
                    ? ""
                    : EntityUtils.toString(entity, resolveCharset(entity));
            if (status >= 400) {
                throw new VenueReservationProxyException(
                        errorCodeOnFailure, VenueId.KADERU,
                        String.format("%s (HTTP %d, url=%s)", message, status, request.getURI()));
            }
            return body == null ? "" : body;
        } catch (SocketTimeoutException | ConnectTimeoutException e) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.TIMEOUT, VenueId.KADERU,
                    message + " (timeout)", e);
        } catch (IOException e) {
            throw new VenueReservationProxyException(
                    errorCodeOnFailure, VenueId.KADERU,
                    message + " (I/O: " + e.getMessage() + ")", e);
        }
    }

    /**
     * 応答 entity の Content-Type ヘッダから charset を抽出する。指定が無い場合は UTF-8 にフォールバック。
     */
    private static Charset resolveCharset(HttpEntity entity) {
        if (entity == null) {
            return StandardCharsets.UTF_8;
        }
        ContentType contentType = ContentType.get(entity);
        Charset charset = contentType == null ? null : contentType.getCharset();
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    /** テスト容易性のための setter。production では PostConstruct 経由で生成される。 */
    void setHttpClientForTesting(CloseableHttpClient client) {
        this.httpClient = client;
    }
}
