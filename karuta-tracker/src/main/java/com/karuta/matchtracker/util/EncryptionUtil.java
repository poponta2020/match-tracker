package com.karuta.matchtracker.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM暗号化ユーティリティ
 *
 * LINEチャネルの認証情報（channel_secret, channel_access_token）の
 * DBへの暗号化保存・復号に使用する。
 */
public final class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private EncryptionUtil() {
    }

    /**
     * 平文を暗号化してBase64文字列で返す
     *
     * @param plaintext  暗号化する文字列
     * @param key        暗号化キー（32バイト = 256ビット）
     * @return Base64エンコードされた暗号文（IV + 暗号文 + タグ）
     */
    public static String encrypt(String plaintext, String key) {
        try {
            byte[] keyBytes = normalizeKey(key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes());

            // IV + 暗号文を結合
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Base64暗号文を復号して平文に戻す
     *
     * @param ciphertext Base64エンコードされた暗号文
     * @param key        暗号化キー
     * @return 復号された平文
     */
    public static String decrypt(String ciphertext, String key) {
        try {
            byte[] keyBytes = normalizeKey(key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] decoded = Base64.getDecoder().decode(ciphertext);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * キーを32バイトに正規化（SHA-256ハッシュを使用）
     */
    private static byte[] normalizeKey(String key) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        return digest.digest(key.getBytes());
    }
}
