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
        if (html == null || html.isEmpty()) {
            return html == null ? "" : html;
        }

        String pre = strategy != null ? strategy.rewriteHtml(html, session.getToken()) : html;
        if (pre == null) pre = html;

        Document doc = Jsoup.parse(pre);
        String baseUrl = venueConfig.baseUrl();
        String token = session.getToken();

        rewriteAttribute(doc, "a[href]", "href", baseUrl, token);
        rewriteAttribute(doc, "form[action]", "action", baseUrl, token);
        rewriteAttribute(doc, "img[src]", "src", baseUrl, token);
        rewriteAttribute(doc, "link[href]", "href", baseUrl, token);
        rewriteAttribute(doc, "script[src]", "src", baseUrl, token);
        rewriteAttribute(doc, "iframe[src]", "src", baseUrl, token);

        for (Element base : doc.select("base")) {
            base.remove();
        }

        rewriteInlineScriptsAndStyles(doc, baseUrl, token);

        injectScriptIntoHead(doc, session, venueConfig, strategy);
        injectBannerIntoBody(doc, session, venueConfig);

        return doc.outerHtml();
    }

    private void rewriteAttribute(Document doc, String selector, String attr, String baseUrl, String token) {
        for (Element el : doc.select(selector)) {
            String original = el.attr(attr);
            if (original == null || original.isEmpty()) continue;
            String rewritten = rewriteUrl(original, baseUrl, token);
            if (rewritten != null && !rewritten.equals(original)) {
                el.attr(attr, rewritten);
            }
        }
    }

    /**
     * 会場サイト URL → プロキシ URL に変換する。会場サイト外 URL や javascript: などは不変。
     *
     * @return 変換後 URL。変換不要なら入力をそのまま返す。null は null。
     */
    String rewriteUrl(String url, String baseUrl, String token) {
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

        String path;
        if (trimmed.startsWith(baseUrl)) {
            path = trimmed.substring(baseUrl.length());
            if (path.isEmpty() || path.charAt(0) != '/') {
                path = "/" + path;
            }
        } else if (trimmed.charAt(0) == '/') {
            path = trimmed;
        } else if (lower.startsWith("http://") || lower.startsWith("https://")) {
            // 会場外 URL は透過
            return url;
        } else {
            // プロトコル相対 / 相対 URL は会場サイト相対とみなす
            if (trimmed.startsWith("//")) {
                return url; // protocol-relative external — 透過
            }
            path = "/" + trimmed;
        }

        return PROXY_PREFIX + path + appendTokenSeparator(path) + "token=" + urlEncode(token);
    }

    private static String appendTokenSeparator(String path) {
        return path.indexOf('?') >= 0 ? "&" : "?";
    }

    private static String urlEncode(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private void rewriteInlineScriptsAndStyles(Document doc, String baseUrl, String token) {
        // インライン script: 引用符で囲まれた会場サイト絶対URLのみを保守的に書き換える
        for (Element script : doc.select("script:not([src])")) {
            String body = script.data();
            if (body == null || body.isEmpty()) continue;
            String rewritten = rewriteQuotedVenueUrls(body, baseUrl, token);
            if (!rewritten.equals(body)) {
                script.text("");
                script.appendChild(new org.jsoup.nodes.DataNode(rewritten));
            }
        }
        // インライン style: url(...) と引用符付き URL を書き換え
        for (Element style : doc.select("style")) {
            String body = style.data();
            if (body == null || body.isEmpty()) continue;
            String afterCssUrl = rewriteCssUrls(body, baseUrl, token);
            String afterQuoted = rewriteQuotedVenueUrls(afterCssUrl, baseUrl, token);
            if (!afterQuoted.equals(body)) {
                style.text("");
                style.appendChild(new org.jsoup.nodes.DataNode(afterQuoted));
            }
        }
    }

    private String rewriteQuotedVenueUrls(String body, String baseUrl, String token) {
        Matcher m = QUOTED_VENUE_URL.matcher(body);
        StringBuilder sb = new StringBuilder(body.length());
        int last = 0;
        while (m.find()) {
            sb.append(body, last, m.start());
            String quote = m.group(1);
            String url = m.group(2);
            String closeQuote = m.group(3);
            if (url.startsWith(baseUrl)) {
                String rewritten = rewriteUrl(url, baseUrl, token);
                sb.append(quote).append(rewritten).append(closeQuote);
            } else {
                sb.append(m.group(0));
            }
            last = m.end();
        }
        sb.append(body, last, body.length());
        return sb.toString();
    }

    private String rewriteCssUrls(String body, String baseUrl, String token) {
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
                String rewritten = rewriteUrl(url, baseUrl, token);
                sb.append("url(").append(openQuote).append(rewritten).append(closeQuote).append(")");
            } else {
                sb.append(m.group(0));
            }
            last = m.end();
        }
        sb.append(body, last, body.length());
        return sb.toString();
    }

    private void injectScriptIntoHead(Document doc, ProxySession session, VenueConfig venueConfig, VenueRewriteStrategy strategy) {
        String venueInject = strategy != null && strategy.injectScript() != null ? strategy.injectScript() : "";
        String script = injectorTemplate
                .replace("{{token}}", jsEscape(session.getToken()))
                .replace("{{baseUrl}}", jsEscape(venueConfig.baseUrl()))
                .replace("{{proxyPrefix}}", jsEscape(PROXY_PREFIX))
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
        String banner = bannerTemplate
                .replace("{{displayName}}", htmlEscape(venueConfig.displayName()))
                .replace("{{token}}", jsEscape(session.getToken()))
                .replace("{{practiceSessionId}}", psid == null ? "" : String.valueOf(psid))
                .replace("{{venue}}", session.getVenue() == null ? "" : session.getVenue().name());

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
