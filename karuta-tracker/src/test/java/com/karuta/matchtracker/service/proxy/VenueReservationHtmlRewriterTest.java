package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.service.proxy.venue.VenueConfig;
import com.karuta.matchtracker.service.proxy.venue.VenueRewriteStrategy;
import com.karuta.matchtracker.service.proxy.venue.kaderu.KaderuRewriteStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VenueReservationHtmlRewriter 単体テスト")
class VenueReservationHtmlRewriterTest {

    private static final String BASE_URL = "https://k2.p-kashikan.jp";
    private static final String DISPLAY_NAME = "かでる2・7";
    private static final String PROXY_PREFIX = VenueReservationHtmlRewriter.PROXY_PREFIX;

    private VenueReservationHtmlRewriter rewriter;
    private VenueConfig venueConfig;
    private VenueRewriteStrategy noopStrategy;
    private ProxySession session;
    private String token;

    @BeforeEach
    void setUp() {
        rewriter = new VenueReservationHtmlRewriter();
        venueConfig = new VenueConfig() {
            @Override public VenueId venue() { return VenueId.KADERU; }
            @Override public String baseUrl() { return BASE_URL; }
            @Override public String displayName() { return DISPLAY_NAME; }
            @Override public Duration sessionTimeout() { return Duration.ofMinutes(15); }
        };
        noopStrategy = new VenueRewriteStrategy() {
            @Override public VenueId venue() { return VenueId.KADERU; }
            @Override public String rewriteHtml(String html, String proxyToken) { return html; }
            @Override public String injectScript() { return ""; }
        };

        token = UUID.randomUUID().toString();
        session = ProxySession.builder()
                .token(token)
                .venue(VenueId.KADERU)
                .practiceSessionId(123L)
                .roomName("はまなす")
                .date(LocalDate.of(2026, 4, 12))
                .slotIndex(2)
                .hiddenFields(new HashMap<>())
                .build();
    }

    private String wrap(String body) {
        return "<html><head></head><body>" + body + "</body></html>";
    }

    private Document rewritten(String html) {
        return Jsoup.parse(rewriter.rewrite(html, session, venueConfig, noopStrategy));
    }

    private String expectedProxy(String path) {
        String base = PROXY_PREFIX + path;
        String sep = path.indexOf('?') >= 0 ? "&" : "?";
        return base + sep + "token=" + token;
    }

    @Nested
    @DisplayName("URL 書き換え (Jsoup DOM)")
    class UrlRewriting {

        @Test
        @DisplayName("<a href> 絶対URL (会場サイト) → プロキシURLに書き換え")
        void anchorAbsolute() {
            Document out = rewritten(wrap("<a id='x' href='" + BASE_URL + "/foo/bar'>link</a>"));
            assertThat(out.selectFirst("#x").attr("href"))
                    .isEqualTo(expectedProxy("/foo/bar"));
        }

        @Test
        @DisplayName("<a href> 相対URL (パス) → プロキシURLに書き換え")
        void anchorRelative() {
            Document out = rewritten(wrap("<a id='x' href='/foo/bar'>link</a>"));
            assertThat(out.selectFirst("#x").attr("href"))
                    .isEqualTo(expectedProxy("/foo/bar"));
        }

        @Test
        @DisplayName("<a href> 既にクエリ文字列がある場合は &token=... を付ける")
        void anchorWithQuery() {
            Document out = rewritten(wrap("<a id='x' href='/foo?p=login'>link</a>"));
            assertThat(out.selectFirst("#x").attr("href"))
                    .isEqualTo(PROXY_PREFIX + "/foo?p=login&token=" + token);
        }

        @Test
        @DisplayName("<a href> 会場外サイトURLは書き換えない")
        void anchorExternal() {
            Document out = rewritten(wrap("<a id='x' href='https://other-site.com/foo'>link</a>"));
            assertThat(out.selectFirst("#x").attr("href"))
                    .isEqualTo("https://other-site.com/foo");
        }

