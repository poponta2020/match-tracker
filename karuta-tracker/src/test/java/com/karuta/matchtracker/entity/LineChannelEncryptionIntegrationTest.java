package com.karuta.matchtracker.entity;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.converter.LineCredentialCipher;
import com.karuta.matchtracker.converter.LineEncryptionKeyHolder;
import com.karuta.matchtracker.entity.LineChannel.ChannelStatus;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.service.LineMessagingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * LineChannel の @Convert 適用による暗号化永続化の結合テスト（実 DB = Testcontainers）。
 *
 * <p>AC-2（永続化層の往復・保存時は暗号文）／AC-4（復号 secret での署名検証）／
 * AC-5（復号 access_token が平文で送信呼び出しに渡る）／AC-6（鍵なしでレガシー平文読取）を担保。
 *
 * <p>@DataJpaTest スライスは {@code LineEncryptionKeyProvider}(@Component) を読み込まないため、
 * Option B に従い各テストが {@link LineEncryptionKeyHolder} を明示制御する。
 * 鍵不要を主張するテスト（AC-6）は {@code current()} が空であることを検証する。
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("LineChannel 暗号化永続化 結合テスト")
class LineChannelEncryptionIntegrationTest {

    private static final String TEST_KEY = "i1paoEBF5XgTlTLDjO3C8Lv8wDa6S88CXCXjSno83LI=";

    @Autowired
    private LineChannelRepository repository;

    @Autowired
    private TestEntityManager testEntityManager;

    @BeforeEach
    void resetHolder() {
        LineEncryptionKeyHolder.clear();
    }

    @AfterEach
    void clearHolder() {
        LineEncryptionKeyHolder.clear();
    }

    private void withKey() {
        LineEncryptionKeyHolder.set(new LineCredentialCipher(TEST_KEY));
    }

    private LineChannel newChannel(String lineChannelId, String secret, String token) {
        return LineChannel.builder()
                .lineChannelId(lineChannelId)
                .channelSecret(secret)
                .channelAccessToken(token)
                .channelType(ChannelType.PLAYER)
                .status(ChannelStatus.AVAILABLE)
                .build();
    }

    @Test
    @DisplayName("AC-2: DB 上は enc:v1: 暗号文で保存され、getter は平文を返す")
    void secretIsEncryptedAtRestButReadBackAsPlaintext() {
        withKey();
        LineChannel saved = repository.saveAndFlush(
                newChannel("ch-enc-1", "plain-secret-value", "plain-token-value"));
        Long id = saved.getId();
        // 永続化コンテキストを空にして DB から再読込させる（読取パス＝復号を通す）
        testEntityManager.clear();

        // 生カラムは enc:v1: で始まる（@Convert をバイパスする native query）
        String rawSecret = (String) testEntityManager.getEntityManager()
                .createNativeQuery("SELECT channel_secret FROM line_channels WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        String rawToken = (String) testEntityManager.getEntityManager()
                .createNativeQuery("SELECT channel_access_token FROM line_channels WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(rawSecret).startsWith("enc:v1:").doesNotContain("plain-secret-value");
        assertThat(rawToken).startsWith("enc:v1:").doesNotContain("plain-token-value");

        // getter（読取パス）は平文を返す
        LineChannel loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getChannelSecret()).isEqualTo("plain-secret-value");
        assertThat(loaded.getChannelAccessToken()).isEqualTo("plain-token-value");
    }

    @Test
    @DisplayName("AC-4: 復号した channel_secret での webhook 署名検証が暗号化前と同一結果になる")
    void decryptedSecretVerifiesSignatureIdentically() {
        withKey();
        String plaintextSecret = "0123456789abcdef0123456789abcdef";
        String body = "{\"events\":[{\"type\":\"message\"}]}";
        String signature = hmacSha256Base64(plaintextSecret, body);

        LineChannel saved = repository.saveAndFlush(
                newChannel("ch-sig-1", plaintextSecret, "token-sig"));
        testEntityManager.clear();
        LineChannel loaded = repository.findById(saved.getId()).orElseThrow();

        LineMessagingService messagingService = new LineMessagingService(new RestTemplateBuilder());

        // 復号後の secret（＝暗号化前と同値）で検証すると成功する
        assertThat(messagingService.verifySignature(loaded.getChannelSecret(), body, signature)).isTrue();
        // 暗号化前の平文でも同一結果（parity）
        assertThat(messagingService.verifySignature(plaintextSecret, body, signature)).isTrue();
        // 検証が実際に効いている（誤 secret では false）
        assertThat(messagingService.verifySignature("wrong-secret", body, signature)).isFalse();
    }

    @Test
    @DisplayName("AC-5: 復号した access_token が平文のまま送信呼び出し（Bearer）に渡る")
    void decryptedTokenFlowsAsPlaintextToSend() {
        withKey();
        String plaintextToken = "plain-access-token-xyz";

        LineChannel saved = repository.saveAndFlush(
                newChannel("ch-token-1", "secret-token", plaintextToken));
        testEntityManager.clear();
        LineChannel loaded = repository.findById(saved.getId()).orElseThrow();

        // 読取値そのものが平文（enc:v1: が付いていない）
        assertThat(loaded.getChannelAccessToken()).isEqualTo(plaintextToken).doesNotStartWith("enc:v1:");

        // 送信呼び出しへ渡る第1引数（Bearer に載る token）が平文であることを検証
        LineMessagingService spy = mock(LineMessagingService.class);
        spy.sendPushMessage(loaded.getChannelAccessToken(), "U0000000000000000000000000000000", "hello");

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(spy).sendPushMessage(tokenCaptor.capture(), any(), any());
        assertThat(tokenCaptor.getValue()).isEqualTo(plaintextToken).doesNotStartWith("enc:v1:");
    }

    @Test
    @DisplayName("AC-6: 鍵未設定でも接頭辞なしレガシー平文チャネルの読取は成功する")
    void legacyPlaintextChannelReadsWithoutKey() {
        // 鍵は未設定であることを明示（リーク非依存）。native INSERT でコンバータをバイパスし平文行を作る
        assertThat(LineEncryptionKeyHolder.current()).isEmpty();
        testEntityManager.getEntityManager().createNativeQuery(
                        "INSERT INTO line_channels "
                        + "(line_channel_id, channel_secret, channel_access_token, channel_type, status, "
                        + "monthly_message_count, created_at, updated_at) "
                        + "VALUES (:lid, :secret, :token, 'PLAYER', 'AVAILABLE', 0, now(), now())")
                .setParameter("lid", "legacy-ch-1")
                .setParameter("secret", "legacy-plain-secret")
                .setParameter("token", "legacy-plain-token")
                .executeUpdate();
        testEntityManager.flush();
        testEntityManager.clear();

        LineChannel loaded = repository.findAll().stream()
                .filter(c -> "legacy-ch-1".equals(c.getLineChannelId()))
                .findFirst()
                .orElseThrow();

        // 鍵なしのままパススルーで平文が読める
        assertThat(LineEncryptionKeyHolder.current()).isEmpty();
        assertThat(loaded.getChannelSecret()).isEqualTo("legacy-plain-secret");
        assertThat(loaded.getChannelAccessToken()).isEqualTo("legacy-plain-token");
    }

    private static String hmacSha256Base64(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
