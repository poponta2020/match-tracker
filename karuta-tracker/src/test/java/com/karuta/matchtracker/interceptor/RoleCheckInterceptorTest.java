package com.karuta.matchtracker.interceptor;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * RoleCheckInterceptorの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RoleCheckInterceptor 単体テスト")
class RoleCheckInterceptorTest {

    @InjectMocks
    private RoleCheckInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    // ===== 正常系テスト =====

    @Test
    @DisplayName("@RequireRoleアノテーションがない場合はスキップしてtrueを返す")
    void testPreHandle_NoAnnotation_ReturnsTrue() throws Exception {
        // Given
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMINロールでSUPER_ADMIN必須エンドポイントにアクセスできる")
    void testPreHandle_SuperAdminAccess_Granted() throws Exception {
        // Given
        RequireRole requireRole = createRequireRoleAnnotation(Role.SUPER_ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("SUPER_ADMIN");

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ADMINロールでADMIN必須エンドポイントにアクセスできる")
    void testPreHandle_AdminAccess_Granted() throws Exception {
        // Given
        RequireRole requireRole = createRequireRoleAnnotation(Role.ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("ADMIN");

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("複数ロール指定時にいずれかがマッチすればアクセスできる")
    void testPreHandle_MultipleRoles_AnyMatches() throws Exception {
        // Given - SUPER_ADMINまたはADMINが必要
        RequireRole requireRole = createRequireRoleAnnotation(Role.SUPER_ADMIN, Role.ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("ADMIN");

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("SUPER_ADMINはADMIN必須エンドポイントにもアクセスできる（複数ロール設定時）")
    void testPreHandle_SuperAdminAccessToAdminEndpoint_WhenMultipleRolesAllowed() throws Exception {
        // Given - SUPER_ADMINまたはADMINが必要なエンドポイント
        RequireRole requireRole = createRequireRoleAnnotation(Role.SUPER_ADMIN, Role.ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("SUPER_ADMIN");

        // When
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // Then
        assertThat(result).isTrue();
    }

    // ===== 異常系テスト =====

    @Test
    @DisplayName("X-User-Roleヘッダがない場合はForbiddenExceptionが発生")
    void testPreHandle_NoRoleHeader_ThrowsForbidden() throws Exception {
        // Given
        RequireRole requireRole = createRequireRoleAnnotation(Role.ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("認証が必要です");
    }

    @Test
    @DisplayName("X-User-Roleヘッダが空の場合はForbiddenExceptionが発生")
    void testPreHandle_EmptyRoleHeader_ThrowsForbidden() throws Exception {
        // Given
        RequireRole requireRole = createRequireRoleAnnotation(Role.ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("");

        // When & Then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("認証が必要です");
    }

    @Test
    @DisplayName("不正なロール値の場合はForbiddenExceptionが発生")
    void testPreHandle_InvalidRole_ThrowsForbidden() throws Exception {
        // Given
        RequireRole requireRole = createRequireRoleAnnotation(Role.ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("INVALID_ROLE");

        // When & Then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("不正なロール情報です");
    }

    @Test
    @DisplayName("PLAYERロールでADMIN必須エンドポイントにアクセスするとForbiddenExceptionが発生")
    void testPreHandle_InsufficientPermission_ThrowsForbidden() throws Exception {
        // Given
        RequireRole requireRole = createRequireRoleAnnotation(Role.ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("PLAYER");

        // When & Then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("この操作を実行する権限がありません");
    }

    @Test
    @DisplayName("PLAYERロールでSUPER_ADMIN必須エンドポイントにアクセスするとForbiddenExceptionが発生")
    void testPreHandle_PlayerAccessToSuperAdminEndpoint_ThrowsForbidden() throws Exception {
        // Given
        RequireRole requireRole = createRequireRoleAnnotation(Role.SUPER_ADMIN);
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(requireRole);
        when(request.getHeader("X-User-Role")).thenReturn("PLAYER");

        // When & Then
        assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("この操作を実行する権限がありません");
    }

    // ===== 境界値テスト =====

    @Test
    @DisplayName("HandlerMethod以外のハンドラの場合はスキップしてtrueを返す")
    void testPreHandle_NonHandlerMethod_ReturnsTrue() throws Exception {
        // Given - HandlerMethodではないオブジェクト
        Object nonHandlerMethod = new Object();

        // When
        boolean result = interceptor.preHandle(request, response, nonHandlerMethod);

        // Then
        assertThat(result).isTrue();
    }

    // ===== ヘルパーメソッド =====

    /**
     * テスト用の@RequireRoleアノテーションを作成する
     */
    private RequireRole createRequireRoleAnnotation(Role... roles) {
        return new RequireRole() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequireRole.class;
            }

            @Override
            public Role[] value() {
                return roles;
            }
        };
    }
}
