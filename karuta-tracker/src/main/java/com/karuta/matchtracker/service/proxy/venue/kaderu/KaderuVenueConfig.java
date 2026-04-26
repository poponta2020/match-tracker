package com.karuta.matchtracker.service.proxy.venue.kaderu;

import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.venue.VenueConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * かでる2・7 会場の {@link VenueConfig} 実装。
 *
 * <p>baseUrl / 認証情報は {@link VenueReservationProxyConfig#getVenues()} から
 * {@code "kaderu"} キーで読み出す。</p>
 */
@Component
@RequiredArgsConstructor
public class KaderuVenueConfig implements VenueConfig {

    /** {@link VenueReservationProxyConfig#getVenues()} のキー名 */
    static final String VENUE_KEY = "kaderu";

    /** baseUrl 未設定時のフォールバック (venues/kaderu.md §1) */
    private static final String DEFAULT_BASE_URL = "https://k2.p-kashikan.jp";

    private final VenueReservationProxyConfig proxyConfig;

    @Override
    public VenueId venue() {
        return VenueId.KADERU;
    }

    @Override
    public String baseUrl() {
        VenueReservationProxyConfig.VenueProperties props = proxyConfig.getVenues().get(VENUE_KEY);
        if (props == null || props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            return DEFAULT_BASE_URL;
        }
        return stripTrailingSlash(props.getBaseUrl());
    }

    @Override
    public String displayName() {
        return "かでる2・7";
    }

    /**
     * かでる2・7 の予約システムは {@code /kaderu27/} 配下にデプロイされている。
     * 申込トレイ画面の HTML 内の相対 URL ({@code action="index.php"} 等) は
     * このエントリーポイントを基準に解決する。
     */
    @Override
    public String entryPath() {
        return KaderuReservationClient.ENTRY_PATH;
    }

    @Override
    public Duration sessionTimeout() {
        return Duration.ofMinutes(proxyConfig.getSessionTimeoutMinutes());
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
