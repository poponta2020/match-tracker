package com.karuta.matchtracker.util;

import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationScopeResolver 単体テスト")
class OrganizationScopeResolverTest {

    @Mock
    private OrganizationService organizationService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private OrganizationScopeResolver resolver;

    private void stubRoleAttributes(String role, Long adminOrgId, Long currentUserId) {
        lenient().when(httpRequest.getAttribute("currentUserRole")).thenReturn(role);
        lenient().when(httpRequest.getAttribute("adminOrganizationId")).thenReturn(adminOrgId);
        lenient().when(httpRequest.getAttribute("currentUserId")).thenReturn(currentUserId);
    }

    @Test
    @DisplayName("ADMIN: requestedOrganizationId が未指定なら adminOrganizationId を返す")
    void adminWithoutRequestedOrgUsesAdminOrgId() {
        stubRoleAttributes("ADMIN", 7L, 1L);

        Long result = resolver.resolveEffectiveOrganizationId(httpRequest, null);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("ADMIN: requestedOrganizationId が adminOrganizationId と一致するなら通す")
    void adminWithMatchingRequestedOrgPasses() {
        stubRoleAttributes("ADMIN", 7L, 1L);

        Long result = resolver.resolveEffectiveOrganizationId(httpRequest, 7L);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("ADMIN: 他団体IDを指定したら 403")
    void adminWithMismatchedRequestedOrgIsForbidden() {
        stubRoleAttributes("ADMIN", 7L, 1L);

        assertThatThrownBy(() -> resolver.resolveEffectiveOrganizationId(httpRequest, 99L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("他団体");
    }

    @Test
    @DisplayName("PLAYER: requestedOrganizationId が所属団体ならそれを返す")
    void playerWithBelongingOrgPasses() {
        stubRoleAttributes("PLAYER", null, 10L);
        when(organizationService.getPlayerOrganizationIds(10L)).thenReturn(List.of(7L, 8L));

        Long result = resolver.resolveEffectiveOrganizationId(httpRequest, 7L);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("PLAYER: 所属外の団体IDを指定したら 403")
    void playerWithNonBelongingOrgIsForbidden() {
        stubRoleAttributes("PLAYER", null, 10L);
        when(organizationService.getPlayerOrganizationIds(10L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> resolver.resolveEffectiveOrganizationId(httpRequest, 99L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("参加していない団体");
    }

    @Test
    @DisplayName("PLAYER: requestedOrganizationId が null なら null を返す（従来の日付のみ検索）")
    void playerWithoutRequestedOrgReturnsNull() {
        stubRoleAttributes("PLAYER", null, 10L);

        Long result = resolver.resolveEffectiveOrganizationId(httpRequest, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("SUPER_ADMIN: requestedOrganizationId をそのまま返す")
    void superAdminPassesThroughRequestedOrg() {
        stubRoleAttributes("SUPER_ADMIN", null, 1L);

        assertThat(resolver.resolveEffectiveOrganizationId(httpRequest, 7L)).isEqualTo(7L);
        assertThat(resolver.resolveEffectiveOrganizationId(httpRequest, null)).isNull();
    }

    // ===== resolveViewingOrganizationId（閲覧用・ADMIN を PLAYER と同じ会員団体スコープに統一） =====

    @Test
    @DisplayName("閲覧: ADMIN は organizationId 未指定なら null（会員団体スコープ・非限定）を返す")
    void viewingAdminWithoutRequestedOrgReturnsNull() {
        stubRoleAttributes("ADMIN", 7L, 1L);

        // 書き込み系と違い adminOrganizationId で強制スコープしない（他団体会員でもある ADMIN が閲覧できる）
        assertThat(resolver.resolveViewingOrganizationId(httpRequest, null)).isNull();
    }

    @Test
    @DisplayName("閲覧: ADMIN は所属団体IDを指定すればそれを返す（admin_org 以外の会員団体も可）")
    void viewingAdminWithBelongingOrgPasses() {
        stubRoleAttributes("ADMIN", 7L, 1L);
        when(organizationService.getPlayerOrganizationIds(1L)).thenReturn(List.of(7L, 8L));

        assertThat(resolver.resolveViewingOrganizationId(httpRequest, 8L)).isEqualTo(8L);
    }

    @Test
    @DisplayName("閲覧: ADMIN は所属外の団体IDを指定すると 403")
    void viewingAdminWithNonBelongingOrgIsForbidden() {
        stubRoleAttributes("ADMIN", 7L, 1L);
        when(organizationService.getPlayerOrganizationIds(1L)).thenReturn(List.of(7L));

        assertThatThrownBy(() -> resolver.resolveViewingOrganizationId(httpRequest, 99L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("参加していない団体");
    }

    @Test
    @DisplayName("閲覧: PLAYER は未指定→null / 所属団体→その団体 / 所属外→403")
    void viewingPlayerBehavesLikeMemberScope() {
        stubRoleAttributes("PLAYER", null, 10L);
        when(organizationService.getPlayerOrganizationIds(10L)).thenReturn(List.of(7L));

        assertThat(resolver.resolveViewingOrganizationId(httpRequest, null)).isNull();
        assertThat(resolver.resolveViewingOrganizationId(httpRequest, 7L)).isEqualTo(7L);
        assertThatThrownBy(() -> resolver.resolveViewingOrganizationId(httpRequest, 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("閲覧: SUPER_ADMIN は requestedOrganizationId を素通し（未指定は null）")
    void viewingSuperAdminPassesThrough() {
        stubRoleAttributes("SUPER_ADMIN", null, 1L);

        assertThat(resolver.resolveViewingOrganizationId(httpRequest, 7L)).isEqualTo(7L);
        assertThat(resolver.resolveViewingOrganizationId(httpRequest, null)).isNull();
    }
}
