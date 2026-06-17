package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.MatchVideoDateCandidateDto;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.MatchVideoService;
import com.karuta.matchtracker.service.OrganizationService;
import com.karuta.matchtracker.util.OrganizationScopeResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MatchVideoController 単体テスト。
 *
 * <p>{@code date-candidates} エンドポイントの「単一所属PLAYERは既定で所属団体にスコープする」特例
 * （PR #858 WARNING 対応）を中心に、組織スコープ解決の振る舞いを検証する。
 * {@link OrganizationScopeResolver} は実体を {@link Import} し、{@link RoleCheckInterceptor} 経由で
 * セットされる {@code currentUserId} / {@code currentUserRole} / {@code adminOrganizationId} 属性に
 * 基づく解決を本物のロジックで通す（{@link MatchPairingControllerTest} と同じ流儀）。</p>
 */
@WebMvcTest(MatchVideoController.class)
@Import(OrganizationScopeResolver.class)
@DisplayName("MatchVideoController 単体テスト")
class MatchVideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MatchVideoService matchVideoService;

    @MockitoBean
    private OrganizationService organizationService;

    // RoleCheckInterceptor が依存（ADMIN の adminOrganizationId 解決等）。
    @MockitoBean
    private PlayerRepository playerRepository;

    @Autowired
    private MatchVideoController matchVideoController;

    private MatchVideoDateCandidateDto sampleCandidate(LocalDate date) {
        return MatchVideoDateCandidateDto.builder()
                .matchDate(date)
                .matchNumber(1)
                .player1Id(1L).player1Name("選手A")
                .player2Id(2L).player2Name("選手B")
                .hasResult(false)
                .registered(false)
                .build();
    }

    @Nested
    @DisplayName("GET /api/match-videos/date-candidates - 既定組織スコープ解決")
    class GetDateCandidatesTests {

        @Test
        @DisplayName("単一所属PLAYERが organizationId 未指定で呼ぶと所属団体IDでスコープされる")
        void shouldScopeToSoleOrganizationForPlayerWithoutExplicitOrg() throws Exception {
            // Given: PLAYER(userId=10) はちょうど1団体(7L)所属。フロントは organizationId を渡さない。
            LocalDate date = LocalDate.of(2024, 1, 15);
            Long playerUserId = 10L;
            Long orgId = 7L;
            when(organizationService.getPlayerOrganizationIds(playerUserId))
                    .thenReturn(List.of(orgId));
            when(matchVideoService.getDateCandidates(date, orgId))
                    .thenReturn(List.of(sampleCandidate(date)));

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "PLAYER").header("X-User-Id", playerUserId.toString())
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].matchNumber").value(1));

            // 所属団体IDでスコープされる（null では呼ばれない）
            verify(matchVideoService).getDateCandidates(date, orgId);
            verify(matchVideoService, never()).getDateCandidates(eq(date), isNull());
        }

        @Test
        @DisplayName("複数所属PLAYERが organizationId 未指定で呼ぶと非限定（null）のまま")
        void shouldNotScopeForPlayerWithMultipleOrganizations() throws Exception {
            // Given: PLAYER(userId=10) は2団体(7L,8L)所属。一意に決められないため非限定。
            LocalDate date = LocalDate.of(2024, 1, 15);
            Long playerUserId = 10L;
            when(organizationService.getPlayerOrganizationIds(playerUserId))
                    .thenReturn(List.of(7L, 8L));
            when(matchVideoService.getDateCandidates(eq(date), isNull()))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "PLAYER").header("X-User-Id", playerUserId.toString())
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk());

            // 非限定（null）で呼ばれる。特定団体IDでは呼ばれない。
            verify(matchVideoService).getDateCandidates(eq(date), isNull());
            verify(matchVideoService, never()).getDateCandidates(eq(date), eq(7L));
            verify(matchVideoService, never()).getDateCandidates(eq(date), eq(8L));
        }

        @Test
        @DisplayName("未所属PLAYERが organizationId 未指定で呼ぶと非限定（null）のまま")
        void shouldNotScopeForPlayerWithNoOrganization() throws Exception {
            // Given: PLAYER(userId=10) は0団体所属。
            LocalDate date = LocalDate.of(2024, 1, 15);
            Long playerUserId = 10L;
            when(organizationService.getPlayerOrganizationIds(playerUserId))
                    .thenReturn(Collections.emptyList());
            when(matchVideoService.getDateCandidates(eq(date), isNull()))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "PLAYER").header("X-User-Id", playerUserId.toString())
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk());

            verify(matchVideoService).getDateCandidates(eq(date), isNull());
        }

        @Test
        @DisplayName("単一所属PLAYERが自団体IDを明示指定すると（既定解決を経ず）その団体でスコープされる")
        void shouldUseExplicitOrgWhenPlayerSpecifiesOwnOrg() throws Exception {
            // Given: organizationId を明示指定。OrganizationScopeResolver が直接 7L を返すため既定解決は走らない。
            LocalDate date = LocalDate.of(2024, 1, 15);
            Long playerUserId = 10L;
            Long orgId = 7L;
            when(organizationService.getPlayerOrganizationIds(playerUserId))
                    .thenReturn(List.of(orgId, 8L));
            when(matchVideoService.getDateCandidates(date, orgId))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "PLAYER").header("X-User-Id", playerUserId.toString())
                            .param("date", "2024-01-15")
                            .param("organizationId", orgId.toString()))
                    .andExpect(status().isOk());

            verify(matchVideoService).getDateCandidates(date, orgId);
        }

        @Test
        @DisplayName("PLAYER が所属外の団体IDを明示指定すると 403（サービスは呼ばれない）")
        void shouldReturn403WhenPlayerRequestsNonBelongingOrg() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Long playerUserId = 10L;
            when(organizationService.getPlayerOrganizationIds(playerUserId))
                    .thenReturn(List.of(7L));

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "PLAYER").header("X-User-Id", playerUserId.toString())
                            .param("date", "2024-01-15")
                            .param("organizationId", "99"))
                    .andExpect(status().isForbidden());

            verify(matchVideoService, never()).getDateCandidates(any(), any());
        }

        @Test
        @DisplayName("SUPER_ADMIN が organizationId 未指定なら非限定（null）。既定解決の所属引きも行わない")
        void shouldNotScopeForSuperAdminWithoutExplicitOrg() throws Exception {
            // Given: SUPER_ADMIN(userId=1) は organizations を持たない（getPlayerOrganizationIds は空）。
            LocalDate date = LocalDate.of(2024, 1, 15);
            when(matchVideoService.getDateCandidates(eq(date), isNull()))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk());

            verify(matchVideoService).getDateCandidates(eq(date), isNull());
            // SUPER_ADMIN は既定解決（PLAYER 限定）に入らないため、所属引きを一切行わない。
            verify(organizationService, never()).getPlayerOrganizationIds(any());
        }

        @Test
        @DisplayName("SUPER_ADMIN が単一所属でも organizationId 未指定なら非限定（null）。所属団体に勝手に絞らない")
        void shouldNotScopeForSuperAdminEvenWithSingleOrganization() throws Exception {
            // Given: SUPER_ADMIN(userId=1) がたまたま1団体(7L)に所属していても、
            //        SUPER_ADMIN は全団体横断のため、未指定時は所属団体に絞らず非限定（null）で呼ぶ。
            //        ※ もし PLAYER 同様に既定解決していたら 7L に絞られてしまう（これが WARNING の指摘）。
            LocalDate date = LocalDate.of(2024, 1, 15);
            when(matchVideoService.getDateCandidates(eq(date), isNull()))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk());

            // 非限定（null）で呼ばれ、単一所属の 7L には絞られない。
            verify(matchVideoService).getDateCandidates(eq(date), isNull());
            verify(matchVideoService, never()).getDateCandidates(eq(date), eq(7L));
            // SUPER_ADMIN は既定解決の所属引き自体を行わない（ロールで早期に弾く）。
            verify(organizationService, never()).getPlayerOrganizationIds(any());
        }

        @Test
        @DisplayName("ADMIN は adminOrganizationId でスコープされ、既定解決の所属引きは行わない")
        void shouldScopeAdminByAdminOrganizationIdWithoutDefaultResolution() throws Exception {
            // Given: ADMIN(userId=1) は adminOrganizationId=7L。RoleCheckInterceptor が属性をセットする。
            LocalDate date = LocalDate.of(2024, 1, 15);
            com.karuta.matchtracker.entity.Player admin = new com.karuta.matchtracker.entity.Player();
            admin.setId(1L);
            admin.setAdminOrganizationId(7L);
            when(playerRepository.findById(1L)).thenReturn(java.util.Optional.of(admin));
            when(matchVideoService.getDateCandidates(date, 7L))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .header("X-User-Role", "ADMIN").header("X-User-Id", "1")
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk());

            verify(matchVideoService).getDateCandidates(date, 7L);
            // ADMIN は OrganizationScopeResolver が adminOrgId を返すため、既定解決（所属引き）は走らない。
            verify(organizationService, never()).getPlayerOrganizationIds(any());
        }

        @Test
        @DisplayName("認可ヘッダーなしは 403")
        void shouldReturn403WithoutAuthHeader() throws Exception {
            mockMvc.perform(get("/api/match-videos/date-candidates")
                            .param("date", "2024-01-15"))
                    .andExpect(status().isForbidden());

            verify(matchVideoService, never()).getDateCandidates(any(), any());
        }
    }

    @Nested
    @DisplayName("resolveDefaultOrganizationIdForCandidates（既定組織スコープ解決ロジック）")
    class ResolveDefaultOrganizationIdTests {

        @Test
        @DisplayName("ちょうど1団体所属ならその団体IDを返す")
        void singleOrganizationReturnsThatId() {
            when(organizationService.getPlayerOrganizationIds(10L)).thenReturn(List.of(7L));

            Long result = matchVideoController.resolveDefaultOrganizationIdForCandidates(10L);

            assertThat(result).isEqualTo(7L);
        }

        @Test
        @DisplayName("複数所属なら null（非限定）")
        void multipleOrganizationsReturnNull() {
            when(organizationService.getPlayerOrganizationIds(10L)).thenReturn(List.of(7L, 8L));

            Long result = matchVideoController.resolveDefaultOrganizationIdForCandidates(10L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("0所属なら null（非限定）")
        void noOrganizationReturnsNull() {
            when(organizationService.getPlayerOrganizationIds(10L)).thenReturn(Collections.emptyList());

            Long result = matchVideoController.resolveDefaultOrganizationIdForCandidates(10L);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("currentUserId が null なら所属を引かず null を返す")
        void nullUserIdReturnsNullWithoutLookup() {
            Long result = matchVideoController.resolveDefaultOrganizationIdForCandidates(null);

            assertThat(result).isNull();
            verify(organizationService, never()).getPlayerOrganizationIds(any());
        }
    }
}
