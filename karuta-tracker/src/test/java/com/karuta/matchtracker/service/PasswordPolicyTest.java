package com.karuta.matchtracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PasswordPolicy の単体テスト（auth-tokenization）
 *
 * 実際の BCryptPasswordEncoder を使う（ハッシュ形式とバイト長境界を本物で確かめるため）。
 */
@DisplayName("PasswordPolicy 単体テスト")
class PasswordPolicyTest {

    private PasswordPolicy passwordPolicy;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder();
        passwordPolicy = new PasswordPolicy(encoder);
    }

    @Test
    @DisplayName("BCryptハッシュを返し、元のパスワードで照合できる")
    void testEncode_ProducesVerifiableBcryptHash() {
        String hash = passwordPolicy.encode("password123");

        assertThat(hash).matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
        assertThat(encoder.matches("password123", hash)).isTrue();
    }

    @Test
    @DisplayName("8文字未満は拒否する（既存の最低文字数要件）")
    void testEncode_RejectsTooShort() {
        assertThatThrownBy(() -> passwordPolicy.encode("short7c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8文字以上");
    }

    @Test
    @DisplayName("null・空文字は拒否する")
    void testEncode_RejectsNullOrEmpty() {
        assertThatThrownBy(() -> passwordPolicy.encode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> passwordPolicy.encode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("UTF-8で72バイトちょうどは受け付ける（境界）")
    void testEncode_AcceptsExactlyMaxBytes() {
        String exactly72 = "a".repeat(72);

        assertThat(passwordPolicy.encode(exactly72)).isNotBlank();
    }

    @Test
    @DisplayName("72バイトを超えるパスワードは拒否する（BCryptが黙って切り詰めるため）")
    void testEncode_RejectsOverMaxBytes() {
        assertThatThrownBy(() -> passwordPolicy.encode("a".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("長すぎます");
    }

    @Test
    @DisplayName("日本語は文字数が少なくてもバイト長で判定する（1文字3バイト）")
    void testEncode_CountsUtf8BytesNotChars() {
        // 24文字 = 72バイト（許容）
        assertThat(passwordPolicy.encode("あ".repeat(24))).isNotBlank();

        // 25文字 = 75バイト（拒否）。文字数だけで見ていると見逃す
        assertThatThrownBy(() -> passwordPolicy.encode("あ".repeat(25)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("長すぎます");
    }

    @Test
    @DisplayName("BCryptが72バイトで切り詰める挙動そのものを固定する（この前提が崩れたら上限も見直す）")
    void testBcryptSilentlyTruncatesBeyond72Bytes() {
        // ポリシーを通さず直接 encode すると、73バイト目以降は無視される。
        // 例外は飛ばず「黙って」弱くなるため、PasswordPolicy 側で弾く必要がある。
        String hash = encoder.encode("a".repeat(73));

        assertThat(encoder.matches("a".repeat(72), hash))
                .as("BCryptは72バイトを超える入力を切り捨てる（実測）")
                .isTrue();
    }
}