        @Test
        @DisplayName("<form action> POST フォームのアクションを書き換え")
        void formAction() {
            Document out = rewritten(wrap(
                    "<form id='f' action='" + BASE_URL + "/submit' method='POST'></form>"));
            assertThat(out.selectFirst("#f").attr("action"))
                    .isEqualTo(expectedProxy("/submit"));
        }

        @Test
        @DisplayName("<img src> / <link href> / <script src> を書き換え")
        void mediaAndAssetTags() {
            String html = wrap(
                    "<img id='i' src='" + BASE_URL + "/img.png'>"
                  + "<link id='l' href='/style.css' rel='stylesheet'>"
                  + "<script id='s' src='/static/main.js'></script>");
            Document out = rewritten(html);
            assertThat(out.selectFirst("#i").attr("src")).isEqualTo(expectedProxy("/img.png"));
            assertThat(out.selectFirst("#l").attr("href")).isEqualTo(expectedProxy("/style.css"));
            assertThat(out.selectFirst("#s").attr("src")).isEqualTo(expectedProxy("/static/main.js"));
        }

        @Test
        @DisplayName("<base href> は削除される")
        void baseHrefRemoved() {
            Document out = rewritten("<html><head><base href='" + BASE_URL + "/'></head><body></body></html>");
            assertThat(out.select("base")).isEmpty();
        }

        @Test
        @DisplayName("<a href=''> や href なしの <a> でも例外にならない")
        void anchorEmptyOrMissingHref() {
            Document out = rewritten(wrap("<a id='x' href=''>empty</a><a id='y'>nohref</a>"));
            assertThat(out.selectFirst("#x").attr("href")).isEmpty();
            assertThat(out.selectFirst("#y").hasAttr("href")).isFalse();
        }

        @Test
        @DisplayName("javascript:/mailto:/data: スキームは書き換えない")
        void specialSchemes() {
            Document out = rewritten(wrap(
                    "<a id='a' href='javascript:void(0)'>js</a>"
                  + "<a id='b' href='mailto:test@example.com'>mail</a>"
                  + "<a id='c' href='data:text/plain;base64,xxx'>data</a>"
                  + "<a id='d' href='#anchor'>hash</a>"));
            assertThat(out.selectFirst("#a").attr("href")).isEqualTo("javascript:void(0)");
            assertThat(out.selectFirst("#b").attr("href")).isEqualTo("mailto:test@example.com");
            assertThat(out.selectFirst("#c").attr("href")).isEqualTo("data:text/plain;base64,xxx");
            assertThat(out.selectFirst("#d").attr("href")).isEqualTo("#anchor");
        }

        @Test
        @DisplayName("既にプロキシ URL の場合は二重書き換えしない")
        void alreadyProxied() {
            String already = PROXY_PREFIX + "/foo?token=" + token;
            Document out = rewritten(wrap("<a id='x' href='" + already + "'>link</a>"));
            assertThat(out.selectFirst("#x").attr("href")).isEqualTo(already);
        }

        @Test
        @DisplayName("currentUpstreamUrl 指定時: 相対 URL (ファイル名のみ) は会場ページのディレクトリ基準で解決する")
        void relativeFileResolvedAgainstUpstreamPage() {
            String upstream = BASE_URL + "/kaderu27/index.php";
            String html = wrap(
                    "<form id='f' action='index.php' method='POST'></form>"
                  + "<script id='s' src='script/default.js?v=1'></script>"
                  + "<link id='l' href='css/style.css?v=2' rel='stylesheet'>");
            String out = rewriter.rewrite(html, upstream, session, venueConfig, noopStrategy);
            Document doc = Jsoup.parse(out);
            assertThat(doc.selectFirst("#f").attr("action"))
                    .isEqualTo(expectedProxy("/kaderu27/index.php"));
            assertThat(doc.selectFirst("#s").attr("src"))
                    .isEqualTo(PROXY_PREFIX + "/kaderu27/script/default.js?v=1&token=" + token);
            assertThat(doc.selectFirst("#l").attr("href"))
                    .isEqualTo(PROXY_PREFIX + "/kaderu27/css/style.css?v=2&token=" + token);
        }

