package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineConfirmationToken;
import com.karuta.matchtracker.repository.LineConfirmationTokenRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LineConfirmationService テスト")
class LineConfirmationServiceTest {

    @Mock
    private LineConfirmationTokenRepository lineConfirmationTokenRepository;

    @InjectMocks
    private LineConfirmationService lineConfirmationService;

    private static final Long PLAYER_ID = 1L;
    private static final String ACTION = "waitlist_accept";
    private static final String PARAMS = "{\"action\":\"waitlist_accept\",\"participantId\":\"123\"}";

    @Nested
    @DisplayName("createToken")
    class CreateTokenTest {

        @Test
        @DisplayName("トークンが正常に発行される")
        void shouldCreateToken() {
            when(lineConfirmationTokenRepository.save(any(LineConfirmationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            String token = lineConfirmationService.createToken(ACTION, PARAMS, PLAYER_ID);

            assertThat(token).isNotNull().isNotEmpty();

            // 期限切れトークン削除が呼ばれたことを確認
            verify(lineConfirmationTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));

            // 保存されたトークンの内容を確認
            ArgumentCaptor<LineConfirmationToken> captor = ArgumentCaptor.forClass(LineConfirmationToken.class);
            verify(lineConfirmationTokenRepository).save(captor.capture());
            LineConfirmationToken saved = captor.getValue();
            assertThat(saved.getToken()).isEqualTo(token);
            assertThat(saved.getAction()).isEqualTo(ACTION);
            assertThat(saved.getParams()).isEqualTo(PARAMS);
            assertThat(saved.getPlayerId()).isEqualTo(PLAYER_ID);
            assertThat(saved.getExpiresAt()).isAfter(JstDateTimeUtil.now());
        }

        @Test
        @DisplayName("発行前に期限切れトークンが削除される")
        void shouldDeleteExpiredTokensBeforeCreating() {
            when(lineConfirmationTokenRepository.save(any(LineConfirmationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            lineConfirmationService.createToken(ACTION, PARAMS, PLAYER_ID);

            verify(lineConfirmationTokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("consumeToken")
    class ConsumeTokenTest {

        private LineConfirmationToken validToken;

        @BeforeEach
        void setUp() {
            validToken = LineConfirmationToken.builder()
                .id(1L)
                .token("test-token-uuid")
                .action(ACTION)
                .params(PARAMS)
                .playerId(PLAYER_ID)
                .createdAt(JstDateTimeUtil.now())
                .expiresAt(JstDateTimeUtil.now().plusMinutes(5))
                .usedAt(null)
                .build();
        }

        @Test
        @DisplayName("有効なトークンが正常に消費される")
        void shouldConsumeValidToken() {
            when(lineConfirmationTokenRepository.findByToken("test-token-uuid"))
                .thenReturn(Optional.of(validToken));
            when(lineConfirmationTokenRepository.save(any(LineConfirmationToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            LineConfirmationToken result = lineConfirmationService.consumeToken("test-token-uuid", PLAYER_ID);

            assertThat(result.getAction()).isEqualTo(ACTION);
            assertThat(result.getParams()).isEqualTo(PARAMS);
            assertThat(result.getUsedAt()).isNotNull();
            verify(lineConfirmationTokenRepository).save(validToken);
        }

        @Test
        @DisplayName("存在しないトークンはエラー")
        void shouldThrowWhenTokenNotFound() {
            when(lineConfirmationTokenRepository.findByToken("nonexistent"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> lineConfirmationService.consumeToken("nonexistent", PLAYER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("この確認は期限切れです。もう一度操作してください。");
        }

        @Test
        @DisplayName("使用済みトークンはエラー")
        void shouldThrowWhenTokenAlreadyUsed() {
            validToken.setUsedAt(JstDateTimeUtil.now().minusMinutes(1));
            when(lineConfirmationTokenRepository.findByToken("test-token-uuid"))
                .thenReturn(Optional.of(validToken));

            assertThatThrownBy(() -> lineConfirmationService.consumeToken("test-token-uuid", PLAYER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("この確認は期限切れです。もう一度操作してください。");

            verify(lineConfirmationTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("期限切れトークンはエラー")
        void shouldThrowWhenTokenExpired() {
            validToken.setExpiresAt(JstDateTimeUtil.now().minusMinutes(1));
            when(lineConfirmationTokenRepository.findByToken("test-token-uuid"))
                .thenReturn(Optional.of(validToken));

            assertThatThrownBy(() -> lineConfirmationService.consumeToken("test-token-uuid", PLAYER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("この確認は期限切れです。もう一度操作してください。");

            verify(lineConfirmationTokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("他人のトークンはエラー")
        void shouldThrowWhenPlayerIdMismatch() {
            when(lineConfirmationTokenRepository.findByToken("test-token-uuid"))
                .thenReturn(Optional.of(validToken));

            Long otherPlayerId = 999L;
            assertThatThrownBy(() -> lineConfirmationService.consumeToken("test-token-uuid", otherPlayerId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("この確認は期限切れです。もう一度操作してください。");

            verify(lineConfirmationTokenRepository, never()).save(any());
        }
    }
}
