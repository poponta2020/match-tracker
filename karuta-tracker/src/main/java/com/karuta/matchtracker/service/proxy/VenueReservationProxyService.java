package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.config.AdjacentRoomConfig;
import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import com.karuta.matchtracker.dto.CreateVenueProxySessionRequest;
import com.karuta.matchtracker.dto.CreateVenueProxySessionResponse;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.proxy.venue.VenueConfig;
import com.karuta.matchtracker.service.proxy.venue.VenueRewriteStrategy;
import com.karuta.matchtracker.util.AdminScopeValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * venue-reservation-proxy の会場非依存ファサード。
 *
 * <p>Spring の {@code Map<String, T>} DI は bean 名がキーになるため、会場 dispatch 用の registry は
 * 各 bean の {@code venue()} を見てコンストラクタで {@link EnumMap} に組み立てる。</p>
 */
@Service
@Slf4j
public class VenueReservationProxyService {

    private static final String VIEW_PATH = "/api/venue-reservation-proxy/view";
    private static final String FETCH_PREFIX = VenueReservationHtmlRewriter.PROXY_PREFIX;
    private static final String COMPLETED_HEADER = "X-VRP-Completed";
    private static final String REFERRER_POLICY_HEADER = "Referrer-Policy";
    /**
     * view/fetch のレスポンスに付与する Referrer-Policy。
     * token を URL 上に持つため、ユーザーが会場サイト内のリンクをクリックした際に
     * Referer 経由で外部に漏れるのを防ぐ。
     */
    private static final String REFERRER_POLICY_NO_REFERRER = "no-referrer";
    private static final String CSP_HEADER = "Content-Security-Policy";
    private static final MediaType TEXT_HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    /**
     * Phase 1 で許可する slotIndex。要件定義書では「夜間のみ自動予約対象」と決定済み。
     * 朝/昼スロットを ADMIN+ 権限で確保できないようにサーバ側で固定する。
     */
    private static final int EXPECTED_NIGHT_SLOT = 2;

    private static final Set<String> REQUEST_HEADERS_SKIP = Set.of(
            "host",
            "cookie",
            "content-length",
            "connection",
            "transfer-encoding",
            "accept-encoding"
    );

    private static final Set<String> RESPONSE_HEADERS_SKIP = Set.of(
            "set-cookie",
            "x-frame-options",
            "strict-transport-security",
            "content-security-policy",
            "content-length",
            "content-type",
            "content-encoding",
            "connection",
            "transfer-encoding",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "trailer",
            "upgrade"
    );

    private final VenueReservationProxyConfig proxyConfig;
    private final VenueReservationSessionStore sessionStore;
    private final VenueReservationHtmlRewriter htmlRewriter;
    private final VenueReservationCompletionDetector completionDetector;
    private final PracticeSessionRepository practiceSessionRepository;
    private final Map<VenueId, VenueReservationClient> clients;
    private final Map<VenueId, VenueConfig> venueConfigs;
    private final Map<VenueId, VenueRewriteStrategy> rewriteStrategies;
    /**
     * {@code returnUrl} の host に許可するセット。{@code app.cors.allowed-origins} から
     * 起動時に算出する。空セットの場合は returnUrl の URL 形式 (http/https) のみ検証する
     * フェイルクローズに倒すため、createSession で必ず非空チェックを行う。
     */
    private final Set<String> allowedReturnUrlHosts;

    public VenueReservationProxyService(VenueReservationProxyConfig proxyConfig,
                                        VenueReservationSessionStore sessionStore,
                                        VenueReservationHtmlRewriter htmlRewriter,
                                        VenueReservationCompletionDetector completionDetector,
                                        PracticeSessionRepository practiceSessionRepository,
                                        List<VenueReservationClient> clients,
                                        List<VenueConfig> venueConfigs,
                                        List<VenueRewriteStrategy> rewriteStrategies,
                                        @Value("${app.cors.allowed-origins:}") String allowedOriginsRaw) {
        this.proxyConfig = proxyConfig;
        this.sessionStore = sessionStore;
        this.htmlRewriter = htmlRewriter;
        this.completionDetector = completionDetector;
        this.practiceSessionRepository = practiceSessionRepository;
        this.clients = toVenueMap(clients, VenueReservationClient::venue, "VenueReservationClient");
        this.venueConfigs = toVenueMap(venueConfigs, VenueConfig::venue, "VenueConfig");
        this.rewriteStrategies = toVenueMap(rewriteStrategies, VenueRewriteStrategy::venue, "VenueRewriteStrategy");
        this.allowedReturnUrlHosts = parseAllowedHosts(allowedOriginsRaw);
    }

