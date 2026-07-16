package com.karuta.matchtracker.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * ワーカーAPI（{@code /api/line-chat-worker/**}）専用のサービストークン認証インターセプター
 * （line-chat-reserve-broadcast タスク3・AC-2）。
 *
 * <p>ヘッダ {@code X-Service-Token} を環境変数 {@code LINE_CHAT_WORKER_TOKEN} と<b>定数時間比較</b>する。
 * <ul>
 *   <li>env 未設定/空 → サービス無効として全拒否（401）</li>
 *   <li>ヘッダ無し/空 → 401</li>
 *   <li>ヘッダ不一致 → 403</li>
 * </ul>
 * この認証はワーカーAPIパスにのみ適用する（{@link com.karuta.matchtracker.config.WebConfig} で登録）。
 * 既存のロール認証エンドポイントはこのインターセプターの対象外であり、サービストークンでアクセスできない。
 * 本トークン・リクエスト内容はログに出力しない。
 */
@Slf4j
@Component
public class ServiceTokenInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-Service-Token";

    private final String configuredToken;

    public ServiceTokenInterceptor(@Value("${LINE_CHAT_WORKER_TOKEN:}") String configuredToken) {
        this.configuredToken = configuredToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (configuredToken == null || configuredToken.isBlank()) {
            log.warn("Service token auth rejected: LINE_CHAT_WORKER_TOKEN が未設定（ワーカーAPI無効）");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        String provided = request.getHeader(HEADER);
        if (provided == null || provided.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        boolean matches = MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                configuredToken.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            log.warn("Service token auth rejected: 不正なトークン（URI={}）", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
