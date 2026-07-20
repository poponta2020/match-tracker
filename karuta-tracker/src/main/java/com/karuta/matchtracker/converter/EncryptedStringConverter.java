package com.karuta.matchtracker.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * LINE 認証情報を AES-256-GCM で暗号化して永続化する JPA コンバータ。
 *
 * <p>{@code autoApply = false}。{@code LineChannel.channelSecret} /
 * {@code channelAccessToken} にのみ {@code @Convert} で<b>明示付与</b>する
 * （他の String カラムへ誤適用しない）。
 *
 * <ul>
 *   <li><b>書込</b>（{@link #convertToDatabaseColumn}）: null/空は素通し／既に {@code enc:v1:} なら
 *       二重暗号化しない／それ以外は暗号化。<b>鍵未設定なら fail-fast</b>
 *       （空鍵暗号化・平文サイレント保存を絶対にしない）。</li>
 *   <li><b>読取</b>（{@link #convertToEntityAttribute}）: null/空は素通し／{@code enc:v1:} 無しは
 *       レガシー平文としてパススルー（<b>鍵不要</b>）／{@code enc:v1:} 有りは復号
 *       （鍵未設定・復号失敗は明確な例外。null／ゴミを返さない）。</li>
 * </ul>
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        if (LineCredentialCipher.isEncrypted(attribute)) {
            // 既に暗号化済み（idempotent）— 二重暗号化しない
            return attribute;
        }
        LineCredentialCipher cipher = LineEncryptionKeyHolder.current()
                .orElseThrow(() -> new IllegalStateException(
                        "LINE_ENCRYPTION_KEY is not configured; cannot encrypt a LINE credential. "
                        + "Set LINE_ENCRYPTION_KEY (base64-encoded 32 bytes) in every environment."));
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        if (!LineCredentialCipher.isEncrypted(dbData)) {
            // レガシー平文（接頭辞なし）— 鍵なしでそのまま返す（パススルー）
            return dbData;
        }
        LineCredentialCipher cipher = LineEncryptionKeyHolder.current()
                .orElseThrow(() -> new IllegalStateException(
                        "LINE_ENCRYPTION_KEY is not configured; cannot decrypt an enc:v1 LINE credential."));
        return cipher.decrypt(dbData);
    }
}