    /**
     * プロキシセッションを発行する。
     *
     * <p>capability token (UUID) を発行する前に、{@code request.practiceSessionId} の所有権と
     * リクエスト内容の整合性をサーバ側で検証する:</p>
     * <ul>
     *   <li>ADMIN は自団体の練習日のみ操作可能 (SUPER_ADMIN は無条件で許可)</li>
     *   <li>{@code request.date} は対象 practice_session の {@code session_date} と一致する</li>
     *   <li>{@code request.venue} は practice_session の {@code venue_id} が属するプロキシ会場と一致する</li>
     *   <li>{@code request.roomName} は隣室名 ({@link AdjacentRoomConfig#getAdjacentRoomName(Long)}) と一致する</li>
     * </ul>
     */
    public CreateVenueProxySessionResponse createSession(CreateVenueProxySessionRequest request,
                                                         String currentUserRole,
                                                         Long adminOrganizationId) {
        VenueId venue = request == null ? null : request.getVenue();
        ensureVenueReady(venue);

        PracticeSession practiceSession = practiceSessionRepository.findById(request.getPracticeSessionId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PracticeSession", request.getPracticeSessionId()));

        AdminScopeValidator.validateScope(
                currentUserRole,
                adminOrganizationId,
                practiceSession.getOrganizationId(),
                "他団体の練習日は予約できません");

        validateRequestMatchesPracticeSession(request, practiceSession);
        validateReturnUrl(request);

        ProxySession session = sessionStore.createSession(
                venue,
                request.getPracticeSessionId(),
                request.getRoomName(),
                request.getDate(),
                request.getSlotIndex(),
                request.getReturnUrl());

        try {
            clientFor(venue).prepareReservationTray(session);
        } catch (VenueReservationProxyException e) {
            sessionStore.remove(session.getToken());
            throw e;
        } catch (RuntimeException e) {
            sessionStore.remove(session.getToken());
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.SCRIPT_ERROR,
                    venue,
                    "Failed to prepare venue reservation tray",
                    e);
        }

        return CreateVenueProxySessionResponse.builder()
                .proxyToken(session.getToken())
                .viewUrl(VIEW_PATH + "?token=" + session.getToken())
                .venue(venue)
                .build();
    }

