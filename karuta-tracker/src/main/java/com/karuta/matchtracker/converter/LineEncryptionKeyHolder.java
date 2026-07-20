package com.karuta.matchtracker.converter;

import java.util.Optional;

/**
 * 暗号器（{@link LineCredentialCipher}）を保持する静的ブリッジ。
 *
 * <p>JPA {@link jakarta.persistence.AttributeConverter} は Hibernate が生成し Spring 管理外のため
 * {@code @Value} 注入が効かない。そこで Spring {@code @Component}
 * （{@link LineEncryptionKeyProvider}）が起動時にこのホルダへ暗号器を登録し、
 * {@link EncryptedStringConverter} は {@link #current()} で取得する。
 *
 * <p>テストは {@link #set}/{@link #clear} で直接制御できる（Spring 不要）。
 * {@code volatile} により、起動スレッドでの populate がリクエストスレッドから確実に見える。
 */
public final class LineEncryptionKeyHolder {

    private static volatile LineCredentialCipher cipher;

    private LineEncryptionKeyHolder() {
    }

    public static void set(LineCredentialCipher c) {
        cipher = c;
    }

    public static void clear() {
        cipher = null;
    }

    public static Optional<LineCredentialCipher> current() {
        return Optional.ofNullable(cipher);
    }
}
