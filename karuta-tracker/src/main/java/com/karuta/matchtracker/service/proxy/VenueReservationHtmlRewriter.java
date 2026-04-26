package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.service.proxy.venue.VenueConfig;
import com.karuta.matchtracker.service.proxy.venue.VenueRewriteStrategy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 会場非依存の HTML 書き換えコア。
 *
 * <p>会場サイトから返却された HTML に対し以下を順に適用する:</p>
 * <ol>
 *   <li>会場固有の事前書き換え ({@link VenueRewriteStrategy#rewriteHtml})</li>
 *   <li>Jsoup による DOM 書き換え:
 *       {@code <a href>} {@code <form action>} {@code <img src>} {@code <link href>}
 *       {@code <script src>} を {@code /api/venue-reservation-proxy/fetch/...} に変換、
 *       {@code <base href>} は削除</li>
 *   <li>インライン {@code <script>} / {@code <style>} 内の会場サイト URL を正規表現で書き換え</li>
 *   <li>注入スクリプト ({@code Location}/{@code window.open}/{@code fetch}/{@code XHR} フック) を
 *       {@code <head>} 末尾に挿入</li>
 *   <li>ヘッダーバナー (アプリに戻る + 完了ダイアログ + BroadcastChannel) を {@code <body>} 先頭に挿入</li>
 * </ol>
 *
 * <p>会場固有の URL / DOM はすべて {@link VenueConfig} と {@link VenueRewriteStrategy} 経由で取得し、
 * 本クラス自体は会場依存ロジックを持たない。</p>
 */
@Component
@Slf4j
public class VenueReservationHtmlRewriter {

    /** プロキシ中継エンドポイントのパスプレフィックス。 */
    public static final String PROXY_PREFIX = "/api/venue-reservation-proxy/fetch";

    private static final String INJECTOR_RESOURCE = "static/venue-proxy-injector.js";
    private static final String BANNER_RESOURCE = "static/venue-proxy-banner.html";

    /** 注入したスクリプトを判別する marker class (再注入抑止 + テスト容易性) */
    private static final String MARKER_INJECTOR = "vrp-injector";

    /** インライン script/style 内の URL リライト用正規表現 (引用符で囲まれた絶対URL) */
    private static final Pattern QUOTED_VENUE_URL = Pattern.compile(
            "([\"'])(https?://[^\"'\\s]+)([\"'])");

    /** インライン style 内の url(...) 書き換え用正規表現 */
    private static final Pattern CSS_URL = Pattern.compile(
            "url\\(\\s*([\"']?)([^)\"']+)([\"']?)\\s*\\)");

    private final String injectorTemplate;
    private final String bannerTemplate;

    public VenueReservationHtmlRewriter() {
        this.injectorTemplate = loadResource(INJECTOR_RESOURCE);
        this.bannerTemplate = loadResource(BANNER_RESOURCE);
    }

    /**
     * 会場サイトからの HTML を、ブラウザがプロキシ経由でのみ動作するように書き換える。
     *
     * @param html         会場サイトから受信した生の HTML
     * @param session      対象プロキシセッション (token / venue / practiceSessionId 参照)
     * @param venueConfig  会場メタ情報 (baseUrl / displayName)
     * @param strategy     会場固有 strategy
     * @return 書き換え済み HTML 文字列
     */
    public String rewrite(String html, ProxySession session, VenueConfig venueConfig, VenueRewriteStrategy strategy) {
        return rewrite(html, defaultEntryUrl(venueConfig), session, venueConfig, strategy);
    }

    /**
     * 上流 URL を明示して HTML を書き換える。
     *
     * <p>{@code currentUpstreamUrl} は会場サイト上の現在のページの絶対 URL
     * (例: {@code https://k2.p-kashikan.jp/kaderu27/index.php?p=apply})。
     * HTML 内の相対 URL ({@code "index.php"} や {@code "script/default.js"}) を
     * この URL を基準に解決し、ドメイン直下に誤ってマッピングされる事故を防ぐ。</p>
     *
     * @param html              会場サイトから受信した生の HTML
     * @param currentUpstreamUrl HTML が取得された会場サイト上の絶対 URL
     * @param session           対象プロキシセッション
     * @param venueConfig       会場メタ情報
     * @param strategy          会場固有 strategy
     * @return 書き換え済み HTML 文字列
     */
    public String rewrite(String html, String currentUpstreamUrl, ProxySession session,
                          VenueConfig venueConfig, VenueRewriteStrategy strategy) {
        if (html == null || html.isEmpty()) {
            return html == null ? "" : html;
        }

        String pre = strategy != null ? strategy.rewriteHtml(html, session.getToken()) : html;
        if (pre == null) pre = html;

        Document doc = Jsoup.parse(pre);
        String baseUrl = venueConfig.baseUrl();
        String token = session.getToken();
        String upstreamUrl = currentUpstreamUrl != null && !currentUpstreamUrl.isBlank()
                ? currentUpstreamUrl
                : defaultEntryUrl(venueConfig);

        rewriteAttribute(doc, "a[href]", "href", baseUrl, upstreamUrl, token);
        rewriteAttribute(doc, "form[action]", "action", baseUrl, upstreamUrl, token);
        rewriteAttribute(doc, "img[src]", "src", baseUrl, upstreamUrl, token);
        rewriteAttribute(doc, "link[href]", "href", baseUrl, upstreamUrl, token);
        rewriteAttribute(doc, "script[src]", "src", baseUrl, upstreamUrl, token);
        rewriteAttribute(doc, "iframe[src]", "src", baseUrl, upstreamUrl, token);

        for (Element base : doc.select("base")) {
            base.remove();
        }

        rewriteInlineScriptsAndStyles(doc, baseUrl, upstreamUrl, token);

        injectScriptIntoHead(doc, session, venueConfig, strategy, upstreamUrl);
        injectBannerIntoBody(doc, session, venueConfig);

        return doc.outerHtml();
    }

    private static String defaultEntryUrl(VenueConfig venueConfig) {
        String entry = venueConfig.entryPath();
        if (entry == null || entry.isBlank()) entry = "/";
        if (entry.charAt(0) != '/') entry = "/" + entry;
        return venueConfig.baseUrl() + entry;
    }

    private void rewriteAttribute(Document doc, String selector, String attr,
                                  String baseUrl, String upstreamUrl, String token) {
        for (Element el : doc.select(selector)) {
            String original = el.attr(attr);
            if (original == null || original.isEmpty()) continue;
            String rewritten = rewriteUrl(original, baseUrl, upstreamUrl, token);
            if (rewritten != null && !rewritten.equals(original)) {
                el.attr(attr, rewritten);
            }
        }
    }

    /**
     * 後方互換用 (テスト等)。{@code venueConfig.baseUrl()} 直下を upstream とみなす。
     */
    String rewriteUrl(String url, String baseUrl, String token) {
        return rewriteUrl(url, baseUrl, baseUrl + "/", token);
    }

    /**
     * 会場サイト URL → プロキシ URL に変換する。
     *
     * <p>相対 URL は {@code currentUpstreamUrl} (会場サイト上の現在ページ URL) を基準に
     * {@link URI#resolve} で解決してから絶対化する。これにより
     * {@code action="index.php"} のような相対 URL が、誤ってドメイン直下ではなく
     * {@code /kaderu27/index.php} に解決される。</p>
     *
     * <p>会場サイト外 URL や {@code javascript:}/{@code mailto:} 等は不変。</p>
     *
     * @return 変換後 URL。変換不要なら入力をそのまま返す。null は null。
     */
    String rewriteUrl(String url, String baseUrl, String currentUpstreamUrl, String token) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return url;

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("javascript:")
                || lower.startsWith("mailto:")
                || lower.startsWith("data:")
                || lower.startsWith("about:")
                || lower.startsWith("blob:")
                || lower.startsWith("#")
                || trimmed.startsWith(PROXY_PREFIX)) {
            return url;
        }
        if (trimmed.startsWith("//")) {
            return url; // protocol-relative external — 透過
        }

        // 会場サイト上での絶対 URL を URI.resolve で解決する。
        URI absolute = resolveAgainstUpstream(trimmed, currentUpstreamUrl);
        if (absolute == null) {
            return url;
        }

        // 絶対 URL のスキーム+ホストが baseUrl と一致しなければ「会場外 URL」として透過。
        URI baseUri;
        try {
            baseUri = new URI(baseUrl);
        } catch (URISyntaxException e) {
            return url;
        }
        if (absolute.getHost() == null || baseUri.getHost() == null
                || !absolute.getHost().equalsIgnoreCase(baseUri.getHost())
                || (absolute.getScheme() != null && baseUri.getScheme() != null
                    && !absolute.getScheme().equalsIgnoreCase(baseUri.getScheme()))) {
            return url;
        }

        String path = absolute.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = absolute.getRawQuery();
        String fragment = absolute.getRawFragment();

        StringBuilder sb = new StringBuilder();
        sb.append(PROXY_PREFIX).append(path);
        if (query != null) {
            sb.append('?').append(query);
            sb.append('&');
        } else {
            sb.append('?');
        }
        sb.append("token=").append(urlEncode(token));
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    private static URI resolveAgainstUpstream(String url, String currentUpstreamUrl) {
        try {
            URI base = currentUpstreamUrl != null && !currentUpstreamUrl.isBlank()
                    ? new URI(currentUpstreamUrl)
                    : null;
            URI input = new URI(url);
            if (input.isAbsolute()) {
                return input;
            }
            if (base == null) {
                return null;
            }
            return base.resolve(input);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String urlEncode(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void rewriteInlineScriptsAndStyles(Document doc, String baseUrl, String upstreamUrl, String token) {
        // インライン script: 引用符で囲まれた会場サイト絶対URLのみを保守的に書き換える
        for (Element script : doc.select("script:not([src])")) {
            String body = script.data();
            if (body == null || body.isEmpty()) continue;
            String rewritten = rewriteQuotedVenueUrls(body, baseUrl, upstreamUrl, token);
            if (!rewritten.equals(body)) {
                script.text("");
                script.appendChild(new org.jsoup.nodes.DataNode(rewritten));
            }
        }
        // インライン style: url(...) と引用符付き URL を書き換え
        for (Element style : doc.select("style")) {
            String body = style.data();
            if (body == null || body.isEmpty()) continue;
            String afterCssUrl = rewriteCssUrls(body, baseUrl, upstreamUrl, token);
            String afterQuoted = rewriteQuotedVenueUrls(afterCssUrl, baseUrl, upstreamUrl, token);
            if (!afterQuoted.equals(body)) {
                style.text("");
                style.appendChild(new org.jsoup.nodes.DataNode(afterQuoted));
            }
        }
    }

    private String rewriteQuotedVenueUrls(String body, String baseUrl, String upstreamUrl, String token) {
        Matcher m = QUOTED_VENUE_URL.matcher(body);
        StringBuilder sb = new StringBuilder(body.length());
        int last = 0;
        while (m.find()) {
            sb.append(body, last, m.start());
            String quote = m.group(1);
            String url = m.group(2);
            String closeQuote = m.group(3);
            if (url.startsWith(baseUrl)) {
                String rewritten = rewriteUrl(url, baseUrl, upstreamUrl, token);
                sb.append(quote).append(rewritten).append(closeQuote);
            } else {
                sb.append(m.group(0));
            }
            last = m.end();
        }
        sb.append(body, last, body.length());
        return sb.toString();
    }

    private String rewriteCssUrls(String body, String baseUrl, String upstreamUrl, String token) {
        Matcher m = CSS_URL.matcher(body);
        StringBuilder sb = new StringBuilder(body.length());
        int last = 0;
        while (m.find()) {
            sb.append(body, last, m.start());
            String openQuote = m.group(1);
            String url = m.group(2).trim();
            String closeQuote = m.group(3);
            if (url.startsWith(baseUrl)
                    || url.startsWith("/") && !url.startsWith("//")) {
                String rewritten = rewriteUrl(url, baseUrl, upstreamUrl, token);
                sb.append("url(").append(openQuote).append(rewritten).append(closeQuote).append(")");
            } else {
                sb.append(m.group(0));
            }
            last = m.end();
        }
        sb.append(body, last, body.length());
        return sb.toString();
    }

    private void injectScriptIntoHead(Document doc, ProxySession session, VenueConfig venueConfig,
                                      VenueRewriteStrategy strategy, String currentUpstreamUrl) {
        String venueInject = strategy != null && strategy.injectScript() != null ? strategy.injectScript() : "";
        String upstream = currentUpstreamUrl != null && !currentUpstreamUrl.isBlank()
                ? currentUpstreamUrl
                : defaultEntryUrl(venueConfig);
        String script = injectorTemplate
                .replace("{{token}}", jsEscape(session.getToken()))
                .replace("{{baseUrl}}", jsEscape(venueConfig.baseUrl()))
                .replace("{{proxyPrefix}}", jsEscape(PROXY_PREFIX))
                .replace("{{currentUpstreamUrl}}", jsEscape(upstream))
                .replace("/* {{venueInjectScript}} */", venueInject);

        Element head = doc.head();
        if (head == null) {
            head = doc.appendElement("head");
        }
        Element scriptEl = new Element(Tag.valueOf("script"), "");
        scriptEl.attr("type", "text/javascript");
        scriptEl.addClass(MARKER_INJECTOR);
        scriptEl.appendChild(new org.jsoup.nodes.DataNode(script));
        // <head> 末尾に挿入 (ページ先頭スクリプトより後で良い: フックは window/Location プロトタイプを差し替える)
        head.appendChild(scriptEl);
    }

    private void injectBannerIntoBody(Document doc, ProxySession session, VenueConfig venueConfig) {
        Element body = doc.body();
        if (body == null) {
            body = doc.appendElement("body");
        }
        Long psid = session.getPracticeSessionId();
        String returnUrl = session.getReturnUrl() == null ? "" : session.getReturnUrl();
        String banner = bannerTemplate
                .replace("{{displayName}}", htmlEscape(venueConfig.displayName()))
                .replace("{{token}}", jsEscape(session.getToken()))
                .replace("{{practiceSessionId}}", psid == null ? "" : String.valueOf(psid))
                .replace("{{venue}}", session.getVenue() == null ? "" : session.getVenue().name())
                .replace("{{returnUrl}}", jsEscape(returnUrl));

        // <body> 先頭に挿入
        body.prepend(banner);
    }

    private static String jsEscape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"':  out.append("\\\""); break;
                case '\'': out.append("\\'"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '<':  out.append("\\u003C"); break;
                case '>':  out.append("\\u003E"); break;
                case '&':  out.append("\\u0026"); break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }

    private static String htmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String loadResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load classpath resource: " + path, e);
        }
    }
}