    public ResponseEntity<String> view(String token) {
        ProxySession session = requireSession(token);
        sessionStore.touch(token);

        String html = session.getCachedTrayHtml();
        if (html == null) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.SCRIPT_ERROR,
                    session.getVenue(),
                    "Proxy session has no cached tray HTML");
        }

        VenueConfig venueConfig = configFor(session.getVenue());
        // 申込トレイ HTML はエントリーポイント URL から取得した想定。HTML 内の相対 URL は
        // この URL を基準に解決する (ドメイン直下と取り違えないため)。
        String currentUpstreamUrl = entryUpstreamUrl(venueConfig);
        String rewritten = htmlRewriter.rewrite(
                html,
                currentUpstreamUrl,
                session,
                venueConfig,
                rewriteStrategyFor(session.getVenue()));

        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .header(REFERRER_POLICY_HEADER, REFERRER_POLICY_NO_REFERRER)
                .header(CSP_HEADER, buildCspHeader(venueConfig.baseUrl()))
                .body(rewritten);
    }

    /**
     * view/fetch の HTML レスポンスに付与する CSP。会場サイト由来のインライン JS が
     * API オリジン上でフルパワー実行されるのを縮退させる。注入バナー/インジェクタは
     * {@code 'self'} で動作するため、会場側の {@code 'unsafe-inline'} は維持する。
     */
    private static String buildCspHeader(String baseUrl) {
        String src = "'self' " + baseUrl;
        return "default-src " + src + "; "
                + "img-src " + src + " data:; "
                + "script-src " + src + " 'unsafe-inline'; "
                + "style-src " + src + " 'unsafe-inline'; "
                + "connect-src " + src + "; "
                + "frame-ancestors 'self'";
    }

    private static String entryUpstreamUrl(VenueConfig venueConfig) {
        String entry = venueConfig.entryPath();
        if (entry == null || entry.isBlank()) entry = "/";
        if (entry.charAt(0) != '/') entry = "/" + entry;
        return venueConfig.baseUrl() + entry;
    }

    public ResponseEntity<byte[]> fetch(String token, HttpServletRequest request) {
        ProxySession session = requireSession(token);
        sessionStore.touch(token);

        VenueId venue = session.getVenue();
        VenueConfig venueConfig = configFor(venue);
        VenueRewriteStrategy rewriteStrategy = rewriteStrategyFor(venue);

        HttpUriRequest upstreamRequest = buildUpstreamRequest(request, venueConfig);

        try (CloseableHttpResponse upstreamResponse =
                     clientFor(venue).fetch(session, upstreamRequest)) {
            return toResponseEntity(session, venueConfig, rewriteStrategy, upstreamRequest, upstreamResponse);
        } catch (IOException e) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.SCRIPT_ERROR,
                    venue,
                    "Failed to read venue response",
                    e);
        }
    }

    private ResponseEntity<byte[]> toResponseEntity(ProxySession session,
                                                   VenueConfig venueConfig,
                                                   VenueRewriteStrategy rewriteStrategy,
                                                   HttpUriRequest upstreamRequest,
                                                   HttpResponse upstreamResponse) throws IOException {
        HttpEntity entity = upstreamResponse.getEntity();
        Charset charset = resolveCharset(entity);
        String contentType = firstHeaderValue(upstreamResponse, HttpHeaders.CONTENT_TYPE);
        byte[] body = entity == null ? new byte[0] : EntityUtils.toByteArray(entity);

        String currentUpstreamUrl = upstreamRequest.getURI().toString();
        boolean html = isHtml(contentType);
        String responseLocation = firstHeaderValue(upstreamResponse, HttpHeaders.LOCATION);
        // 完了検知は HTML レスポンスのみに限定する。CSS/JS/画像等のサブリソースで URL に
        // /complete 等のサブストリングが含まれる場合の誤陽性を防ぐ (KaderuCompletionStrategy
        // は word-boundary 化済みだが、main document 以外で陽性化する余地は残しておかない)。
        boolean completed = false;
        String responseBodyForDetection = null;
        if (html) {
            responseBodyForDetection = new String(body, charset);
            completed = completionDetector.detectAndMarkComplete(
                    session,
                    currentUpstreamUrl,
                    responseLocation,
                    responseBodyForDetection);
        }

        byte[] responseBody;
        MediaType responseContentType = parseMediaType(contentType);
        if (html) {
            String rewritten = htmlRewriter.rewrite(
                    responseBodyForDetection,
                    currentUpstreamUrl,
                    session,
                    venueConfig,
                    rewriteStrategy);
            responseBody = rewritten.getBytes(StandardCharsets.UTF_8);
            responseContentType = TEXT_HTML_UTF8;
        } else {
            responseBody = body;
        }

        HttpHeaders headers = rewriteResponseHeaders(
                upstreamResponse, venueConfig, currentUpstreamUrl, session.getToken());
        if (completed) {
            headers.set(COMPLETED_HEADER, "true");
        }
        if (responseContentType != null) {
            headers.setContentType(responseContentType);
        }
        headers.set(REFERRER_POLICY_HEADER, REFERRER_POLICY_NO_REFERRER);
        if (html) {
            // CSP は HTML レスポンスのみに付与する (CSS/JS/画像には不要)。
            headers.set(CSP_HEADER, buildCspHeader(venueConfig.baseUrl()));
        }
        headers.setContentLength(responseBody.length);

        int status = upstreamResponse.getStatusLine() == null
                ? HttpStatus.OK.value()
                : upstreamResponse.getStatusLine().getStatusCode();

        return ResponseEntity.status(status)
                .headers(headers)
                .body(responseBody);
    }

    private HttpUriRequest buildUpstreamRequest(HttpServletRequest request, VenueConfig venueConfig) {
        String targetUrl = resolveTargetUrl(request, venueConfig);
        RequestBuilder builder = RequestBuilder.create(request.getMethod()).setUri(targetUrl);

        copyRequestHeaders(request, builder);
        byte[] body = readRequestBody(request);
        if (body.length > 0 && mayHaveRequestBody(request.getMethod())) {
            builder.setEntity(newRequestEntity(body, request.getContentType()));
        }

        return builder.build();
    }

    private String resolveTargetUrl(HttpServletRequest request, VenueConfig venueConfig) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        if (!uri.startsWith(FETCH_PREFIX)) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.SCRIPT_ERROR,
                    venueConfig.venue(),
                    "Invalid proxy fetch path: " + uri);
        }

        String path = uri.substring(FETCH_PREFIX.length());
        if (path.isBlank()) {
            path = "/";
        } else if (!path.startsWith("/")) {
            path = "/" + path;
        }

        // path traversal / 二重スラッシュ経由の host swap を上流に渡さないためのサニタイズ。
        // {@link HttpServletRequest#getRequestURI()} は decode されない素のパスなので、
        // ここでは encoded 形式 (例 %2e%2e) も含めてブロックする。
        if (containsTraversal(path)) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    venueConfig.venue(),
                    "Proxy fetch path contains forbidden traversal sequence: " + path);
        }

        String query = stripTokenFromQuery(request.getQueryString());
        return venueConfig.baseUrl() + path + (query.isEmpty() ? "" : "?" + query);
    }

    private static boolean containsTraversal(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("..")
                || lower.contains("//")
                || lower.contains("%2e%2e")
                || lower.contains("%2f%2f");
    }

    private void copyRequestHeaders(HttpServletRequest request, RequestBuilder builder) {
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (REQUEST_HEADERS_SKIP.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                builder.addHeader(name, values.nextElement());
            }
        }
    }

    private HttpHeaders rewriteResponseHeaders(HttpResponse upstreamResponse, VenueConfig venueConfig,
                                               String currentUpstreamUrl, String token) {
        HttpHeaders headers = new HttpHeaders();
        for (Header header : upstreamResponse.getAllHeaders()) {
            String name = header.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (RESPONSE_HEADERS_SKIP.contains(lower)) {
                continue;
            }
            if (HttpHeaders.LOCATION.equalsIgnoreCase(name)) {
                // Location ヘッダの相対 URL も会場ページ基準で絶対化してからプロキシ URL に変換する。
                headers.add(HttpHeaders.LOCATION,
                        htmlRewriter.rewriteUrl(header.getValue(), venueConfig.baseUrl(),
                                currentUpstreamUrl, token));
            } else {
                headers.add(name, header.getValue());
            }
        }
        return headers;
    }

    private ProxySession requireSession(String token) {
        return sessionStore.get(token)
                .orElseThrow(() -> new VenueReservationProxyException(
                        VenueReservationProxyException.TIMEOUT,
                        null,
                        "Proxy session not found or expired"));
    }

    /**
     * createSession のリクエストパラメータが対象 practice_session と整合するかを検証する。
     * 不整合な場合は {@link VenueReservationProxyException#INVALID_REQUEST} を投げる。
     *
     * <p>これにより、ADMIN が自団体内の sessionId を指定しつつ別日・別会場の予約を完了させて
     * 任意の練習日を「予約済み」状態にする攻撃を防ぐ。</p>
     */
    private void validateRequestMatchesPracticeSession(CreateVenueProxySessionRequest request,
                                                       PracticeSession practiceSession) {
        VenueId venue = request.getVenue();
        Long sessionVenueId = practiceSession.getVenueId();
        if (sessionVenueId == null) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    venue,
                    "練習日に会場が登録されていません: practiceSessionId=" + practiceSession.getId());
        }

        // Phase 1 では KADERU のみ。HIGASHI 用の会場ID対応は Phase 2 で追加する。
        if (venue == VenueId.KADERU && !AdjacentRoomConfig.isKaderuRoom(sessionVenueId)) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    venue,
                    "練習日の会場が KADERU 対象ではありません: practiceSessionId="
                            + practiceSession.getId() + " venueId=" + sessionVenueId);
        }

        if (request.getDate() == null || !request.getDate().equals(practiceSession.getSessionDate())) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    venue,
                    "リクエスト日付と練習日の日付が一致しません: request=" + request.getDate()
                            + " session=" + practiceSession.getSessionDate());
        }

        String expectedAdjacentRoom = AdjacentRoomConfig.getAdjacentRoomName(sessionVenueId);
        if (expectedAdjacentRoom == null || !expectedAdjacentRoom.equals(request.getRoomName())) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    venue,
                    "リクエスト部屋名と隣室名が一致しません: request=" + request.getRoomName()
                            + " expected=" + expectedAdjacentRoom);
        }

        // Phase 1 では夜間スロット (2) のみ自動予約対象。朝/昼スロットを ADMIN+ 権限で確保
        // できないようにサーバ側で明示的にブロックする (要件定義書 §2 / venues/kaderu.md §4)。
        if (request.getSlotIndex() != EXPECTED_NIGHT_SLOT) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    venue,
                    "slotIndex は " + EXPECTED_NIGHT_SLOT + " (夜間) のみ許可されます: request="
                            + request.getSlotIndex());
        }
    }

    /**
     * createSession の {@code returnUrl} を検証する。
     *
     * <p>{@code returnUrl} は banner の {@code window.location.href} と
     * {@code window.opener.postMessage(targetOrigin)} に渡されるため、
     * {@code javascript:} / {@code data:} 等のスキームが入ると DOM 形 self-XSS に繋がる。
     * 以下を全て満たす必要がある:</p>
     * <ul>
     *   <li>未指定 (null/blank) は OK (banner は同一オリジン構成にフォールバック)</li>
     *   <li>scheme は {@code http} または {@code https}</li>
     *   <li>host は {@code app.cors.allowed-origins} に列挙された origin のいずれかと一致</li>
     * </ul>
     */
    private void validateReturnUrl(CreateVenueProxySessionRequest request) {
        String returnUrl = request.getReturnUrl();
        if (returnUrl == null || returnUrl.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = new URI(returnUrl);
        } catch (URISyntaxException e) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    request.getVenue(),
                    "returnUrl の形式が不正です: " + returnUrl);
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    request.getVenue(),
                    "returnUrl の scheme は http または https のみ許可されます: " + returnUrl);
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    request.getVenue(),
                    "returnUrl に host がありません: " + returnUrl);
        }
        if (!allowedReturnUrlHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.INVALID_REQUEST,
                    request.getVenue(),
                    "returnUrl の host が allowed-origins に含まれていません: " + host);
        }
    }

    private void ensureVenueReady(VenueId venue) {
        if (venue == null || !isVenueEnabled(venue)
                || !clients.containsKey(venue)
                || !venueConfigs.containsKey(venue)
                || !rewriteStrategies.containsKey(venue)) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.VENUE_NOT_SUPPORTED,
                    venue,
                    "Venue reservation proxy is not supported for venue: " + venue);
        }
    }

    private boolean isVenueEnabled(VenueId venue) {
        if (!proxyConfig.isEnabled() || venue == null) {
            return false;
        }
        VenueReservationProxyConfig.VenueProperties props =
                proxyConfig.getVenues().get(toConfigKey(venue));
        return props != null && props.isEnabled();
    }

    private VenueReservationClient clientFor(VenueId venue) {
        VenueReservationClient client = clients.get(venue);
        if (client == null) {
            throw unsupported(venue, "No VenueReservationClient registered for venue: " + venue);
        }
        return client;
    }

    private VenueConfig configFor(VenueId venue) {
        VenueConfig config = venueConfigs.get(venue);
        if (config == null) {
            throw unsupported(venue, "No VenueConfig registered for venue: " + venue);
        }
        return config;
    }

    private VenueRewriteStrategy rewriteStrategyFor(VenueId venue) {
        VenueRewriteStrategy strategy = rewriteStrategies.get(venue);
        if (strategy == null) {
            throw unsupported(venue, "No VenueRewriteStrategy registered for venue: " + venue);
        }
        return strategy;
    }

    private VenueReservationProxyException unsupported(VenueId venue, String message) {
        return new VenueReservationProxyException(
                VenueReservationProxyException.VENUE_NOT_SUPPORTED,
                venue,
                message);
    }

    /**
     * {@code app.cors.allowed-origins} (例: {@code http://localhost:5173,https://app.example.com})
     * を解析し、host のセットに変換する。空 / 不正値はスキップしつつ警告ログを残す。
     */
    private static Set<String> parseAllowedHosts(String allowedOriginsRaw) {
        Set<String> hosts = new HashSet<>();
        if (allowedOriginsRaw == null || allowedOriginsRaw.isBlank()) {
            return hosts;
        }
        for (String origin : allowedOriginsRaw.split(",")) {
            String trimmed = origin.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                URI uri = new URI(trimmed);
                String host = uri.getHost();
                if (host != null && !host.isBlank()) {
                    hosts.add(host.toLowerCase(Locale.ROOT));
                } else {
                    log.warn("CORS allowed-origins entry has no host: {}", trimmed);
                }
            } catch (URISyntaxException e) {
                log.warn("CORS allowed-origins entry is not a valid URI: {}", trimmed);
            }
        }
        return hosts;
    }

    private static <T> Map<VenueId, T> toVenueMap(List<T> beans,
                                                  Function<T, VenueId> venueExtractor,
                                                  String label) {
        Map<VenueId, T> map = new EnumMap<>(VenueId.class);
        for (T bean : beans) {
            VenueId venue = venueExtractor.apply(bean);
            T previous = map.put(venue, bean);
            if (previous != null) {
                log.warn("Duplicate {} registered for venue={}, kept={}, replaced={}",
                        label, venue, bean.getClass().getName(), previous.getClass().getName());
            }
        }
        return map;
    }

    private static String toConfigKey(VenueId venue) {
        return venue.name().toLowerCase(Locale.ROOT);
    }

    private static boolean mayHaveRequestBody(String method) {
        if (method == null) {
            return false;
        }
        String m = method.toUpperCase(Locale.ROOT);
        return !m.equals("GET") && !m.equals("HEAD") && !m.equals("OPTIONS") && !m.equals("TRACE");
    }

    private static byte[] readRequestBody(HttpServletRequest request) {
        try {
            return StreamUtils.copyToByteArray(request.getInputStream());
        } catch (IOException e) {
            throw new VenueReservationProxyException(
                    VenueReservationProxyException.SCRIPT_ERROR,
                    null,
                    "Failed to read proxy request body",
                    e);
        }
    }

    private static ByteArrayEntity newRequestEntity(byte[] body, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return new ByteArrayEntity(body);
        }
        try {
            return new ByteArrayEntity(body, ContentType.parse(contentType));
        } catch (RuntimeException e) {
            ByteArrayEntity entity = new ByteArrayEntity(body);
            entity.setContentType(contentType);
            return entity;
        }
    }

    private static String stripTokenFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(query.length());
        for (String part : query.split("&", -1)) {
            if (part.isEmpty() || isTokenQueryPart(part)) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(part);
        }
        return out.toString();
    }

    private static boolean isTokenQueryPart(String part) {
        int eq = part.indexOf('=');
        String key = eq >= 0 ? part.substring(0, eq) : part;
        try {
            return "token".equals(URLDecoder.decode(key, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return "token".equals(key);
        }
    }

    private static Charset resolveCharset(HttpEntity entity) {
        if (entity == null) {
            return StandardCharsets.UTF_8;
        }
        ContentType contentType = ContentType.get(entity);
        Charset charset = contentType == null ? null : contentType.getCharset();
        return charset == null ? StandardCharsets.UTF_8 : charset;
    }

    private static String firstHeaderValue(HttpResponse response, String name) {
        Header header = response.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    private static boolean isHtml(String contentType) {
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase(Locale.ROOT);
        return lower.contains("text/html") || lower.contains("application/xhtml+xml");
    }

    private static MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
