package com.karuta.matchtracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LINE通知機能の設定
 */
@Configuration
@ConfigurationProperties(prefix = "line")
@Getter
@Setter
public class LineConfig {

    /** チャネル認証情報の暗号化キー */
    private String encryptionKey;

    /** 月間送信上限（デフォルト200） */
    private int monthlyMessageLimit = 200;

    /** 未使用チャネル回収の閾値（日数） */
    private int reclaimInactiveDays = 90;

    /** 回収警告後の猶予期間（日数） */
    private int reclaimGraceDays = 7;
}
