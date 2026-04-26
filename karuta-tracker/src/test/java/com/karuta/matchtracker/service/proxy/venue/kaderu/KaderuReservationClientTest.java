package com.karuta.matchtracker.service.proxy.venue.kaderu;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import com.karuta.matchtracker.service.proxy.ProxySession;
import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyException;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("KaderuReservationClient 単体テスト (WireMock)")
class KaderuReservationClientTest {

    private static final String ENTRY_PATH = "/kaderu27/index.php";
    private static final String ROOM_NAME = "はまなす";
    private static final LocalDate DATE = LocalDate.of(2026, 4, 12);
    private static final int SLOT_INDEX = 2;

    /** 申込トレイ画面の最低限のマーカーを含む HTML */
    private static final String TRAY_HTML =
            "<html><body><h1>申込トレイ</h1>"
            + "<table><tr><td>" + ROOM_NAME + "</td></tr></table>"
            + "<input type=\"submit\" name=\"applyBtn\" value=\"申込み\"/>"
            + "</body></html>";

    /** ログイン成功後のページ HTML (マイページマーカーを含む) */
    private static final String LOGGED_IN_HTML =
            "<html><body>マイページへようこそ。<a>ログアウト</a></body></html>";

    /** 部屋名を含む空き状況ページ HTML */
    private static final String AVAILABILITY_HTML =
            "<html><body><table>"
            + "<tr><td class=\"name\">" + ROOM_NAME + "</td><td>○</td></tr>"
            + "</table></body></html>";

    private WireMockServer wireMock;
    private VenueReservationProxyConfig proxyConfig;
    private KaderuVenueConfig venueConfig;
    private KaderuReservationClient client;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        proxyConfig = new VenueReservationProxyConfig();
        proxyConfig.setRequestTimeoutSeconds(5);

        VenueReservationProxyConfig.VenueProperties kaderu =
                new VenueReservationProxyConfig.VenueProperties();
        kaderu.setEnabled(true);
        kaderu.setBaseUrl("http://localhost:" + wireMock.port());
        kaderu.setUserId("testuser");
        kaderu.setPassword("testpass");
        proxyConfig.getVenues().put("kaderu", kaderu);

