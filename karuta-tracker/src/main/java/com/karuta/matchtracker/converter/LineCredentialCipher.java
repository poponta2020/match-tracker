package com.karuta.matchtracker.converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * LINE 認証情報（channel_secret / channel_access_token）の AES-256-GCM 暗号化・復号ロジック。
 *
 * <p>暗号文フォーマット: {@code enc:v1:} + base64( IV(12バイト) ‖ ciphertext ‖ GCM tag(16バイト) )。
 * IV はレコード毎に {@link SecureRandom} で生成する。鍵は base64 エンコードした32バイト（AES-256）。
 *
 * <p>本クラスは Spring 管理外（JPA コンバータから静的に参照される）のため、鍵は
 * {@link LineEncryptionKeyHolder} 経由で注入される。例外メッセージには平文・鍵を一切含めない。
 */
public final class LineCredentialCipher {

    /** 暗号文のバージョン接頭辞。接頭辞の有無で暗号文／レガシー平文を判定する。 */
    static final String VERSION_PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;      // AES-256
    private static final int IV_LENGTH_BYTES = 12;       // GCM 標準 nonce 長
    private static final int GCM_TAG_LENGTH_BITS = 128;  // 認証タグ 16バイト

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * @param base64Key base64 エンコードした32バイト鍵。null・空・不正 base64・長さ不一致は
     *                  {@link IllegalArgumentException}（＝誤設定の early fail-fast）。
     */
    public LineCredentialCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("LINE_ENCRYPTION_KEY is not set");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("LINE_ENCRYPTION_KEY is not valid base64");
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "LINE_ENCRYPTION_KEY must decode to " + KEY_LENGTH_BYTES
                    + " bytes (AES-256), but got " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 平文を暗号化し {@code enc:v1:} 付き暗号文を返す。
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // 平文・鍵はメッセージに含めない
            throw new IllegalStateException("Failed to encrypt LINE credential", e);
        }
    }

    /**
     * {@code enc:v1:} 付き暗号文を復号して平文を返す。
     *
     * <p>鍵誤り・データ破損は GCM 認証失敗（{@code AEADBadTagException}）となり
     * {@link IllegalStateException} を投げる（null やゴミを返さない）。
     */
    public String decrypt(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(VERSION_PREFIX)) {
            throw new IllegalArgumentException("Value is not an enc:v1 ciphertext");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(VERSION_PREFIX.length()));
            if (combined.length < IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                throw new IllegalStateException("Failed to decrypt LINE credential (ciphertext too short)");
            }
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(combined, IV_LENGTH_BYTES, combined.length - IV_LENGTH_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // 鍵誤り／破損（GCM tag 不一致）。平文・鍵はメッセージに含めない
            throw new IllegalStateException("Failed to decrypt LINE credential (wrong key or corrupted data)", e);
        }
    }

    /** 値が {@code enc:v1:} 接頭辞を持つ暗号文かどうか（鍵不要の判定）。 */
    static boolean isEncrypted(String value) {
        return value != null && value.startsWith(VERSION_PREFIX);
    }
}
