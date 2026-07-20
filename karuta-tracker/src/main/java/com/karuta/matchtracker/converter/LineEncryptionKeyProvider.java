package com.karuta.matchtracker.converter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 起動時に環境変数 {@code line.encryption-key}（{@code LINE_ENCRYPTION_KEY}）を読み、
 * {@link LineEncryptionKeyHolder} へ暗号器を登録する Spring コンポーネント。
 *
 * <ul>
 *   <li>未設定（空）→ 何もしない。LINE を使わないクラブは鍵なしで正常起動できる（＝遅延検証の要）。</li>
 *   <li>設定あり → base64→32バイトを検証し暗号器を構築。<b>不正形式なら {@code @PostConstruct} で fail-fast</b>
 *       （誤設定を起動時に検出する）。</li>
 * </ul>
 */
@Component
@Slf4j
public class LineEncryptionKeyProvider {

    @Value("${line.encryption-key:}")
    private String encryptionKey;

    @PostConstruct
    public void init() {
        if (encryptionKey == null || encryptionKey.isBlank()) {
            log.info("LINE_ENCRYPTION_KEY is not set; LINE credential encryption is inactive "
                    + "(legacy plaintext passthrough only, no encrypted write possible)");
            return;
        }
        // 不正な鍵はここでコンストラクタが例外を投げ、起動が fail-fast する（鍵値はログに出さない）
        LineEncryptionKeyHolder.set(new LineCredentialCipher(encryptionKey));
        log.info("LINE credential encryption is active (AES-256-GCM)");
    }
}
