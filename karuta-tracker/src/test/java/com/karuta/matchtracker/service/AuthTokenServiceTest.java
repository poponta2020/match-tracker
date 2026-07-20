package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.AuthToken;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.AuthTokenRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthTokenService の単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthTokenService 単体テスト")
class AuthTokenServiceTest {

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private AuthTokenService authTokenService;

    private Player player;

    @BeforeEach
    void setUp() {
        player = Player.builder()
                .id(1L)
                .name("山田太郎")
                .password("$2a$10$hashed")
                .role(Player.Role.PLAYER)
                .build();
    }

    /** テスト側で独立に SHA-256 を計算する（実装と同じ計算をしていることの確認に使う） */
    private static String sha256Hex(String raw) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Nested
    @DisplayName("issue（発行）")
    class IssueTests {

        @Test
        @DisplayName("生トークンを返し、DBにはSHA-256ハッシュのみを保存する（生トークンは保存しない）")
        void testIssue_StoresHashOnly() throws Exception {
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            String rawToken = authTokenService.issue(player);

            ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
            verify(authTokenRepository).save(captor.capture());
            AuthToken saved = captor.getValue();

            // 生トークンは 32バイト = hex 64文字
            assertThat(rawToken).hasSize(64).matches("[0-9a-f]{64}");
            // 保存されているのはハッシュであって生トークンではない
            assertThat(saved.getTokenHash()).isNotEqualTo(rawToken);
            assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rawToken));
            assertThat(saved.getPlayerId()).isEqualTo(1L);
            assertThat(saved.getRevokedAt()).isNull();
        }

        @Test
        @DisplayName("有効期限は発行から約1年後に設定される")
        void testIssue_ExpiresInAboutOneYear() {
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            authTokenService.issue(player);

            ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
            verify(authTokenRepository).save(captor.capture());
            AuthToken saved = captor.getValue();

            assertThat(saved.getExpiresAt()).isEqualTo(saved.getIssuedAt().plusDays(365));
        }

        @Test
        @DisplayName("発行のたびに異なるトークンが払い出される")
        void testIssue_GeneratesUniqueTokens() {
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));

            String first = authTokenService.issue(player);
            String second = authTokenService.issue(player);

            assertThat(first).isNotEqualTo(second);
        }
    }

    @Nested
    @DisplayName("verify（検証）")
    class VerifyTests {

        @Test
        @DisplayName("発行したトークンは検証を通り、対応する選手を解決できる")
        void testVerify_ValidToken_ResolvesPlayer() {
            when(authTokenRepository.save(any(AuthToken.class))).thenAnswer(inv -> inv.getArgument(0));
            String rawToken = authTokenService.issue(player);

            ArgumentCaptor<AuthToken> captor = ArgumentCaptor.forClass(AuthToken.class);
            verify(authTokenRepository).save(captor.capture());
            AuthToken issued = captor.getValue();

            when(authTokenRepository.findByTokenHash(issued.getTokenHash())).thenReturn(Optional.of(issued));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            Optional<Player> result = authTokenService.verify(rawToken);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("存在しないトークンは検証を通らない")
        void testVerify_UnknownToken_ReturnsEmpty() {
            when(authTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThat(authTokenService.verify("deadbeef")).isEmpty();
        }

        @Test
        @DisplayName("期限切れトークンは検証を通らない")
        void testVerify_ExpiredToken_ReturnsEmpty() throws Exception {
            String rawToken = "a".repeat(64);
            AuthToken expired = AuthToken.builder()
                    .playerId(1L)
                    .tokenHash(sha256Hex(rawToken))
                    .issuedAt(LocalDateTime.now().minusDays(400))
                    .expiresAt(LocalDateTime.now().minusDays(35))
                    .build();
            when(authTokenRepository.findByTokenHash(expired.getTokenHash())).thenReturn(Optional.of(expired));

            assertThat(authTokenService.verify(rawToken)).isEmpty();
            // 期限切れの時点で打ち切り、選手の解決まで行かない
            verify(playerRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("失効済みトークンは検証を通らない")
        void testVerify_RevokedToken_ReturnsEmpty() throws Exception {
            String rawToken = "b".repeat(64);
            AuthToken revoked = AuthToken.builder()
                    .playerId(1L)
                    .tokenHash(sha256Hex(rawToken))
                    .issuedAt(LocalDateTime.now().minusDays(1))
                    .expiresAt(LocalDateTime.now().plusDays(364))
                    .revokedAt(LocalDateTime.now().minusMinutes(1))
                    .build();
            when(authTokenRepository.findByTokenHash(revoked.getTokenHash())).thenReturn(Optional.of(revoked));

            assertThat(authTokenService.verify(rawToken)).isEmpty();
            verify(playerRepository, never()).findById(anyLong());
        }

        @Test
        @DisplayName("論理削除された選手のトークンは検証を通らない")
        void testVerify_DeletedPlayer_ReturnsEmpty() throws Exception {
            String rawToken = "c".repeat(64);
            AuthToken token = AuthToken.builder()
                    .playerId(1L)
                    .tokenHash(sha256Hex(rawToken))
                    .issuedAt(LocalDateTime.now().minusDays(1))
                    .expiresAt(LocalDateTime.now().plusDays(364))
                    .build();
            player.setDeletedAt(LocalDateTime.now().minusHours(1));

            when(authTokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
            when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

            assertThat(authTokenService.verify(rawToken)).isEmpty();
        }

        @Test
        @DisplayName("null・空文字のトークンは検証を通らず、DBも引かない")
        void testVerify_NullOrBlank_ReturnsEmpty() {
            assertThat(authTokenService.verify(null)).isEmpty();
            assertThat(authTokenService.verify("")).isEmpty();
            assertThat(authTokenService.verify("   ")).isEmpty();

            verify(authTokenRepository, never()).findByTokenHash(anyString());
        }
    }

    @Nested
    @DisplayName("revoke（失効）")
    class RevokeTests {

        @Test
        @DisplayName("指定トークンのみを失効させる（ログアウト）")
        void testRevoke_SetsRevokedAt() throws Exception {
            String rawToken = "d".repeat(64);
            AuthToken token = AuthToken.builder()
                    .playerId(1L)
                    .tokenHash(sha256Hex(rawToken))
                    .issuedAt(LocalDateTime.now().minusDays(1))
                    .expiresAt(LocalDateTime.now().plusDays(364))
                    .build();
            when(authTokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

            authTokenService.revoke(rawToken);

            assertThat(token.getRevokedAt()).isNotNull();
            verify(authTokenRepository).save(token);
        }

        @Test
        @DisplayName("既に失効済みのトークンを再度失効させても失効日時は変わらない")
        void testRevoke_AlreadyRevoked_IsNoOp() throws Exception {
            String rawToken = "e".repeat(64);
            LocalDateTime revokedAt = LocalDateTime.now().minusDays(2);
            AuthToken token = AuthToken.builder()
                    .playerId(1L)
                    .tokenHash(sha256Hex(rawToken))
                    .issuedAt(LocalDateTime.now().minusDays(3))
                    .expiresAt(LocalDateTime.now().plusDays(362))
                    .revokedAt(revokedAt)
                    .build();
            when(authTokenRepository.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));

            authTokenService.revoke(rawToken);

            assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
            verify(authTokenRepository, never()).save(any(AuthToken.class));
        }

        @Test
        @DisplayName("選手単位で有効なトークンを一括失効させる")
        void testRevokeAllForPlayer() {
            when(authTokenRepository.revokeAllByPlayerId(eq(1L), any(LocalDateTime.class))).thenReturn(3);

            int revoked = authTokenService.revokeAllForPlayer(1L);

            assertThat(revoked).isEqualTo(3);
            verify(authTokenRepository, times(1)).revokeAllByPlayerId(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("playerIdがnullなら何もしない")
        void testRevokeAllForPlayer_NullId_IsNoOp() {
            assertThat(authTokenService.revokeAllForPlayer(null)).isZero();

            verify(authTokenRepository, never()).revokeAllByPlayerId(anyLong(), any(LocalDateTime.class));
        }
    }
}