        @Test
        @DisplayName("currentUpstreamUrl 指定時: 親ディレクトリへの ../ も会場ページ基準で解決する")
        void relativeParentDirectoryResolved() {
            String upstream = BASE_URL + "/kaderu27/sub/page.php";
            Document out = Jsoup.parse(rewriter.rewrite(
                    wrap("<a id='x' href='../shared/main.css'>x</a>"),
                    upstream, session, venueConfig, noopStrategy));
            assertThat(out.selectFirst("#x").attr("href"))
                    .isEqualTo(expectedProxy("/kaderu27/shared/main.css"));
        }
    }

    @Nested
    @DisplayName("インライン CSS / JS の URL 書き換え")
    class InlineRewriting {

        @Test
        @DisplayName("<style> 内の url(会場サイトURL) を書き換え")
        void styleUrlVenue() {
            String html = wrap("<style>body { background: url(" + BASE_URL + "/bg.png) }</style>");
            String out = rewriter.rewrite(html, session, venueConfig, noopStrategy);
            assertThat(out).contains("url(" + expectedProxy("/bg.png") + ")");
        }

        @Test
        @DisplayName("外部CSS内の @import url(相対URL) をCSSファイル基準で書き換え")
        void externalCssImportUrlRelative() {
            String upstream = BASE_URL + "/kaderu27/css/style.css?25007";
            String css = "@import url(color_local.css?25004) screen;\n"
                    + "@import url(\"../shared/form.css\") screen;";

            String out = rewriter.rewriteCss(css, upstream, session, venueConfig);

            assertThat(out)
                    .contains("url(" + PROXY_PREFIX
                            + "/kaderu27/css/color_local.css?25004&token=" + token + ")")
                    .contains("url(\"" + PROXY_PREFIX
                            + "/kaderu27/shared/form.css?token=" + token + "\")");
        }

        @Test
        @DisplayName("外部CSS内の @import \"相対URL\" と通常 url(...) を書き換え")
        void externalCssQuotedImportAndUrl() {
            String upstream = BASE_URL + "/kaderu27/css/style.css";
            String css = "@import \"font.css?25001\" screen;\n"
                    + ".logo { background-image: url('../images/logo.png'); }\n"
                    + ".icon { background-image: url(data:image/png;base64,xxx); }\n"
                    + ".external { background-image: url(https://example.com/ext.png); }";

            String out = rewriter.rewriteCss(css, upstream, session, venueConfig);

            assertThat(out)
                    .contains("@import \"" + PROXY_PREFIX
                            + "/kaderu27/css/font.css?25001&token=" + token + "\"")
                    .contains("url('" + PROXY_PREFIX
                            + "/kaderu27/images/logo.png?token=" + token + "')")
                    .contains("url(data:image/png;base64,xxx)")
                    .contains("url(https://example.com/ext.png)");
        }

        @Test
        @DisplayName("<script> 内の引用符付き 会場サイト絶対URL を書き換え")
        void scriptQuotedVenueUrl() {
            String html = wrap("<script>const URL = 'https://k2.p-kashikan.jp/api'; foo();</script>");
            String out = rewriter.rewrite(html, session, venueConfig, noopStrategy);
            assertThat(out).contains("'" + expectedProxy("/api") + "'");
        }

        @Test
        @DisplayName("<script> 内の会場外URLは書き換えない")
        void scriptExternalUrl() {
            String html = wrap("<script>const X = 'https://example.com/api';</script>");
            String out = rewriter.rewrite(html, session, venueConfig, noopStrategy);
            assertThat(out).contains("'https://example.com/api'");
            assertThat(out).doesNotContain(PROXY_PREFIX + "/api");
        }
    }

    @Nested
    @DisplayName("注入スクリプト")
    class InjectorScript {

        @Test
        @DisplayName("注入スクリプトが <head> に挿入される")
        void injectorInHead() {
            Document out = rewritten("<html><head></head><body></body></html>");
            assertThat(out.select("head script.vrp-injector")).hasSize(1);
        }

