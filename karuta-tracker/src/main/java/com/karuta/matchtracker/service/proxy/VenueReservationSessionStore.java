package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.BasicCookieStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * プロキシセッションのインメモリ管理。会場非依存。
 *
 * <p>JVM メモリ管理を採用する根拠は要件定義書 §6.4 を参照
 * (15分タイムアウトでセッションが自然消滅するため永続化不要 / インスタンス再起動の影響は実害なし)。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VenueReservationSessionStore {

    private final VenueReservationProxyConfig config;

    private final ConcurrentMap<String, ProxySession> sessions = new ConcurrentHashMap<>();

    /**
     * 新しいプロキシセッションを生成し、UUID トークンで登録する。
     */
    public ProxySession createSession(VenueId venue,
                                      Long practiceSessionId,
                                      String roomName,
                                      LocalDate date,
                                      int slotIndex) {
        Instant now = Instant.now();
        ProxySession session = ProxySession.builder()
                .token(UUID.randomUUID().toString())
                .venue(venue)
                .practiceSessionId(practiceSessionId)
                .roomName(roomName)
                .date(date)
                .slotIndex(slotIndex)
                .cookies(new BasicCookieStore())
                .hiddenFields(new HashMap<>())
                .createdAt(now)
                .lastAccessedAt(now)
                .completed(false)
                .build();
        sessions.put(session.getToken(), session);
        log.debug("Created proxy session token={} venue={} practiceSessionId={}",
                session.getToken(), venue, practiceSessionId);
        return session;
    }

    public Optional<ProxySession> get(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(token));
    }

    /**
     * lastAccessedAt を現在時刻で更新する。token が未登録の場合は何もしない。
     */
    public void touch(String token) {
        ProxySession session = sessions.get(token);
        if (session != null) {
            session.setLastAccessedAt(Instant.now());
        }
    }

    public void remove(String token) {
        if (token == null) return;
        ProxySession removed = sessions.remove(token);
        if (removed != null) {
            log.debug("Removed proxy session token={} venue={}", token, removed.getVenue());
        }
    }

    /**
     * 期限切れ・完了済みセッションをクリーンアップする。
     *
     * <p>cleanupIntervalMinutes (default 5) 間隔で実行する。fixedDelayString と timeUnit の組み合わせは
     * Spring 6.0+ の機能。</p>
     */
    @Scheduled(
            fixedDelayString = "${venue-reservation-proxy.cleanup-interval-minutes:5}",
            initialDelayString = "${venue-reservation-proxy.cleanup-interval-minutes:5}",
            timeUnit = TimeUnit.MINUTES
    )
    public void cleanupExpiredSessions() {
        cleanupExpiredSessions(Instant.now());
    }

    /**
     * テスト容易性のため now を引数で注入できるようにしたオーバーロード。
     */
    void cleanupExpiredSessions(Instant now) {
        Duration timeout = Duration.ofMinutes(config.getSessionTimeoutMinutes());
        int removed = 0;
        for (var entry : sessions.entrySet()) {
            ProxySession session = entry.getValue();
            boolean expired = Duration.between(session.getLastAccessedAt(), now).compareTo(timeout) > 0;
            if (session.isCompleted() || expired) {
                if (sessions.remove(entry.getKey(), session)) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired/completed proxy sessions (remaining={})", removed, sessions.size());
        }
    }

    /** テスト用: 現在保持しているセッション数 */
    int size() {
        return sessions.size();
    }
}
