package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.CreateVenueProxySessionRequest;
import com.karuta.matchtracker.dto.CreateVenueProxySessionResponse;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyException;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 会場予約リバースプロキシのエンドポイント。
 *
 * <p>Phase 1 では KADERU のみ有効。Controller は認可・入力受け渡し・例外レスポンス整形に限定し、
 * 会場別 dispatch と HTML/ヘッダ書き換えは {@link VenueReservationProxyService} に委譲する。</p>
 *
 * <p><strong>認可モデル:</strong></p>
 * <ul>
 *   <li>{@code POST /session}: ヘッダー認証 ({@code X-User-Role}/{@code X-User-Id}) + {@code @RequireRole(ADMIN+)}。
 *       ここでサーバ側で短命 (15分) なプロキシ token を発行する。</li>
 *   <li>{@code GET /view} / {@code ANY /fetch/**}: token 自体を capability として検証する。
 *       これらはブラウザの新規タブでの通常 GET 遷移として呼ばれるため、API クライアント
 *       interceptor で付与される認可ヘッダーは届かない。token は UUID v4 (122 bit ランダム)
 *       かつ {@code SessionStore} に登録されているもののみ有効で、Referer 経由の漏洩を防ぐため
 *       Service 側で {@code Referrer-Policy: no-referrer} を付与する。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/venue-reservation-proxy")
@RequiredArgsConstructor
@Slf4j
public class VenueReservationProxyController {

    private final VenueReservationProxyService venueReservationProxyService;

    @PostMapping("/session")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<CreateVenueProxySessionResponse> createSession(
            @Valid @RequestBody CreateVenueProxySessionRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/venue-reservation-proxy/session: venue={} practiceSessionId={} room={} date={} slot={}",
                request.getVenue(), request.getPracticeSessionId(), request.getRoomName(),
                request.getDate(), request.getSlotIndex());
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        return ResponseEntity.ok(
                venueReservationProxyService.createSession(request, role, adminOrgId));
    }

    /**
     * プロキシ予約画面 (申込トレイ HTML を書き換えたもの) を返す。
     * 認可は {@code token} (capability) のみ。{@code @RequireRole} は付けない。
     */
    @GetMapping("/view")
    public ResponseEntity<String> view(@RequestParam String token) {
        return venueReservationProxyService.view(token);
    }

    /**
     * 会場サイトへの中継エンドポイント。
     * 認可は {@code token} (capability) のみ。{@code @RequireRole} は付けない。
     *
     * <p><strong>token は {@link #extractTokenFromQuery} で URL クエリ文字列から手動で抽出する</strong>。
     * {@code @RequestParam} を使うと Spring が {@code request.getParameterValues("token")} を呼び、
     * Tomcat が {@code application/x-www-form-urlencoded} POST のリクエストボディをパースして
     * parameter map に統合してしまう。これによりリクエストボディの InputStream が枯渇し、
     * 後続の {@link com.karuta.matchtracker.service.proxy.VenueReservationProxyService#fetch}
     * 内の {@code readRequestBody} が空 body を読み取り、Kaderu に空 POST が転送されて
     * トップ画面に飛ばされる (Issue #573)。</p>
     */
    @RequestMapping("/fetch/**")
    public ResponseEntity<byte[]> fetch(HttpServletRequest request) {
        String token = extractTokenFromQuery(request.getQueryString());
        return venueReservationProxyService.fetch(token, request);
    }

    /**
     * クエリ文字列から {@code token} の値を取り出す。
     * {@code request.getParameter*} 系メソッドはリクエストボディの parsing を誘発するので
     * 使ってはいけない。
     */
    static String extractTokenFromQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        for (String pair : queryString.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = pair.substring(0, eq);
            if ("token".equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @ExceptionHandler(VenueReservationProxyException.class)
    public ResponseEntity<VenueReservationProxyErrorResponse> handleVenueReservationProxyException(
            VenueReservationProxyException ex) {
        HttpStatus status = statusFor(ex);
        if (status.is5xxServerError()) {
            log.error("Venue reservation proxy error: code={} venue={} message={}",
                    ex.getErrorCode(), ex.getVenue(), ex.getMessage(), ex);
        } else {
            log.warn("Venue reservation proxy request rejected: code={} venue={} message={}",
                    ex.getErrorCode(), ex.getVenue(), ex.getMessage());
        }
        return ResponseEntity.status(status)
                .body(new VenueReservationProxyErrorResponse(
                        ex.getErrorCode(),
                        ex.getMessage(),
                        ex.getVenue()));
    }

    private static HttpStatus statusFor(VenueReservationProxyException ex) {
        if (VenueReservationProxyException.SCRIPT_ERROR.equals(ex.getErrorCode())) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (VenueReservationProxyException.REQUEST_TOO_LARGE.equals(ex.getErrorCode())) {
            return HttpStatus.PAYLOAD_TOO_LARGE;
        }
        return HttpStatus.BAD_REQUEST;
    }

    public record VenueReservationProxyErrorResponse(
            String errorCode,
            String message,
            VenueId venue
    ) {
    }
}
