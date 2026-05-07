package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.InviteTokenResponse;
import com.karuta.matchtracker.entity.InviteToken.TokenType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.InviteTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteTokenController 単体テスト")
class InviteTokenControllerTest {

    @Mock
    private InviteTokenService inviteTokenService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private InviteTokenController controller;

    @Nested
    @DisplayName("POST /api/invite-tokens - createToken")
    class CreateTokenTests {

        @Test
        @DisplayName("ADMIN: adminOrganizationIdが設定されていれば自団体IDでトークンを発行できる")
        void createToken_admin_usesAdminOrganizationId() {
            InviteTokenResponse expected = InviteTokenResponse.builder()
                    .token("token-1")
                    .type(TokenType.MULTI_USE.name())
                    .organizationId(10L)
                    .expiresAt(LocalDateTime.of(2026, 5, 10, 0, 0))
                    .createdAt(LocalDateTime.of(2026, 5, 7, 0, 0))
                    .build();

            when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
            when(request.getAttribute("adminOrganizationId")).thenReturn(10L);
            when(inviteTokenService.createToken(TokenType.MULTI_USE, 1L, 10L)).thenReturn(expected);

            ResponseEntity<InviteTokenResponse> response =
                    controller.createToken(TokenType.MULTI_USE, 1L, null, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isEqualTo(expected);
            verify(inviteTokenService).createToken(TokenType.MULTI_USE, 1L, 10L);
        }

        @Test
        @DisplayName("ADMIN: クエリで渡されたorganizationIdは無視され、必ずadminOrganizationIdが採用される")
        void createToken_admin_ignoresQueryOrganizationId() {
            InviteTokenResponse expected = InviteTokenResponse.builder()
                    .token("token-2").type(TokenType.SINGLE_USE.name()).organizationId(10L).build();

            when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
            when(request.getAttribute("adminOrganizationId")).thenReturn(10L);
            when(inviteTokenService.createToken(TokenType.SINGLE_USE, 1L, 10L)).thenReturn(expected);

            ResponseEntity<InviteTokenResponse> response =
                    controller.createToken(TokenType.SINGLE_USE, 1L, 999L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(inviteTokenService).createToken(TokenType.SINGLE_USE, 1L, 10L);
        }

        @Test
        @DisplayName("ADMIN: adminOrganizationIdがNULLの場合はIllegalArgumentExceptionで400を返す")
        void createToken_admin_throwsWhenAdminOrgIdMissing() {
            when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
            when(request.getAttribute("adminOrganizationId")).thenReturn(null);

            assertThatThrownBy(() ->
                    controller.createToken(TokenType.MULTI_USE, 1L, null, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("発行者の所属団体が設定されていない");

            verify(inviteTokenService, never()).createToken(any(TokenType.class), anyLong(), anyLong());
        }

        @Test
        @DisplayName("SUPER_ADMIN: organizationIdが指定されていればトークンを発行できる")
        void createToken_superAdmin_usesQueryOrganizationId() {
            InviteTokenResponse expected = InviteTokenResponse.builder()
                    .token("token-3").type(TokenType.MULTI_USE.name()).organizationId(20L).build();

            when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());
            when(inviteTokenService.createToken(TokenType.MULTI_USE, 2L, 20L)).thenReturn(expected);

            ResponseEntity<InviteTokenResponse> response =
                    controller.createToken(TokenType.MULTI_USE, 2L, 20L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            verify(inviteTokenService).createToken(TokenType.MULTI_USE, 2L, 20L);
        }

        @Test
        @DisplayName("SUPER_ADMIN: organizationIdが未指定の場合はIllegalArgumentExceptionで400を返す")
        void createToken_superAdmin_throwsWhenOrganizationIdMissing() {
            when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());

            assertThatThrownBy(() ->
                    controller.createToken(TokenType.SINGLE_USE, 2L, null, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("organizationId");

            verify(inviteTokenService, never()).createToken(any(TokenType.class), anyLong(), anyLong());
        }
    }
}
