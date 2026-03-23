package com.karuta.matchtracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Web Push通知の設定
 *
 * VAPID (Voluntary Application Server Identification) キーの管理
 */
@Configuration
@ConfigurationProperties(prefix = "webpush.vapid")
@Getter
@Setter
public class WebPushConfig {

    /** VAPID公開鍵（Base64 URL-safe encoded） */
    private String publicKey;

    /** VAPID秘密鍵（Base64 URL-safe encoded） */
    private String privateKey;

    /** 連絡先（mailto: or URL） */
    private String subject = "mailto:admin@example.com";
}
