package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.InviteTokenResponse;
import com.karuta.matchtracker.entity.InviteToken;
import com.karuta.matchtracker.entity.InviteToken.TokenType;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.InviteTokenRepository;
import com.karuta.matchtracker.repository.OrganizationRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InviteTokenService 単体テスト")
class InviteTokenServiceTest {

    @Mock
    private InviteTokenRepository inviteTokenRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    @InjectMocks
    private InviteTokenService inviteTokenService;

    @Test
    @DisplayName("createToken: organizationIdがnullの場合はResourceNotFoundExceptionをスローし、保存しない")
    void createToken_throwsWhenOrganizationIdNull() {
        assertThatThrownBy(() ->
                inviteTokenService.createToken(TokenType.MULTI_USE, 1L, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inviteTokenRepository, never()).save(any(InviteToken.class));
    }

    @Test
    @DisplayName("createToken: 存在しないorganizationIdの場合はResourceNotFoundExceptionをスローし、保存しない")
    void createToken_throwsWhenOrganizationDoesNotExist() {
        when(organizationRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() ->
                inviteTokenService.createToken(TokenType.SINGLE_USE, 2L, 999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Organization");

        verify(inviteTokenRepository, never()).save(any(InviteToken.class));
    }

    @Test
    @DisplayName("createToken: 存在する団体IDが指定されればトークンを発行できる")
    void createToken_succeedsWhenOrganizationExists() {
        when(organizationRepository.existsById(10L)).thenReturn(true);
        when(inviteTokenRepository.save(any(InviteToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InviteTokenResponse response =
                inviteTokenService.createToken(TokenType.MULTI_USE, 1L, 10L);

        assertThat(response).isNotNull();
        assertThat(response.getOrganizationId()).isEqualTo(10L);
        assertThat(response.getType()).isEqualTo(TokenType.MULTI_USE.name());
        verify(inviteTokenRepository).save(any(InviteToken.class));
    }
}
