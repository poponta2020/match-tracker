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
}