        @Test
        @DisplayName("注入スクリプトに Location.prototype.assign のフックが含まれる")
        void injectorContainsLocationHook() {
            Document out = rewritten("<html><head></head><body></body></html>");
            String body = out.selectFirst("head script.vrp-injector").data();
            assertThat(body).contains("Location.prototype.assign");
            assertThat(body).contains("Location.prototype.replace");
            assertThat(body).contains("window.open");
            assertThat(body).contains("window.fetch");
            assertThat(body).contains("XMLHttpRequest");
        }

        @Test
        @DisplayName("token / baseUrl / proxyPrefix プレースホルダが置換されている")
        void placeholdersReplaced() {
            Document out = rewritten("<html><head></head><body></body></html>");
            String body = out.selectFirst("head script.vrp-injector").data();
            assertThat(body).contains(token);
            assertThat(body).contains(BASE_URL);
            assertThat(body).contains(PROXY_PREFIX);
            assertThat(body).doesNotContain("{{token}}");
            assertThat(body).doesNotContain("{{baseUrl}}");
            assertThat(body).doesNotContain("{{proxyPrefix}}");
            assertThat(body).doesNotContain("{{currentUpstreamUrl}}");
        }

        @Test
        @DisplayName("currentUpstreamUrl が注入スクリプトに埋め込まれる")
        void injectorContainsUpstreamUrl() {
            String upstream = BASE_URL + "/kaderu27/index.php";
            String out = rewriter.rewrite(
                    "<html><head></head><body></body></html>",
                    upstream, session, venueConfig, noopStrategy);
            Document doc = Jsoup.parse(out);
            String body = doc.selectFirst("head script.vrp-injector").data();
            assertThat(body).contains("kaderu27/index.php");
            assertThat(body).doesNotContain("{{currentUpstreamUrl}}");
        }

        @Test
        @DisplayName("strategy.injectScript() の戻り値がスクリプト末尾に含まれる")
        void strategyInjectScriptAppended() {
            VenueRewriteStrategy custom = new VenueRewriteStrategy() {
                @Override public VenueId venue() { return VenueId.KADERU; }
                @Override public String rewriteHtml(String html, String proxyToken) { return html; }
                @Override public String injectScript() { return "console.log('venue-specific hook');"; }
            };
            String out = rewriter.rewrite("<html><head></head><body></body></html>", session, venueConfig, custom);
            assertThat(out).contains("console.log('venue-specific hook');");
        }

        @Test
        @DisplayName("strategy.injectScript() が空でも例外にならない")
        void strategyEmptyScript() {
            String out = rewriter.rewrite("<html><head></head><body></body></html>", session, venueConfig, noopStrategy);
            assertThat(out).contains("vrp-injector");
        }
    }

    @Nested
    @DisplayName("ヘッダーバナー")
    class BannerInjection {

        @Test
        @DisplayName("バナーが <body> 先頭に挿入される")
        void bannerAtBodyStart() {
            Document out = rewritten("<html><head></head><body><div id='content'>x</div></body></html>");
            Element body = out.body();
            assertThat(body).isNotNull();
            assertThat(body.selectFirst("#vrp-banner")).isNotNull();
            // バナーの位置が <body> の先頭であることを確認 (#content より前にある)
            int bannerIndex = body.children().indexOf(body.selectFirst("#vrp-banner"));
            int contentIndex = body.children().indexOf(body.selectFirst("#content"));
            assertThat(bannerIndex).isLessThan(contentIndex);
        }

        @Test
        @DisplayName("バナーに displayName が含まれる")
        void bannerContainsDisplayName() {
            Document out = rewritten("<html><head></head><body></body></html>");
            assertThat(out.selectFirst("#vrp-title").text()).contains(DISPLAY_NAME);
        }

        @Test
        @DisplayName("バナーに「アプリに戻る」ボタンが含まれる")
        void bannerHasBackButton() {
            Document out = rewritten("<html><head></head><body></body></html>");
            assertThat(out.selectFirst("#vrp-back-btn")).isNotNull();
        }

