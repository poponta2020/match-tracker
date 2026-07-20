package com.karuta.matchtracker.integration;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.dto.LoginRequest;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.service.AuthTokenService;
import com.karuta.matchtracker.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * パスワード変更とログインの競合に関する統合テスト（auth-tokenization / AC-12）
 *
 * <p><b>閉じたい穴</b>: 行ロックが無いと、パスワード変更中のログインが
 * 「変更前のハッシュで認証 → 一括失効が走った<b>後で</b>トークンを INSERT」して
 * 失効をすり抜け、旧パスワード由来のトークンが約1年間有効なまま残る。
 *
 * <p>このテストは<b>意図的に @Transactional を付けていない</b>。
 * 2つのトランザクションを別スレッドで本当に並行させる必要があるため、
 * {@link BaseIntegrationTest} のテストトランザクションは使えない。
 * 後始末は各テストで明示的に行う。
 */
@SpringBootTest
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("ログインとパスワード変更の競合（AC-12）")
class LoginPasswordChangeRaceIntegrationTest {

    @Autowired
    private PlayerService playerService;

    @Autowired
    private AuthTokenService authTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static final String NAME = "競合検証選手";
    private static final String OLD_PASSWORD = "oldpassword";
    private static final String NEW_PASSWORD = "newpassword";

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE auth_tokens, player_organizations, players "
                + "RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("パスワード変更と並行したログインのトークンも必ず失効する（すり抜けない）")
    void testConcurrentLoginDuringPasswordChangeIsRevoked() throws Exception {
        PlayerDto player = playerService.createPlayer(PlayerCreateRequest.builder()
                .name(NAME)
                .password(OLD_PASSWORD)
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // パスワード変更トランザクションを開始し、行ロックを取った状態で待機させる
        CountDownLatch changeHasLock = new CountDownLatch(1);
        CountDownLatch loginAttempted = new CountDownLatch(1);

        Future<?> passwordChange = executor.submit(() -> tx.execute(status -> {
            playerService.updatePlayer(player.getId(), PlayerUpdateRequest.builder()
                    .password(NEW_PASSWORD)
                    .build());
            changeHasLock.countDown();
            try {
                // ログイン側が「旧パスワードで入ろうとする」時間を与えてからコミットする。
                // 行ロックが無ければ、ここでログインが通ってトークンが残ってしまう
                loginAttempted.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));

        assertThat(changeHasLock.await(10, TimeUnit.SECONDS))
                .as("パスワード変更トランザクションが開始しなかった")
                .isTrue();

        String survivingToken = null;
        try {
            // 旧パスワードでのログイン。行ロックによりパスワード変更のコミットまで待たされ、
            // その後は新しいハッシュを読むため失敗するのが期待挙動
            survivingToken = playerService.login(new LoginRequest(NAME, OLD_PASSWORD)).getToken();
        } catch (RuntimeException expected) {
            // 旧パスワードが拒否された（望ましい経路）
        } finally {
            loginAttempted.countDown();
        }

        passwordChange.get(20, TimeUnit.SECONDS);
        executor.shutdown();

        // 旧パスワードでトークンが発行されてしまった場合でも、失効していなければ穴が開いている
        if (survivingToken != null) {
            assertThat(authTokenService.verify(survivingToken))
                    .as("旧パスワードで発行されたトークンが失効をすり抜けて有効なまま残っている")
                    .isEmpty();
        }

        // 新パスワードでは当然ログインできる
        assertThat(playerService.login(new LoginRequest(NAME, NEW_PASSWORD)).getToken()).isNotBlank();

        // 後始末（このクラスは @Transactional ではないため明示的に消す）
        jdbcTemplate.execute("TRUNCATE TABLE auth_tokens, player_organizations, players "
                + "RESTART IDENTITY CASCADE");
    }
}
