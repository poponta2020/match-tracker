package com.karuta.matchtracker.interceptor;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.UnauthorizedException;
import com.karuta.matchtracker.service.AuthTokenService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * RoleCheckInterceptor の単体テスト（auth-tokenization でトークン認証へ全面改修）
 *
 * <p>旧実装は {@code X-User-Role} / {@code X-User-Id} ヘッダーの自己申告を信用していたため、
 * このテストもヘッダーの解釈を検証していた。現在はトークンだけが根拠なので、
 * 検証対象を「トークンの有無・妥当性」「ヘッダーが無視されること」
 * 「公開許可リスト」「ロール判定」に置き換えている。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RoleCheckInterceptor 単体テスト")
class RoleCheckInterceptorTest {

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private HttpServletResponse response;

    @Mock
    private HandlerMethod handlerMethod;

    @InjectMocks
    private RoleCheckInterceptor interceptor;

    private static final String VALID_TOKEN = "a".repeat(64);

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    /** VALID_TOKEN だけを有効なトークンとして解決させる */
    private void givenValidToken(Player player) {
        when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());
        when(authTokenService.verify(VALID_TOKEN)).thenReturn(Optional.of(player));
    }

    private Player player(Long id, Role role, Long adminOrganizationId) {
        return Player.builder().id(id).role(role).adminOrganizationId(adminOrganizationId).build();
    }

    private void givenRequiredRoles(Role... roles) {
        RequireRole annotation = new RequireRole() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequireRole.class;
            }

            @Override
            public Role[] value() {
                return roles;
            }
        };
        when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(annotation);
    }

    @Nested
    @DisplayName("認証（トークンが唯一の根拠）")
    class AuthenticationTests {

        @Test
        @DisplayName("トークンが無ければ 401（@RequireRole の有無によらない = deny by default）")
        void testNoToken_ThrowsUnauthorized() {
            when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());
            when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);

            MockHttpServletRequest request = request("GET", "/api/players/1");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("無効・期限切れ・失効済みトークン（verifyがemptyを返す）は 401")
        void testInvalidToken_ThrowsUnauthorized() {
            when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());

            MockHttpServletRequest request = request("GET", "/api/players/1");
            request.addHeader("Authorization", "Bearer expired-or-revoked-token");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("Bearer 形式でない Authorization ヘッダーは 401")
        void testNonBearerAuthorization_ThrowsUnauthorized() {
            when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());

            MockHttpServletRequest request = request("GET", "/api/players/1");
            request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("有効なトークンならリクエスト属性に主体がセットされる（AC-4）")
        void testValidToken_SetsRequestAttributes() throws Exception {
            givenValidToken(player(42L, Role.PLAYER, null));
            when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);

            MockHttpServletRequest request = request("GET", "/api/players/1");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
            assertThat(request.getAttribute("currentUserId")).isEqualTo(42L);
            assertThat(request.getAttribute("currentUserRole")).isEqualTo("PLAYER");
        }

        @Test
        @DisplayName("ADMIN なら adminOrganizationId もセットされる（AC-4）")
        void testValidAdminToken_SetsAdminOrganizationId() throws Exception {
            givenValidToken(player(7L, Role.ADMIN, 2L));
            when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);

            MockHttpServletRequest request = request("GET", "/api/practice-sessions");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            interceptor.preHandle(request, response, handlerMethod);

            assertThat(request.getAttribute("currentUserId")).isEqualTo(7L);
            assertThat(request.getAttribute("currentUserRole")).isEqualTo("ADMIN");
            assertThat(request.getAttribute("adminOrganizationId")).isEqualTo(2L);
        }
    }

    @Nested
    @DisplayName("ヘッダー詐称が通らないこと（AC-2 / AC-3）")
    class HeaderSpoofingTests {

        @Test
        @DisplayName("AC-2: X-User-Role: SUPER_ADMIN を付けてもトークンが無ければ 401")
        void testSpoofedRoleHeaderWithoutToken_ThrowsUnauthorized() {
            when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());

            MockHttpServletRequest request = request("DELETE", "/api/players/1");
            request.addHeader("X-User-Role", "SUPER_ADMIN");
            request.addHeader("X-User-Id", "1");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("AC-3: トークンと矛盾する X-User-Role / X-User-Id を送ってもトークン由来の主体で判定される")
        void testConflictingHeaders_AreIgnored() throws Exception {
            // トークンの主体は PLAYER(42)。ヘッダーでは SUPER_ADMIN(1) を名乗る
            givenValidToken(player(42L, Role.PLAYER, null));
            when(handlerMethod.getMethodAnnotation(RequireRole.class)).thenReturn(null);

            MockHttpServletRequest request = request("GET", "/api/players/1");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            request.addHeader("X-User-Role", "SUPER_ADMIN");
            request.addHeader("X-User-Id", "1");

            interceptor.preHandle(request, response, handlerMethod);

            // ヘッダーは完全に無視され、トークン由来の値だけが入る
            assertThat(request.getAttribute("currentUserId")).isEqualTo(42L);
            assertThat(request.getAttribute("currentUserRole")).isEqualTo("PLAYER");
        }

        @Test
        @DisplayName("AC-3: 詐称ヘッダーでは権限も昇格しない（PLAYER のまま SUPER_ADMIN 専用EPは 403）")
        void testConflictingHeaders_DoNotEscalateRole() {
            givenValidToken(player(42L, Role.PLAYER, null));
            givenRequiredRoles(Role.SUPER_ADMIN);

            MockHttpServletRequest request = request("DELETE", "/api/players/1");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);
            request.addHeader("X-User-Role", "SUPER_ADMIN");

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("公開許可リスト（AC-8）")
    class PublicEndpointTests {

        @Test
        @DisplayName("POST /api/players/login は未認証で通る")
        void testLoginIsPublic() throws Exception {
            assertThat(interceptor.preHandle(request("POST", "/api/players/login"), response, handlerMethod))
                    .isTrue();
        }

        @Test
        @DisplayName("GET /api/organizations は未認証で通る（招待登録画面が呼ぶ）")
        void testOrganizationsListIsPublic() throws Exception {
            assertThat(interceptor.preHandle(request("GET", "/api/organizations"), response, handlerMethod))
                    .isTrue();
        }

        @Test
        @DisplayName("GET /api/organizations/players/1 は公開ではない（完全一致なので巻き込まない）")
        void testOrganizationsSubPathIsNotPublic() {
            when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interceptor.preHandle(
                    request("GET", "/api/organizations/players/1"), response, handlerMethod))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("POST /api/organizations は公開ではない（メソッド違いを巻き込まない）")
        void testOrganizationsPostIsNotPublic() {
            when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interceptor.preHandle(
                    request("POST", "/api/organizations"), response, handlerMethod))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("招待リンクの検証・公開登録は未認証で通る")
        void testInviteTokenEndpointsArePublic() throws Exception {
            assertThat(interceptor.preHandle(
                    request("GET", "/api/invite-tokens/validate/abc-123"), response, handlerMethod)).isTrue();
            assertThat(interceptor.preHandle(
                    request("POST", "/api/invite-tokens/register"), response, handlerMethod)).isTrue();
        }

        @Test
        @DisplayName("招待トークンの発行（POST /api/invite-tokens）は公開ではない")
        void testInviteTokenCreationIsNotPublic() {
            when(authTokenService.verify(nullable(String.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interceptor.preHandle(
                    request("POST", "/api/invite-tokens"), response, handlerMethod))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("会場予約プロキシの view / fetch は未認証で通る（capabilityトークンで保護済み）")
        void testVenueReservationProxyIsPublic() throws Exception {
            assertThat(interceptor.preHandle(
                    request("GET", "/api/venue-reservation-proxy/view"), response, handlerMethod)).isTrue();
            assertThat(interceptor.preHandle(
                    request("POST", "/api/venue-reservation-proxy/fetch/anything"), response, handlerMethod))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("認可（@RequireRole によるロール判定）")
    class AuthorizationTests {

        @Test
        @DisplayName("必要なロールを持っていれば通る")
        void testMatchingRole_IsAllowed() throws Exception {
            givenValidToken(player(1L, Role.SUPER_ADMIN, null));
            givenRequiredRoles(Role.SUPER_ADMIN);

            MockHttpServletRequest request = request("DELETE", "/api/players/2");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
        }

        @Test
        @DisplayName("複数指定のいずれかに一致すれば通る")
        void testOneOfRequiredRoles_IsAllowed() throws Exception {
            givenValidToken(player(3L, Role.ADMIN, 1L));
            givenRequiredRoles(Role.SUPER_ADMIN, Role.ADMIN);

            MockHttpServletRequest request = request("POST", "/api/practice-sessions");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            assertThat(interceptor.preHandle(request, response, handlerMethod)).isTrue();
        }

        @Test
        @DisplayName("必要なロールを持たなければ 403（401 ではない = 本人は分かっている）")
        void testInsufficientRole_ThrowsForbidden() {
            givenValidToken(player(9L, Role.PLAYER, null));
            givenRequiredRoles(Role.SUPER_ADMIN, Role.ADMIN);

            MockHttpServletRequest request = request("POST", "/api/practice-sessions");
            request.addHeader("Authorization", "Bearer " + VALID_TOKEN);

            assertThatThrownBy(() -> interceptor.preHandle(request, response, handlerMethod))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    @Nested
    @DisplayName("ハンドラー種別")
    class HandlerTypeTests {

        @Test
        @DisplayName("HandlerMethod でないハンドラー（静的リソース・CORSプリフライト）は素通しする")
        void testNonHandlerMethod_IsSkipped() throws Exception {
            assertThat(interceptor.preHandle(request("OPTIONS", "/api/players"), response, new Object()))
                    .isTrue();
        }
    }
}