        venueConfig = new KaderuVenueConfig(proxyConfig);
        client = new KaderuReservationClient(proxyConfig, venueConfig);
        client.initHttpClient();
    }

    @AfterEach
    void tearDown() {
        client.closeHttpClient();
        wireMock.stop();
    }

    private ProxySession newSession() {
        return ProxySession.builder()
                .token(UUID.randomUUID().toString())
                .venue(VenueId.KADERU)
                .practiceSessionId(123L)
                .roomName(ROOM_NAME)
                .date(DATE)
                .slotIndex(SLOT_INDEX)
                .cookies(new BasicCookieStore())
                .hiddenFields(new HashMap<>())
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .completed(false)
                .build();
    }

    /**
     * 6 ステップを順次成功させるための WireMock スタブ。
     * 各 POST のフォームペイロードに含まれる p={page} を区別キーに使う。
     */
    private void stubSuccessfulFlow() {
        // ステップ 1a: 初期 GET (PHPSESSID 取得想定)
        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Set-Cookie", "PHPSESSID=mocksession; Path=/")
                        .withBody("<html><body>ログインページ</body></html>")));

        // ステップ 1b: ログイン POST
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*loginID=testuser.*"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(LOGGED_IN_HTML)));

        // ステップ 6 (最後): requestBtn を含む POST → トレイ画面 HTML
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*requestBtn=.*"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(TRAY_HTML)));

        // ステップ 6 (前段): setAppStatus を含む POST → 空きあり (エラー文言なし)
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*setAppStatus=1.*"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(AVAILABILITY_HTML)));

        // ステップ 5: clickDay 等価 (p=date_select 単独)
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching("p=date_select(?!.*setAppStatus).*"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(AVAILABILITY_HTML)));

        // ステップ 4: ensureMonth (UseYear/UseMonth 含む)
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*UseYear=.*"))
                .atPriority(2)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(AVAILABILITY_HTML)));

        // ステップ 3: 空き状況遷移 (UseYM= 含む。ensureMonth より優先度低めに設定)
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*UseYM=.*"))
                .atPriority(3)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(AVAILABILITY_HTML)));

        // ステップ 2: マイページ遷移 (p=my_page 単独。ログイン POST 後の汎用フォールバック)
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .atPriority(10)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(LOGGED_IN_HTML)));
    }

    @Test
    @DisplayName("正常系: 6ステップを順次実行して cachedTrayHtml にトレイ HTML がセットされる")
    void prepareReservationTray_success_setsCachedTrayHtml() {
        stubSuccessfulFlow();
        ProxySession session = newSession();

        client.prepareReservationTray(session);

        assertThat(session.getCachedTrayHtml()).isEqualTo(TRAY_HTML);
        assertThat(session.getCachedTrayHtml()).contains("申込トレイ");
    }

    @Test
    @DisplayName("正常系: PHPSESSID Cookie がセッションの CookieStore に保持される")
    void prepareReservationTray_success_persistsCookies() {
        stubSuccessfulFlow();
        ProxySession session = newSession();

        client.prepareReservationTray(session);

        boolean hasPhpSession = session.getCookies().getCookies().stream()
                .anyMatch(c -> "PHPSESSID".equals(c.getName()) && "mocksession".equals(c.getValue()));
        assertThat(hasPhpSession).isTrue();
    }

    @Test
    @DisplayName("回帰テスト (Issue #559): 4桁年・遠い未来の expires 属性付き Cookie も拒否されず保存される")
    void prepareReservationTray_persistsCookieWithFarFutureExpires() {
        // 実機 kaderu が返すのと同じ形式の Set-Cookie (RFC1123 4桁年 + Max-Age + secure + HttpOnly)。
        // Apache HttpClient のデフォルト Cookie spec (DefaultCookieSpec→NetscapeDraftSpec) は
        // 2桁年のみ受け付けるため、CookieSpecs.STANDARD への切替で初めて保存される。
        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Set-Cookie",
                                "ARKADERU27PC=k6pss2v5nbdeko4hi5nrpcrrd2; "
                                + "expires=Wed, 19 Aug 2082 22:51:22 GMT; "
                                + "Max-Age=1777204541; path=/; secure; HttpOnly")
                        .withBody("<html><body>ログインページ</body></html>")));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*loginID=testuser.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(LOGGED_IN_HTML)));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*requestBtn=.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(TRAY_HTML)));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*setAppStatus=1.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(AVAILABILITY_HTML)));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody(LOGGED_IN_HTML
                        + "<table><tr><td>" + ROOM_NAME + "</td></tr></table>")));

        ProxySession session = newSession();
        client.prepareReservationTray(session);

        boolean hasArKaderuCookie = session.getCookies().getCookies().stream()
                .anyMatch(c -> "ARKADERU27PC".equals(c.getName())
                        && "k6pss2v5nbdeko4hi5nrpcrrd2".equals(c.getValue()));
        assertThat(hasArKaderuCookie)
                .as("Cookie with 4-digit-year expires must be parsed and stored")
                .isTrue();
    }

    @Test
    @DisplayName("ログイン失敗 → LOGIN_FAILED")
    void prepareReservationTray_loginFailed_throwsLoginFailed() {
        // 初期 GET は成功
        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse().withStatus(200).withBody("login form")));
        // ログイン POST はマイページ/ログアウト文言を含まない応答
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("<html><body>ログインに失敗しました</body></html>")));

        ProxySession session = newSession();

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.LOGIN_FAILED);
                    assertThat(e.getVenue()).isEqualTo(VenueId.KADERU);
                });
    }

    @Test
    @DisplayName("空き状況ページに部屋名がない → ROOM_NOT_FOUND")
    void prepareReservationTray_roomNotFound_throwsRoomNotFound() {
        // ログインまでは成功、空き状況以降は部屋名のないHTMLを返す
        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse().withStatus(200).withBody("entry")));

        // ログイン (loginID 含む) → 成功
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*loginID=testuser.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(LOGGED_IN_HTML)));

        // それ以外の POST は部屋名を含まないHTMLを返す
        String htmlWithoutRoom = "<html><body>マイページ ログアウト 別の部屋</body></html>";
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody(htmlWithoutRoom)));

        ProxySession session = newSession();

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.ROOM_NOT_FOUND);
                    assertThat(e.getVenue()).isEqualTo(VenueId.KADERU);
                });
    }

    @Test
    @DisplayName("スロット選択時に空きエラー文言 → NOT_AVAILABLE")
    void prepareReservationTray_slotNotAvailable_throwsNotAvailable() {
        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse().withStatus(200).withBody("entry")));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*loginID=testuser.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(LOGGED_IN_HTML)));

        // setAppStatus POST は「既に予約されています」を含むHTMLを返す
        String slotErrorHtml =
                "<html><body>マイページ ログアウト " + ROOM_NAME
                + " 既に予約されています</body></html>";
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*setAppStatus=1.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(slotErrorHtml)));

        // それ以外の POST は部屋名を含む正常な空き状況 HTML
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody(AVAILABILITY_HTML
                        + "<span>マイページ ログアウト</span>")));

        ProxySession session = newSession();

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.NOT_AVAILABLE);
                    assertThat(e.getVenue()).isEqualTo(VenueId.KADERU);
                });
    }

    @Test
    @DisplayName("HTTP 500 が返ったら TRAY_NAVIGATION_FAILED (ログイン以外のステップで)")
    void prepareReservationTray_serverError_throwsTrayNavigationFailed() {
        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse().withStatus(200).withBody("entry")));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*loginID=testuser.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(LOGGED_IN_HTML)));

        // それ以降の POST はすべて 500
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .atPriority(10)
                .willReturn(aResponse().withStatus(500).withBody("server error")));

        ProxySession session = newSession();

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode())
                            .isEqualTo(VenueReservationProxyException.TRAY_NAVIGATION_FAILED);
                    assertThat(e.getVenue()).isEqualTo(VenueId.KADERU);
                });
    }

    @Test
    @DisplayName("リクエストタイムアウト → TIMEOUT")
    void prepareReservationTray_timeout_throwsTimeout() {
        // 初期 GET をレスポンス遅延でタイムアウトさせる
        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse()
                        .withFixedDelay((proxyConfig.getRequestTimeoutSeconds() + 5) * 1000)
                        .withStatus(200)
                        .withBody("late")));

        ProxySession session = newSession();

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.TIMEOUT);
                    assertThat(e.getVenue()).isEqualTo(VenueId.KADERU);
                });
    }

    @Test
    @DisplayName("認証情報が未設定 → LOGIN_FAILED で fast-fail")
    void prepareReservationTray_missingCredentials_throwsLoginFailed() {
        proxyConfig.getVenues().get("kaderu").setUserId("");
        proxyConfig.getVenues().get("kaderu").setPassword("");

        ProxySession session = newSession();

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.LOGIN_FAILED);
                });
    }

    @Test
    @DisplayName("不明な部屋名 → ROOM_NOT_FOUND で fast-fail (HTTP 通信なし)")
    void prepareReservationTray_unknownRoom_throwsRoomNotFound() {
        ProxySession session = ProxySession.builder()
                .token(UUID.randomUUID().toString())
                .venue(VenueId.KADERU)
                .roomName("存在しない部屋")
                .date(DATE)
                .slotIndex(SLOT_INDEX)
                .cookies(new BasicCookieStore())
                .hiddenFields(new HashMap<>())
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .build();

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.ROOM_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("session.venue が KADERU 以外 → SCRIPT_ERROR")
    void prepareReservationTray_wrongVenue_throwsScriptError() {
        ProxySession session = newSession();
        session.setVenue(VenueId.HIGASHI);

        assertThatThrownBy(() -> client.prepareReservationTray(session))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.SCRIPT_ERROR);
                });
    }

    // ===== fetch() のテスト =====

    @Test
    @DisplayName("fetch: 任意の GET が会場サイトに中継され、応答が返る")
    void fetch_get_relaysRequest() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/some/path"))
                .willReturn(aResponse().withStatus(200).withBody("hello")));

        ProxySession session = newSession();
        HttpGet request = new HttpGet(venueConfig.baseUrl() + "/some/path");

        try (CloseableHttpResponse response = client.fetch(session, request)) {
            assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
            String body = EntityUtils.toString(response.getEntity());
            assertThat(body).isEqualTo("hello");
        }
    }

    @Test
    @DisplayName("fetch: セッションの Cookie が会場サイトへのリクエストに付与される")
    void fetch_attachesSessionCookies() {
        wireMock.stubFor(any(urlPathEqualTo("/cookie-check"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        ProxySession session = newSession();
        BasicClientCookie cookie = new BasicClientCookie("PHPSESSID", "client-set-value");
        cookie.setDomain("localhost");
        cookie.setPath("/");
        session.getCookies().addCookie(cookie);

        HttpGet request = new HttpGet(venueConfig.baseUrl() + "/cookie-check");
        try (CloseableHttpResponse response = client.fetch(session, request)) {
            assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }

        wireMock.verify(WireMock.getRequestedFor(urlPathEqualTo("/cookie-check"))
                .withHeader("Cookie", matching(".*PHPSESSID=client-set-value.*")));
    }

    @Test
    @DisplayName("fetch: タイムアウト時は TIMEOUT 例外")
    void fetch_timeout_throwsTimeout() {
        wireMock.stubFor(get(urlPathEqualTo("/slow"))
                .willReturn(aResponse()
                        .withFixedDelay((proxyConfig.getRequestTimeoutSeconds() + 5) * 1000)
                        .withStatus(200)
                        .withBody("late")));

        ProxySession session = newSession();
        HttpGet request = new HttpGet(venueConfig.baseUrl() + "/slow");

        assertThatThrownBy(() -> client.fetch(session, request))
                .isInstanceOf(VenueReservationProxyException.class)
                .satisfies(ex -> {
                    VenueReservationProxyException e = (VenueReservationProxyException) ex;
                    assertThat(e.getErrorCode()).isEqualTo(VenueReservationProxyException.TIMEOUT);
                });
    }

    @Test
    @DisplayName("venue() は KADERU を返す")
    void venue_returnsKaderu() {
        assertThat(client.venue()).isEqualTo(VenueId.KADERU);
    }

    @Test
    @DisplayName("KaderuVenueConfig: baseUrl は config の値を返し、displayName は固定")
    void venueConfig_returnsConfiguredValues() {
        assertThat(venueConfig.venue()).isEqualTo(VenueId.KADERU);
        assertThat(venueConfig.baseUrl()).startsWith("http://localhost:");
        assertThat(venueConfig.displayName()).isEqualTo("かでる2・7");
        assertThat(venueConfig.sessionTimeout().toMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("KaderuVenueConfig: baseUrl 末尾の / は除去される")
    void venueConfig_stripsTrailingSlash() {
        proxyConfig.getVenues().get("kaderu").setBaseUrl("http://example.com/");
        assertThat(venueConfig.baseUrl()).isEqualTo("http://example.com");
    }

    @Test
    @DisplayName("KaderuVenueConfig: baseUrl が未設定なら DEFAULT_BASE_URL を返す")
    void venueConfig_defaultBaseUrl() {
        proxyConfig.getVenues().clear();
        assertThat(venueConfig.baseUrl()).isEqualTo("https://k2.p-kashikan.jp");
    }

    @Test
    @DisplayName("KaderuVenueConfig: entryPath は /kaderu27/index.php を返す (相対URL解決の基準)")
    void venueConfig_entryPathIsKaderu27IndexPhp() {
        assertThat(venueConfig.entryPath()).isEqualTo("/kaderu27/index.php");
    }

    // ===== Issue #562 関連: hidden field 抽出とログイン POST への転送 =====

    @Test
    @DisplayName("回帰テスト (Issue #562): ログインフォームの hidden field (op など) がログイン POST に含まれる")
    void prepareReservationTray_includesHiddenFieldsInLoginPost() {
        // 初期 GET 応答に hidden field op を含むログインフォームHTMLを返す
        String loginFormHtml =
                "<html><body>"
                + "<form name=\"form1\" method=\"post\" action=\"\">"
                + "  <input type=\"hidden\" name=\"op\" value=\"login\">"
                + "  <input type=\"text\" name=\"loginID\">"
                + "  <input type=\"password\" name=\"loginPwd\">"
                + "  <button name=\"loginBtn\">ログイン</button>"
                + "</form></body></html>";

        wireMock.stubFor(get(urlPathEqualTo(ENTRY_PATH))
                .willReturn(aResponse().withStatus(200).withBody(loginFormHtml)));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*loginID=testuser.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(LOGGED_IN_HTML)));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*requestBtn=.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(TRAY_HTML)));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching(".*setAppStatus=1.*"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(AVAILABILITY_HTML)));
        wireMock.stubFor(post(urlPathEqualTo(ENTRY_PATH))
                .atPriority(10)
                .willReturn(aResponse().withStatus(200).withBody(LOGGED_IN_HTML
                        + "<table><tr><td>" + ROOM_NAME + "</td></tr></table>")));

        ProxySession session = newSession();
        client.prepareReservationTray(session);

        // ログイン POST に op=login と loginID=testuser の両方が含まれている
        wireMock.verify(WireMock.postRequestedFor(urlPathEqualTo(ENTRY_PATH))
                .withRequestBody(matching("(?s).*op=login.*"))
                .withRequestBody(matching("(?s).*loginID=testuser.*")));
    }

    @Test
    @DisplayName("extractHiddenFields: 属性順 type/name/value のいずれが先でも hidden を抽出できる")
    void extractHiddenFields_variousAttributeOrders() {
        String html =
                "<input type='hidden' name='a' value='1'>"
                + "<input name='b' type='hidden' value='2'>"
                + "<input value='3' name='c' type='hidden'>"
                + "<input type=\"text\" name=\"d\" value=\"4\">"; // hidden ではないので除外

        List<NameValuePair> result = KaderuReservationClient.extractHiddenFields(html);

        assertThat(result).extracting(NameValuePair::getName)
                .containsExactly("a", "b", "c");
        assertThat(result).extracting(NameValuePair::getValue)
                .containsExactly("1", "2", "3");
    }

    @Test
    @DisplayName("extractHiddenFields: value 属性なしは空文字、name なしは無視、同名は最初のみ採用")
    void extractHiddenFields_edgeCases() {
        String html =
                "<input type='hidden' name='a'>"            // value なし → ""
                + "<input type='hidden' value='nope'>"        // name なし → 無視
                + "<input type='hidden' name='a' value='2'>"  // 同名 → 最初のみ採用
                + "<input type='hidden' name='b' value=''>";  // 明示的に空

        List<NameValuePair> result = KaderuReservationClient.extractHiddenFields(html);

        assertThat(result).extracting(NameValuePair::getName)
                .containsExactly("a", "b");
        assertThat(result).extracting(NameValuePair::getValue)
                .containsExactly("", "");
    }

    @Test
    @DisplayName("extractHiddenFields: null / 空文字 は空リストを返す")
    void extractHiddenFields_nullOrEmpty() {
        assertThat(KaderuReservationClient.extractHiddenFields(null)).isEmpty();
        assertThat(KaderuReservationClient.extractHiddenFields("")).isEmpty();
    }
}
