package com.karuta.matchtracker.converter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AES-256-GCM 暗号ロジックの単体テスト（DB 非依存）。
 * AC-1（enc:v1: 接頭辞・平文と異なる）／AC-2（往復一致）／AC-8（誤鍵で例外）／AC-9（長さ ≤ 255）を担保する。
 */
@DisplayName("LineCredentialCipher 単体テスト")
class LineCredentialCipherTest {

    // base64 エンコードした32バイト（AES-256）テスト鍵。2つは異なる鍵。
    private static final String KEY_A = "i1paoEBF5XgTlTLDjO3C8Lv8wDa6S88CXCXjSno83LI=";
    private static final String KEY_B = "3WLb6l75Nj2gYRmhD0bS6iEzis4gyXYTvoplQH/yqzg=";

    // LINE channel secret は 32文字（16進）。AC-9 の長さ検証に使う代表値。
    private static final String SECRET_32 = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("AC-1: 暗号化結果は enc:v1: 始まりで平文と異なる")
    void encryptProducesVersionedCiphertextDifferentFromPlaintext() {
        LineCredentialCipher cipher = new LineCredentialCipher(KEY_A);

        String encrypted = cipher.encrypt(SECRET_32);

        assertThat(encrypted).startsWith("enc:v1:");
        assertThat(encrypted).isNotEqualTo(SECRET_32);
        assertThat(encrypted).doesNotContain(SECRET_32);
    }

    @Test
    @DisplayName("AC-2: 暗号化→復号は元の平文に完全一致する")
    void encryptThenDecryptRoundTrips() {
        LineCredentialCipher cipher = new LineCredentialCipher(KEY_A);

        String roundTripped = cipher.decrypt(cipher.encrypt(SECRET_32));

        assertThat(roundTripped).isEqualTo(SECRET_32);
    }

    @Test
    @DisplayName("同じ平文でも IV がレコード毎ランダムなので暗号文は毎回異なる")
    void encryptUsesRandomIvPerRecord() {
        LineCredentialCipher cipher = new LineCredentialCipher(KEY_A);

        String first = cipher.encrypt(SECRET_32);
        String second = cipher.encrypt(SECRET_32);

        assertThat(first).isNotEqualTo(second);
        // それでも両方とも正しく復号できる
        assertThat(cipher.decrypt(first)).isEqualTo(SECRET_32);
        assertThat(cipher.decrypt(second)).isEqualTo(SECRET_32);
    }

    @Test
    @DisplayName("AC-8: 誤った鍵で復号すると明確な例外を投げる（null/ゴミを返さない）")
    void decryptWithWrongKeyThrows() {
        String encrypted = new LineCredentialCipher(KEY_A).encrypt(SECRET_32);
        LineCredentialCipher wrongKeyCipher = new LineCredentialCipher(KEY_B);

        assertThatThrownBy(() -> wrongKeyCipher.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decrypt")
                // 例外メッセージに平文・鍵を含めない
                .hasMessageNotContaining(SECRET_32)
                .hasMessageNotContaining(KEY_A)
                .hasMessageNotContaining(KEY_B);
    }

    @Test
    @DisplayName("AC-9: 32文字 secret の暗号文は varchar(255) に収まる")
    void ciphertextOf32CharSecretFitsIn255() {
        LineCredentialCipher cipher = new LineCredentialCipher(KEY_A);

        String encrypted = cipher.encrypt(SECRET_32);

        assertThat(encrypted.length()).isLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("長いアクセストークン（TEXT 相当）も往復一致する")
    void longTokenRoundTrips() {
        LineCredentialCipher cipher = new LineCredentialCipher(KEY_A);
        String longToken = "x".repeat(500);

        assertThat(cipher.decrypt(cipher.encrypt(longToken))).isEqualTo(longToken);
    }

    @Test
    @DisplayName("鍵が不正（blank / 不正base64 / 長さ違い）ならコンストラクタで fail-fast")
    void invalidKeyRejectedAtConstruction() {
        assertThatThrownBy(() -> new LineCredentialCipher(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LineCredentialCipher("   "))
                .isInstanceOf(IllegalArgumentException.class);
        // base64 デコードは通るが 16バイトしかない（AES-256 は 32バイト必須）
        assertThatThrownBy(() -> new LineCredentialCipher("AAAAAAAAAAAAAAAAAAAAAA=="))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("enc:v1: 接頭辞を持たない値を decrypt に渡すと例外")
    void decryptRejectsNonCiphertext() {
        LineCredentialCipher cipher = new LineCredentialCipher(KEY_A);

        assertThatThrownBy(() -> cipher.decrypt("plain-secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("isEncrypted は接頭辞の有無を鍵なしで判定する")
    void isEncryptedDetectsPrefix() {
        assertThat(LineCredentialCipher.isEncrypted("enc:v1:abc")).isTrue();
        assertThat(LineCredentialCipher.isEncrypted("plain")).isFalse();
        assertThat(LineCredentialCipher.isEncrypted(null)).isFalse();
    }
}
