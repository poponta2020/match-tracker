package com.karuta.matchtracker.converter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA コンバータの単体テスト。AC-1/2/3/6（単体側）/7 を担保する。
 *
 * <p>静的ホルダを共有するため、Option B の不変条件に従い各テストが自分でホルダ状態を
 * {@link BeforeEach} で確定し {@link AfterEach} でクリアする（他テスト／full-context Spring からの
 * リークに依存しない）。鍵を要さないパススルー系テストは {@code current()} が空であることを明示的に検証する。
 */
@DisplayName("EncryptedStringConverter 単体テスト")
class EncryptedStringConverterTest {

    private static final String KEY_A = "i1paoEBF5XgTlTLDjO3C8Lv8wDa6S88CXCXjSno83LI=";
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final EncryptedStringConverter converter = new EncryptedStringConverter();

    @BeforeEach
    void resetHolder() {
        LineEncryptionKeyHolder.clear();
    }

    @AfterEach
    void clearHolder() {
        LineEncryptionKeyHolder.clear();
    }

    private void withKey() {
        LineEncryptionKeyHolder.set(new LineCredentialCipher(KEY_A));
    }

    @Test
    @DisplayName("AC-1: 書込は enc:v1: 付き暗号文を返し平文と異なる")
    void writeEncryptsWithPrefix() {
        withKey();

        String stored = converter.convertToDatabaseColumn(SECRET);

        assertThat(stored).startsWith("enc:v1:");
        assertThat(stored).isNotEqualTo(SECRET);
    }

    @Test
    @DisplayName("AC-2: 書込→読取の往復で元の平文に一致する")
    void writeThenReadRoundTrips() {
        withKey();

        String stored = converter.convertToDatabaseColumn(SECRET);
        String loaded = converter.convertToEntityAttribute(stored);

        assertThat(loaded).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("既に enc:v1: の値を書込に渡しても二重暗号化しない（idempotent）")
    void writeDoesNotDoubleEncrypt() {
        withKey();
        String alreadyEncrypted = converter.convertToDatabaseColumn(SECRET);

        String again = converter.convertToDatabaseColumn(alreadyEncrypted);

        assertThat(again).isEqualTo(alreadyEncrypted);
        // 復号しても secret 1回分に戻る（二重復号にならない）
        assertThat(converter.convertToEntityAttribute(again)).isEqualTo(SECRET);
    }

    @Test
    @DisplayName("AC-3/AC-6(単体): 接頭辞なしレガシー平文は鍵なしでパススルー")
    void readLegacyPlaintextPassesThroughWithoutKey() {
        // 鍵は未設定であることを明示検証（リーク非依存）
        assertThat(LineEncryptionKeyHolder.current()).isEmpty();

        String loaded = converter.convertToEntityAttribute("legacy-plain-secret");

        assertThat(loaded).isEqualTo("legacy-plain-secret");
    }

    @Test
    @DisplayName("AC-7: 鍵未設定で暗号化書込が発生したら fail-fast（空鍵暗号化・平文保存をしない）")
    void writeWithoutKeyFailsFast() {
        assertThat(LineEncryptionKeyHolder.current()).isEmpty();

        assertThatThrownBy(() -> converter.convertToDatabaseColumn(SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINE_ENCRYPTION_KEY")
                // 平文をメッセージに含めない
                .hasMessageNotContaining(SECRET);
    }

    @Test
    @DisplayName("鍵未設定で enc:v1: 値の復号が必要になったら明確な例外（null/ゴミを返さない）")
    void readEncryptedWithoutKeyThrows() {
        // まず鍵ありで暗号文を作る
        withKey();
        String encrypted = converter.convertToDatabaseColumn(SECRET);
        LineEncryptionKeyHolder.clear();
        assertThat(LineEncryptionKeyHolder.current()).isEmpty();

        assertThatThrownBy(() -> converter.convertToEntityAttribute(encrypted))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LINE_ENCRYPTION_KEY");
    }

    @Test
    @DisplayName("null / 空文字は暗号化・復号ともに素通し（鍵不要）")
    void nullAndEmptyAreNullSafe() {
        assertThat(LineEncryptionKeyHolder.current()).isEmpty();

        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }
}