        @Test
        @DisplayName("バナースクリプトに BroadcastChannel('venue-reservation-proxy') が含まれる")
        void bannerHasBroadcastChannel() {
            Document out = rewritten("<html><head></head><body></body></html>");
            String html = out.outerHtml();
            assertThat(html).contains("BroadcastChannel");
            assertThat(html).contains("venue-reservation-proxy");
            assertThat(html).contains("reservation-completed");
        }

        @Test
        @DisplayName("バナースクリプトに token / practiceSessionId / venue プレースホルダが置換されている")
        void bannerPlaceholdersReplaced() {
            Document out = rewritten("<html><head></head><body></body></html>");
            String html = out.outerHtml();
            assertThat(html).contains(token);
            assertThat(html).contains("123"); // practiceSessionId
            assertThat(html).contains("KADERU");
            assertThat(html).doesNotContain("{{practiceSessionId}}");
            assertThat(html).doesNotContain("{{venue}}");
        }
    }

    @Nested
    @DisplayName("統合 / 異常系")
    class IntegrationAndEdgeCases {

        @Test
        @DisplayName("完全な HTML 文書に対し全処理が適用される")
        void fullDocument() {
            String html = "<html><head>"
                    + "<base href='" + BASE_URL + "/'>"
                    + "<link href='/style.css' rel='stylesheet'>"
                    + "</head><body>"
                    + "<a href='" + BASE_URL + "/foo'>link</a>"
                    + "<form action='/submit' method='POST'></form>"
                    + "</body></html>";
            String out = rewriter.rewrite(html, session, venueConfig, noopStrategy);
            // base 削除
            assertThat(out).doesNotContain("<base");
            // a/href, form/action, link/href がプロキシ URL に
            assertThat(out).contains(expectedProxy("/foo"));
            assertThat(out).contains(expectedProxy("/submit"));
            assertThat(out).contains(expectedProxy("/style.css"));
            // 注入スクリプトとバナー両方挿入
            assertThat(out).contains("vrp-injector");
            assertThat(out).contains("vrp-banner");
        }

        @Test
        @DisplayName("null 入力は空文字を返す")
        void nullInput() {
            String out = rewriter.rewrite(null, session, venueConfig, noopStrategy);
            assertThat(out).isEmpty();
        }

        @Test
        @DisplayName("空文字入力は空文字を返す")
        void emptyInput() {
            String out = rewriter.rewrite("", session, venueConfig, noopStrategy);
            assertThat(out).isEmpty();
        }

        @Test
        @DisplayName("strategy が null でも処理が続行される")
        void nullStrategy() {
            String out = rewriter.rewrite("<html><body></body></html>", session, venueConfig, null);
            assertThat(out).contains("vrp-injector");
            assertThat(out).contains("vrp-banner");
        }

        @Test
        @DisplayName("body / head タグが省略された HTML にも追加処理が適用される")
        void htmlWithoutHeadOrBody() {
            String out = rewriter.rewrite("<html><a href='/foo'>x</a></html>", session, venueConfig, noopStrategy);
            assertThat(out).contains(expectedProxy("/foo"));
            assertThat(out).contains("vrp-banner");
        }
    }

    @Nested
    @DisplayName("KaderuRewriteStrategy")
    class KaderuRewriteStrategySpec {

        private final KaderuRewriteStrategy kaderuStrategy = new KaderuRewriteStrategy();

        @Test
        @DisplayName("venue() は KADERU を返す")
        void venueReturnsKaderu() {
            assertThat(kaderuStrategy.venue()).isEqualTo(VenueId.KADERU);
        }

        @Test
        @DisplayName("rewriteHtml は入力をそのまま返す (no-op)")
        void rewriteHtmlIsNoop() {
            String html = "<html><body><a href='/foo'>x</a></body></html>";
            assertThat(kaderuStrategy.rewriteHtml(html, "tok-123")).isEqualTo(html);
        }

        @Test
        @DisplayName("injectScript は空文字を返す")
        void injectScriptIsEmpty() {
            assertThat(kaderuStrategy.injectScript()).isEmpty();
        }
    }
}
