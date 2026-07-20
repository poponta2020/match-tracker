package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.config.PasswordHashMigrationRunner;
import com.karuta.matchtracker.dto.LoginRequest;
import com.karuta.matchtracker.dto.LoginResponse;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.dto.PublicRegisterRequest;
import com.karuta.matchtracker.entity.InviteToken;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.InviteTokenRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.AuthTokenService;
import com.karuta.matchtracker.service.DensukeImportService;
import com.karuta.matchtracker.service.InviteTokenService;
import com.karuta.matchtracker.service.PlayerService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * パスワードの BCrypt 化と認証トークンのライフサイクルの統合テスト（auth-tokenization タスク2）
 *
 * 主にサービス層を直接呼び、「パスワードがハッシュで保存されるか」
 * 「保存したパスワードでログインできるか」を検証する。
 *
 * 加えて {@code HttpRoundTripTests} では HTTP 経由の往復も検証する。
 * このクラスは {@code AuthTokenService} をモックしない（{@link BaseIntegrationTest} を直接継承）ため、
 * 実際に発行されたトークンがインターセプタで解決される経路を通す唯一のテストになっている。
 * deny by default の網羅的な検証は {@code AuthenticationIntegrationTest} が受け持つ。
 */
@DisplayName("パスワードハッシュ化・トークンライフサイクル統合テスト")
class AuthPasswordIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private InviteTokenService inviteTokenService;

    @Autowired
    private DensukeImportService densukeImportService;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private InviteTokenRepository inviteTokenRepository;

    @Autowired
    private PasswordHashMigrationRunner passwordHashMigrationRunner;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BCRYPT_PREFIX_REGEX = "^\\$2[aby]\\$.+";

    /** DB に保存されている生の password 列を読む（DTO はパスワードを返さないため JDBC で直接読む） */
    private String storedPassword(Long playerId) {
        return jdbcTemplate.queryForObject(
                "SELECT password FROM players WHERE id = ?", String.class, playerId);
    }

    private PlayerDto createPlayerViaService(String name, String rawPassword) {
        return playerService.createPlayer(PlayerCreateRequest.builder()
                .name(name)
                .password(rawPassword)
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build());
    }

    @Nested
    @DisplayName("AC-10 / AC-11b: パスワードの4つの書き込み経路")
    class PasswordWritePathTests {

        @Test
        @DisplayName("経路1（選手作成）: BCryptで保存され、そのパスワードで即ログインできる")
        void testCreatePlayer_HashesAndRoundTrips() {
            PlayerDto created = createPlayerViaService("作成太郎", "password123");

            assertThat(storedPassword(created.getId()))
                    .matches(BCRYPT_PREFIX_REGEX)
                    .isNotEqualTo("password123");

            // 再起動（移行ランナー）を挟まずにログインできること
            LoginResponse response = playerService.login(new LoginRequest("作成太郎", "password123"));
            assertThat(response.getToken()).isNotBlank();
        }

        @Test
        @DisplayName("経路2（選手更新＝パスワード変更）: BCryptで保存され、新パスワードでログインできる")
        void testUpdatePlayer_HashesAndRoundTrips() {
            PlayerDto created = createPlayerViaService("更新次郎", "oldpassword");

            playerService.updatePlayer(created.getId(), PlayerUpdateRequest.builder()
                    .password("newpassword")
                    .build());

            assertThat(storedPassword(created.getId()))
                    .matches(BCRYPT_PREFIX_REGEX)
                    .isNotEqualTo("newpassword");

            LoginResponse response = playerService.login(new LoginRequest("更新次郎", "newpassword"));
            assertThat(response.getToken()).isNotBlank();

            // 旧パスワードではログインできない
            assertThatThrownBy(() -> playerService.login(new LoginRequest("更新次郎", "oldpassword")))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("経路3（招待リンク登録）: BCryptで保存され、そのパスワードでログインできる")
        void testInviteRegister_HashesAndRoundTrips() {
            InviteToken token = inviteTokenRepository.save(InviteToken.builder()
                    .token("invite-token-test")
                    .type(InviteToken.TokenType.MULTI_USE)
                    .expiresAt(JstDateTimeUtil.now().plusHours(72))
                    .createdBy(1L)
                    .organizationId(1L)
                    .build());

            PlayerDto registered = inviteTokenService.registerWithToken(PublicRegisterRequest.builder()
                    .token(token.getToken())
                    .name("招待三郎")
                    .password("invitepass1")
                    .gender(Player.Gender.女性)
                    .dominantHand(Player.DominantHand.左)
                    .build());

            assertThat(storedPassword(registered.getId()))
                    .matches(BCRYPT_PREFIX_REGEX)
                    .isNotEqualTo("invitepass1");

            LoginResponse response = playerService.login(new LoginRequest("招待三郎", "invitepass1"));
            assertThat(response.getToken()).isNotBlank();
        }

        @Test
        @DisplayName("経路4（伝助の自動登録）: BCryptで保存され、初期パスワードでログインできる")
        void testDensukeAutoRegister_HashesAndRoundTrips() {
            Player built = densukeImportService.buildAutoRegisteredPlayer("伝助四郎");
            Player saved = playerRepository.save(built);

            assertThat(storedPassword(saved.getId()))
                    .matches(BCRYPT_PREFIX_REGEX)
                    .isNotEqualTo("pppppppp");

            LoginResponse response = playerService.login(new LoginRequest("伝助四郎", "pppppppp"));
            assertThat(response.getToken()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("AC-11: 平文パスワードの移行")
    class MigrationTests {

        /** 移行前の状態を作るため、JDBC で平文パスワードの行を直接挿入する */
        private Long insertPlaintextPlayer(String name, String plaintext) {
            jdbcTemplate.update(
                    "INSERT INTO players (name, password, gender, dominant_hand, role, ical_feed_token, "
                            + "require_password_change, created_at, updated_at) "
                            + "VALUES (?, ?, '男性', '右', 'PLAYER', ?, false, NOW(), NOW())",
                    name, plaintext, "feed-" + name);
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM players WHERE name = ?", Long.class, name);
        }

        @Test
        @DisplayName("移行前の平文パスワードを持つ会員が、移行後も同じパスワードでログインできる")
        void testMigration_PlaintextUserCanStillLogin() {
            Long id = insertPlaintextPlayer("移行五郎", "legacypass");
            assertThat(storedPassword(id)).isEqualTo("legacypass");

            passwordHashMigrationRunner.run(null);

            assertThat(storedPassword(id)).matches(BCRYPT_PREFIX_REGEX);

            // 会員は今まで通りのパスワードでログインできる
            LoginResponse response = playerService.login(new LoginRequest("移行五郎", "legacypass"));
            assertThat(response.getToken()).isNotBlank();
        }

        @Test
        @DisplayName("BCrypt接頭辞に似ているだけの平文も移行される（接頭辞判定では取りこぼす）")
        void testMigration_ConvertsPlaintextThatLooksLikeBcryptPrefix() {
            // "$2a$" で始まるが BCrypt ハッシュではない平文。接頭辞だけで判定していると
            // 「移行済み」と誤判定され、この会員がログイン不能になる
            Long id = insertPlaintextPlayer("接頭辞紛らわしい選手", "$2a$notarealhash");

            passwordHashMigrationRunner.run(null);

            assertThat(storedPassword(id)).matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
            assertThat(playerService.login(
                    new LoginRequest("接頭辞紛らわしい選手", "$2a$notarealhash")).getToken()).isNotBlank();
        }

        @Test
        @DisplayName("冪等: 既にハッシュ済みの行は再実行しても変化しない（二重ハッシュ化しない）")
        void testMigration_IsIdempotent() {
            Long id = insertPlaintextPlayer("冪等六郎", "legacypass");

            passwordHashMigrationRunner.run(null);
            String afterFirst = storedPassword(id);

            passwordHashMigrationRunner.run(null);
            String afterSecond = storedPassword(id);

            assertThat(afterSecond).isEqualTo(afterFirst);
            // 二重ハッシュ化されていれば元のパスワードでログインできなくなる
            assertThat(playerService.login(new LoginRequest("冪等六郎", "legacypass")).getToken()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("AC-5 / AC-6: ログイン")
    class LoginTests {

        @Test
        @DisplayName("AC-5: ログイン成功でトークンが発行され、そのトークンで本人を解決できる")
        void testLogin_IssuesUsableToken() {
            PlayerDto created = createPlayerViaService("ログイン七郎", "password123");

            LoginResponse response = playerService.login(new LoginRequest("ログイン七郎", "password123"));

            assertThat(response.getToken()).isNotBlank().hasSize(64);
            Optional<Player> resolved = authTokenService.verify(response.getToken());
            assertThat(resolved).isPresent();
            assertThat(resolved.get().getId()).isEqualTo(created.getId());
        }

        @Test
        @DisplayName("AC-6: 誤ったパスワードではトークンを発行せずエラーを返す")
        void testLogin_WrongPassword_IssuesNoToken() {
            createPlayerViaService("失敗八郎", "password123");

            assertThatThrownBy(() -> playerService.login(new LoginRequest("失敗八郎", "wrongpassword")))
                    .isInstanceOf(RuntimeException.class);

            Integer tokenCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth_tokens", Integer.class);
            assertThat(tokenCount).isZero();
        }
    }

    @Nested
    @DisplayName("AC-20: HTTP 経由のトークン往復（実サービス・モックなし）")
    class HttpRoundTripTests {

        /**
         * ログイン → 保護エンドポイント → ログアウト → 再ログインを HTTP で通しで検証する。
         *
         * <p>スライステストや {@link BaseAuthenticatedIntegrationTest} は
         * {@code AuthTokenService} をモックしているため、
         * 「発行された実トークンがインターセプタで解決され、リクエスト属性まで届く」
         * 経路そのものはそこでは実行されない。ここだけが実物を通す。
         */
        @Test
        @DisplayName("ログイン→認証付きアクセス→ログアウト→再ログインが通しで動く")
        void testFullTokenRoundTripOverHttp() throws Exception {
            createPlayerViaService("往復太郎", "password123");

            // 1. 未認証では保護エンドポイントを叩けない
            mockMvc.perform(get("/api/players"))
                    .andExpect(status().isUnauthorized());

            // 2. ログインして実トークンを受け取る
            String loginResponse = mockMvc.perform(post("/api/players/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"往復太郎\",\"password\":\"password123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            String token = objectMapper.readTree(loginResponse).get("token").asText();

            // 3. そのトークンで保護エンドポイントを叩ける
            mockMvc.perform(get("/api/players")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // 4. ログアウトするとそのトークンは使えなくなる
            mockMvc.perform(post("/api/players/logout")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/players")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());

            // 5. 再ログインすると別の有効なトークンが発行される
            String secondLogin = mockMvc.perform(post("/api/players/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"往復太郎\",\"password\":\"password123\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String newToken = objectMapper.readTree(secondLogin).get("token").asText();

            assertThat(newToken).isNotEqualTo(token);
            mockMvc.perform(get("/api/players")
                            .header("Authorization", "Bearer " + newToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("実トークンでもロール不足なら 403（PLAYER は選手削除できない）")
        void testRealTokenStillEnforcesRole() throws Exception {
            PlayerDto created = createPlayerViaService("権限太郎", "password123");

            String loginResponse = mockMvc.perform(post("/api/players/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"権限太郎\",\"password\":\"password123\"}"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String token = objectMapper.readTree(loginResponse).get("token").asText();

            // DELETE /api/players/{id} は SUPER_ADMIN 専用
            mockMvc.perform(delete("/api/players/" + created.getId())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("AC-12 / AC-13 / AC-14: 失効")
    class RevocationTests {

        @Test
        @DisplayName("AC-12: パスワード変更で、その選手の発行済みトークンがすべて無効になる")
        void testPasswordChange_RevokesAllTokens() {
            PlayerDto created = createPlayerViaService("失効九郎", "oldpassword");
            String tokenA = playerService.login(new LoginRequest("失効九郎", "oldpassword")).getToken();
            String tokenB = playerService.login(new LoginRequest("失効九郎", "oldpassword")).getToken();
            assertThat(authTokenService.verify(tokenA)).isPresent();

            playerService.updatePlayer(created.getId(), PlayerUpdateRequest.builder()
                    .password("newpassword")
                    .build());

            assertThat(authTokenService.verify(tokenA)).isEmpty();
            assertThat(authTokenService.verify(tokenB)).isEmpty();
        }

        @Test
        @DisplayName("AC-12: パスワード以外の更新ではトークンは失効しない")
        void testNonPasswordUpdate_KeepsTokens() {
            PlayerDto created = createPlayerViaService("維持十郎", "password123");
            String token = playerService.login(new LoginRequest("維持十郎", "password123")).getToken();

            playerService.updatePlayer(created.getId(), PlayerUpdateRequest.builder()
                    .karutaClub("北大かるた会")
                    .build());

            assertThat(authTokenService.verify(token)).isPresent();
        }

        @Test
        @DisplayName("AC-13: 選手の論理削除で、その選手の発行済みトークンが無効になる")
        void testPlayerDeletion_RevokesTokens() {
            PlayerDto created = createPlayerViaService("削除太郎", "password123");
            String token = playerService.login(new LoginRequest("削除太郎", "password123")).getToken();
            assertThat(authTokenService.verify(token)).isPresent();

            playerService.deletePlayer(created.getId());

            assertThat(authTokenService.verify(token)).isEmpty();
        }

        @Test
        @DisplayName("AC-14: ログアウトで当該トークンのみが無効になる（他端末のトークンは維持）")
        void testLogout_RevokesOnlyThatToken() {
            createPlayerViaService("退出太郎", "password123");
            String tokenA = playerService.login(new LoginRequest("退出太郎", "password123")).getToken();
            String tokenB = playerService.login(new LoginRequest("退出太郎", "password123")).getToken();

            playerService.logout(tokenA);

            assertThat(authTokenService.verify(tokenA)).isEmpty();
            assertThat(authTokenService.verify(tokenB)).isPresent();
        }
    }
}
