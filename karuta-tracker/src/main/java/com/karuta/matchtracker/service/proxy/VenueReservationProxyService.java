package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import com.karuta.matchtracker.dto.CreateVenueProxySessionRequest;
import com.karuta.matchtracker.dto.CreateVenueProxySessionResponse;
import com.karuta.matchtracker.service.proxy.venue.VenueConfig;
import com.karuta.matchtracker.service.proxy.venue.VenueRewriteStrategy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.EnumMap;
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
    private static final MediaType TEXT_HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

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
    private final Map<VenueId, VenueReservationClient> clients;
    private final Map<VenueId, VenueConfig> venueConfigs;
    private final Map<VenueId, VenueRewriteStrategy> rewriteStrategies;

    public VenueReservationProxyService(VenueReservationProxyConfig proxyConfig,
                                        VenueReservationSessionStore sessionStore,
                                        VenueReservationHtmlRewriter htmlRewriter,
                                        VenueReservationCompletionDetector completionDetector,
                                        List<VenueReservationClient> clients,
                                        List<VenueConfig> venueConfigs,
                                        List<VenueRewriteStrategy> rewriteStrategies) {
        this.proxyConfig = proxyConfig;
        this.sessionStore = sessionStore;
        this.htmlRewriter = htmlRewriter;
        this.completionDetector = completionDetector;
        this.clients = toVenueMap(clients, VenueReservationClient::venue, "VenueReservationClient");
        this.venueConfigs = toVenueMap(venueConfigs, VenueConfig::venue, "VenueConfig");
        this.rewriteStrategies = toVenueMap(rewriteStrategies, VenueRewriteStrategy::venue, "VenueRewriteStrategy");
    }

    public CreateVenueProxySessionResponse createSession(CreateVenueProxySessionRequest request) {
        VenueId venue = request == null ? null : request.getVenue();
        ensureVenueReady(venue);

        ProxySession session = sessionStore.createSession(
                venue,
                request.getPracticeSessionId(),
                request.getRoomName(),
                request.getDate(),
                request.getSlotIndex());

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

        String rewritten = htmlRewriter.rewrite(
                html,
                session,
                configFor(session.getVenue()),
                rewriteStrategyFor(session.getVenue()));

        return ResponseEntity.ok()
                .contentType(TEXT_HTML_UTF8)
                .body(rewritten);
    }

    public ResponseEntity<byte[]> fetch(String token, HttpServletRequest request) {
        ProxySession session = requireSession(token);
        sessionStore.touch(token);

        VenueId venue = session.getVenue();
        VenueConfig venueConfig = configFor(venue);
        VenueRewriteStrategy rewriteStrategy = rewriteStrategyFor(venue);

        HttpUriRequest upstreamRequest = buildUpstreamRequest(request, venueConfig);
        HttpResponse upstreamResponse = clientFor(venue).fetch(session, upstreamRequest);

        try {
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

        boolean html = isHtml(contentType);
        String responseBodyForDetection = html ? new String(body, charset) : null;
        String responseLocation = firstHeaderValue(upstreamResponse, HttpHeaders.LOCATION);
        boolean completed = completionDetector.detectAndMarkComplete(
                session,
                upstreamRequest.getURI().toString(),
                responseLocation,
                responseBodyForDetection);

        byte[] responseBody;
        MediaType responseContentType = parseMediaType(contentType);
        if (html) {
            String rewritten = htmlRewriter.rewrite(
                    responseBodyForDetection,
                    session,
                    venueConfig,
                    rewriteStrategy);
            responseBody = rewritten.getBytes(StandardCharsets.UTF_8);
            responseContentType = TEXT_HTML_UTF8;
        } else {
            responseBody = body;
        }

        HttpHeaders headers = rewriteResponseHeaders(upstreamResponse, venueConfig, session.getToken());
        if (completed) {
            headers.set(COMPLETED_HEADER, "true");
        }
        if (responseContentType != null) {
            headers.setContentType(responseContentType);
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

        String query = stripTokenFromQuery(request.getQueryString());
        return venueConfig.baseUrl() + path + (query.isEmpty() ? "" : "?" + query);
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

    private HttpHeaders rewriteResponseHeaders(HttpResponse upstreamResponse, VenueConfig venueConfig, String token) {
        HttpHeaders headers = new HttpHeaders();
        for (Header header : upstreamResponse.getAllHeaders()) {
            String name = header.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (RESPONSE_HEADERS_SKIP.contains(lower)) {
                continue;
            }
            if (HttpHeaders.LOCATION.equalsIgnoreCase(name)) {
                headers.add(HttpHeaders.LOCATION,
                        htmlRewriter.rewriteUrl(header.getValue(), venueConfig.baseUrl(), token));
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
