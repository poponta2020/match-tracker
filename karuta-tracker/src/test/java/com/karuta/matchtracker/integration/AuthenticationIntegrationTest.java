package com.karuta.matchtracker.integration;

import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.support.AuthTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証の統合テスト（auth-tokenization タスク3）
 *
 * <p>「HTTP 経由で本当に塞がっているか」を検証する。とくに以下は<b>否定的な検証</b>であり、
 * トークンを付けて回っただけの green と区別するために不可欠:
 * <ul>
 *   <li>AC-7: 許可リスト外は {@code @RequireRole} の有無によらず未認証で 401</li>
 *   <li>AC-9: {@code @RequireRole} を付けない新規エンドポイントでも既定で認証必須</li>
 * </ul>
 */
@DisplayName("認証統合テスト（deny by default）")
class AuthenticationIntegrationTest extends BaseAuthenticatedIntegrationTest {

    @Nested
    @DisplayName("AC-7: 許可リスト外は未認証で 401")
    class DenyByDefaultTests {

        @Test
        @DisplayName("旧・無保護だった書き込み系が未認証で 401（PUT /api/players/{id}）")
        void testUnprotectedPlayerUpdateIsDenied() throws Exception {
            mockMvc.perform(put("/api/players/1")
                            .contentType("application/json")
                            .content("{\"karutaClub\":\"乗っ取り\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("旧・無保護だった削除系が未認証で 401（DELETE /api/matches/{id}）")
        void testUnprotectedMatchDeleteIsDenied() throws Exception {
            mockMvc.perform(delete("/api/matches/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("旧・無保護だった参照系が未認証で 401（GET /api/players）")
        void testUnprotectedPlayerListIsDenied() throws Exception {
            mockMvc.perform(get("/api/players"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("@RequireRole 付きのエンドポイントも未認証なら 403 ではなく 401")
        void testAnnotatedEndpointWithoutTokenIsUnauthorized() throws Exception {
            mockMvc.perform(delete("/api/players/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("AC-2 / AC-3: ヘッダー詐称が通らない")
    class HeaderSpoofingTests {

        @Test
        @DisplayName("AC-2: X-User-Role: SUPER_ADMIN を付けてもトークンが無ければ 401")
        void testSpoofedSuperAdminHeaderIsRejected() throws Exception {
            mockMvc.perform(delete("/api/players/1")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("AC-3: 有効なPLAYERトークンに SUPER_ADMIN ヘッダーを添えても昇格しない（403）")
        void testConflictingHeadersDoNotEscalate() throws Exception {
            mockMvc.perform(delete("/api/players/1")
                            .header("Authorization", AuthTestSupport.bearer(42L, Role.PLAYER))
                            .header("X-User-Role", "SUPER_ADMIN")
                            .header("X-User-Id", "1"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("AC-9: @RequireRole 未付与でも既定で認証必須（構造テスト）")
    class UnannotatedEndpointTests {

        @Test
        @DisplayName("@RequireRole を付けない新規エンドポイントは未認証で 401")
        void testUnannotatedEndpointRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/__deny-by-default-probe"))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/__deny-by-default-probe"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("@RequireRole を付けない新規エンドポイントは、認証さえ通れば PLAYER でも叩ける")
        void testUnannotatedEndpointAllowsAnyAuthenticatedUser() throws Exception {
            mockMvc.perform(get("/api/__deny-by-default-probe")
                            .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("AC-8: 許可リストは未認証で従来どおり通る")
    class PublicEndpointTests {

        @Test
        @DisplayName("GET /api/organizations は未認証で 200（招待登録画面が呼ぶ）")
        void testOrganizationsListIsPublic() throws Exception {
            mockMvc.perform(get("/api/organizations"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/organizations/players/{id} は未認証では 401（完全一致で巻き込まない）")
        void testOrganizationsSubPathIsNotPublic() throws Exception {
            mockMvc.perform(get("/api/organizations/players/1"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("POST /api/players/login は未認証で到達できる（資格情報が誤りでも 401 認証エラーにはならない）")
        void testLoginIsPublic() throws Exception {
            mockMvc.perform(post("/api/players/login")
                            .contentType("application/json")
                            .content("{\"name\":\"存在しない選手\",\"password\":\"whatever\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("GET /api/invite-tokens/validate/{token} は未認証で到達できる")
        void testInviteValidateIsPublic() throws Exception {
            mockMvc.perform(get("/api/invite-tokens/validate/nonexistent-token"))
                    .andExpect(status().isNotFound());
        }
    }
}
