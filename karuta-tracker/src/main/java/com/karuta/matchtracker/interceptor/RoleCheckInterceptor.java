package com.karuta.matchtracker.interceptor;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.UnauthorizedException;
import com.karuta.matchtracker.service.AuthTokenService;
import com.karuta.matchtracker.util.BearerTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * 認証・認可インターセプター（auth-tokenization）
 *
 * <p><b>認証（あなたが誰か）</b>: {@code Authorization: Bearer <token>} のトークンだけを根拠にする。
 * サーバが発行・検証するため偽装できない。かつて参照していた {@code X-User-Role} /
 * {@code X-User-Id} ヘッダーは<b>一切参照しない</b>（送られてきても無視する）。
 *
 * <p><b>deny by default</b>: {@code /api/**} は既定で認証必須。{@link #PUBLIC_ENDPOINTS} の
 * 許可リストに載っているものだけを未認証で通す。{@code @RequireRole} の有無は
 * <b>ロール判定にのみ</b>使い、認証要否の判断には使わない。
 * アノテーションを付け忘れた新規エンドポイントでも穴が開かない構造にするため
 * （Issue #1103 / PR #1104 の再発防止）。
 *
 * <p><b>リクエスト属性の契約</b>: 認証を通ったリクエストには {@code currentUserId} /
 * {@code currentUserRole} / {@code adminOrganizationId} をセットする。この契約は従来のまま維持し、
 * 供給元だけをヘッダーからトークンへ差し替えている（下流 23ファイル・185箇所を無改修に保つため）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoleCheckInterceptor implements HandlerInterceptor {

    private final AuthTokenService authTokenService;

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 任意の HTTP メソッドを表す */
    private static final String ANY_METHOD = "*";

    /**
     * 未認証で通す公開エンドポイントの許可リスト。<b>これ以外の {@code /api/**} はすべて 401</b>。
     *
     * <p>ここに足すことは「誰でも叩ける口を増やす」ことを意味する。追加時は、そのエンドポイントが
     * 自前の検証（署名・capability トークン等）を持っているか、認証前に必要かを必ず確認すること。
     *
     * <p>{@code /api/line/webhook/**}（LINE 署名検証）と {@code /api/line-chat-worker/**}
     * （サービストークン認証）は {@code WebConfig} の {@code excludePathPatterns} で
     * インターセプタ自体の対象外にしているため、ここには含めない。
     * {@code /ping} と {@code /ical/**} は {@code /api/**} の外なので元々対象外。
     */
    private static final List<PublicEndpoint> PUBLIC_ENDPOINTS = List.of(
            // ログイン（認証前）
            new PublicEndpoint("POST", "/api/players/login"),
            // 招待リンクの検証・公開登録（認証前）
            new PublicEndpoint("GET", "/api/invite-tokens/validate/**"),
            new PublicEndpoint("POST", "/api/invite-tokens/register"),
            // 招待登録画面が未ログインで団体一覧を引く。
            // 完全一致にすること（/api/organizations/players/{id} 等を巻き込まないため）
            new PublicEndpoint("GET", "/api/organizations"),
            // 会場予約プロキシ（capability トークンで保護済み）
            new PublicEndpoint("GET", "/api/venue-reservation-proxy/view"),
            new PublicEndpoint(ANY_METHOD, "/api/venue-reservation-proxy/fetch/**")
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // ハンドラーが HandlerMethod でない場合はスキップ。
        // CORS プリフライト（OPTIONS）もここで通る
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 公開エンドポイントは未認証で通す（リクエスト属性はセットしない）
        if (isPublicEndpoint(request)) {
            return true;
        }

        // ---- 認証: トークンだけを根拠に本人を解決する ----
        String rawToken = BearerTokenExtractor.extract(request);
        Player player = authTokenService.verify(rawToken)
                .orElseThrow(() -> {
                    log.warn("Unauthenticated request to endpoint: {} {}",
                            request.getMethod(), request.getRequestURI());
                    return new UnauthorizedException("認証が必要です");
                });

        // ---- リクエスト属性の契約（供給元がトークンになっただけ） ----
        request.setAttribute("currentUserId", player.getId());
        request.setAttribute("currentUserRole", player.getRole().name());
        if (player.getRole() == Role.ADMIN) {
            request.setAttribute("adminOrganizationId", player.getAdminOrganizationId());
        }

        // ---- 認可: @RequireRole があればロールを検査する ----
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole != null) {
            Role[] requiredRoles = requireRole.value();
            if (!Arrays.asList(requiredRoles).contains(player.getRole())) {
                log.warn("Player {} with role '{}' attempted to access endpoint requiring one of: {} (URI: {})",
                        player.getId(), player.getRole(), Arrays.toString(requiredRoles), request.getRequestURI());
                throw new ForbiddenException("この操作を実行する権限がありません");
            }
        }

        log.debug("Player {} with role '{}' granted access to endpoint: {}",
                player.getId(), player.getRole(), request.getRequestURI());
        return true;
    }

    /**
     * リクエストが公開エンドポイント（未認証で通す）に該当するかを判定する
     */
    private boolean isPublicEndpoint(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(endpoint -> endpoint.matches(method, path));
    }

    /**
     * 公開エンドポイントの定義（HTTPメソッド＋パスパターン）
     *
     * @param method  HTTPメソッド。{@link #ANY_METHOD} なら任意
     * @param pattern Ant 形式のパスパターン
     */
    private record PublicEndpoint(String method, String pattern) {

        boolean matches(String requestMethod, String requestPath) {
            if (!ANY_METHOD.equals(method) && !method.equalsIgnoreCase(requestMethod)) {
                return false;
            }
            return PATH_MATCHER.match(pattern, requestPath);
        }
    }
}
