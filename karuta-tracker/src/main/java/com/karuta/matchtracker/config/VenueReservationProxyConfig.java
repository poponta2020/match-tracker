package com.karuta.matchtracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * venue-reservation-proxy 機能の設定値バインダー。
 *
 * <p>application.properties の {@code venue-reservation-proxy.*} を読み込む。
 * venue 別設定は {@link VenueProperties} に展開し、{@code Map<venueKey, VenueProperties>} で
 * 自動マッピングされる。</p>
 *
 * <p>venue キー (kaderu / higashi) は小文字で application.properties に記述すること。
 * ランタイムで {@link com.karuta.matchtracker.service.proxy.VenueId} と紐づける際は
 * {@code VenueId.name().toLowerCase()} で引く運用。</p>
 */
@Configuration
@ConfigurationProperties(prefix = "venue-reservation-proxy")
@Getter
@Setter
public class VenueReservationProxyConfig {

    /** 機能全体の有効化フラグ */
    private boolean enabled = true;

    /** プロキシセッションのタイムアウト (分) */
    private int sessionTimeoutMinutes = 15;

    /** クリーンアップ間隔 (分) */
    private int cleanupIntervalMinutes = 5;

    /** 会場サイトへのリクエストタイムアウト (秒) */
    private int requestTimeoutSeconds = 30;

    /** 会場別設定 (キー: kaderu / higashi) */
    private Map<String, VenueProperties> venues = new HashMap<>();

    @Getter
    @Setter
    public static class VenueProperties {
        /** この会場でプロキシ機能を有効にするか */
        private boolean enabled;

        /** 会場サイトのベースURL (https://k2.p-kashikan.jp 等) */
        private String baseUrl;

        /** 会場サイトのログインユーザーID */
        private String userId;

        /** 会場サイトのログインパスワード */
        private String password;
    }
}
