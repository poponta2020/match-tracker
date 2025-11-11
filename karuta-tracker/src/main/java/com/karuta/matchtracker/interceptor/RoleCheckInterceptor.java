package com.karuta.matchtracker.interceptor;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * ロールベースの権限チェックを行うインターセプター
 *
 * @RequireRole アノテーションが付与されたメソッドに対して、
 * リクエストヘッダーから取得したユーザーのロールをチェックします。
 */
@Component
@Slf4j
public class RoleCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // ハンドラーがHandlerMethodでない場合はスキップ
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);

        // @RequireRole アノテーションがない場合はスキップ
        if (requireRole == null) {
            return true;
        }

        // リクエストヘッダーからユーザーロールを取得
        // TODO: 実際の認証システムと統合する際は、セッションやJWTから取得するように変更
        String userRoleHeader = request.getHeader("X-User-Role");

        if (userRoleHeader == null || userRoleHeader.isEmpty()) {
            log.warn("No user role found in request header for endpoint: {}", request.getRequestURI());
            throw new ForbiddenException("認証が必要です");
        }

        Role userRole;
        try {
            userRole = Role.valueOf(userRoleHeader);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid role '{}' in request header for endpoint: {}", userRoleHeader, request.getRequestURI());
            throw new ForbiddenException("不正なロール情報です");
        }

        // 必要なロールのチェック
        Role[] requiredRoles = requireRole.value();
        boolean hasPermission = Arrays.asList(requiredRoles).contains(userRole);

        if (!hasPermission) {
            log.warn("User with role '{}' attempted to access endpoint requiring one of: {} (URI: {})",
                    userRole, Arrays.toString(requiredRoles), request.getRequestURI());
            throw new ForbiddenException("この操作を実行する権限がありません");
        }

        log.debug("User with role '{}' granted access to endpoint: {}", userRole, request.getRequestURI());
        return true;
    }
}
