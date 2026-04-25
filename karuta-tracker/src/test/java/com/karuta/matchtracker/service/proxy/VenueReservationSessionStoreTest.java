package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("VenueReservationSessionStore 単体テスト")
class VenueReservationSessionStoreTest {

    private VenueReservationProxyConfig config;
    private VenueReservationSessionStore store;

    @BeforeEach
    void setUp() {
        config = new VenueReservationProxyConfig();
        config.setSessionTimeoutMinutes(15);
        config.setCleanupIntervalMinutes(5);
        store = new VenueReservationSessionStore(config);
    }

    @Test
    @DisplayName("createSession: UUID 形式のトークンが発行され、フィールドが正しくセットされる")
    void createSession_issuesUuidToken_andSetsFields() {
        ProxySession session = store.createSession(
                VenueId.KADERU, 123L, "はまなす", LocalDate.of(2026, 4, 12), 2);

        assertThat(session.getToken()).isNotBlank();
        UUID.fromString(session.getToken()); // 形式検証 (例外が出れば失敗)
        assertThat(session.getVenue()).isEqualTo(VenueId.KADERU);
        assertThat(session.getPracticeSessionId()).isEqualTo(123L);
        assertThat(session.getRoomName()).isEqualTo("はまなす");
        assertThat(session.getDate()).isEqualTo(LocalDate.of(2026, 4, 12));
        assertThat(session.getSlotIndex()).isEqualTo(2);
        assertThat(session.getCookies()).isNotNull();
        assertThat(session.getHiddenFields()).isNotNull().isEmpty();
        assertThat(session.getCreatedAt()).isNotNull();
        assertThat(session.getLastAccessedAt()).isEqualTo(session.getCreatedAt());
        assertThat(session.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("get: 登録済みトークンで ProxySession を取得できる")
    void get_returnsSession_whenTokenExists() {
        ProxySession created = store.createSession(
                VenueId.KADERU, 1L, "はまなす", LocalDate.now(), 1);

        Optional<ProxySession> fetched = store.get(created.getToken());

        assertThat(fetched).isPresent();
        assertThat(fetched.get()).isSameAs(created);
    }

    @Test
    @DisplayName("get: 未登録トークン or null は Optional.empty を返す")
    void get_returnsEmpty_whenTokenUnknown() {
        assertThat(store.get("nonexistent")).isEmpty();
        assertThat(store.get(null)).isEmpty();
    }

    @Test
    @DisplayName("touch: lastAccessedAt が現在時刻で更新される")
    void touch_updatesLastAccessedAt() throws InterruptedException {
        ProxySession session = store.createSession(
                VenueId.KADERU, 1L, "はまなす", LocalDate.now(), 0);
        Instant initial = session.getLastAccessedAt();
        Thread.sleep(10);

        store.touch(session.getToken());

        assertThat(session.getLastAccessedAt()).isAfter(initial);
    }

    @Test
    @DisplayName("touch: 未登録トークンで例外を出さず無視される")
    void touch_ignoresUnknownToken() {
        store.touch("nonexistent"); // 例外が出ないことを確認
    }

    @Test
    @DisplayName("remove: 指定トークンのセッションが削除される")
    void remove_deletesSession() {
        ProxySession session = store.createSession(
                VenueId.KADERU, 1L, "はまなす", LocalDate.now(), 0);

        store.remove(session.getToken());

        assertThat(store.get(session.getToken())).isEmpty();
    }

    @Test
    @DisplayName("KADERU と HIGASHI のセッションは独立して扱われる")
    void differentVenues_areIsolated() {
        ProxySession kaderu = store.createSession(
                VenueId.KADERU, 1L, "はまなす", LocalDate.now(), 0);
        ProxySession higashi = store.createSession(
                VenueId.HIGASHI, 2L, "中ホール", LocalDate.now(), 1);

        assertThat(kaderu.getToken()).isNotEqualTo(higashi.getToken());
        assertThat(store.get(kaderu.getToken()).orElseThrow().getVenue()).isEqualTo(VenueId.KADERU);
        assertThat(store.get(higashi.getToken()).orElseThrow().getVenue()).isEqualTo(VenueId.HIGASHI);

        store.remove(kaderu.getToken());

        assertThat(store.get(kaderu.getToken())).isEmpty();
        assertThat(store.get(higashi.getToken())).isPresent();
    }

    @Test
    @DisplayName("cleanup: タイムアウトを超過したセッションが削除され、期限内のものは残る")
    void cleanup_removesExpiredSessions() {
        ProxySession expired = store.createSession(
                VenueId.KADERU, 1L, "はまなす", LocalDate.now(), 0);
        ProxySession fresh = store.createSession(
                VenueId.KADERU, 2L, "すずらん", LocalDate.now(), 0);
        // expired のみ lastAccessedAt を 16分前にずらす (sessionTimeoutMinutes=15)
        expired.setLastAccessedAt(Instant.now().minusSeconds(16 * 60));

        store.cleanupExpiredSessions(Instant.now());

        assertThat(store.get(expired.getToken())).isEmpty();
        assertThat(store.get(fresh.getToken())).isPresent();
    }

    @Test
    @DisplayName("cleanup: completed=true のセッションは経過時間に関係なく即座に削除される")
    void cleanup_removesCompletedSessionsImmediately() {
        ProxySession completed = store.createSession(
                VenueId.KADERU, 1L, "はまなす", LocalDate.now(), 0);
        completed.setCompleted(true);

        store.cleanupExpiredSessions(Instant.now());

        assertThat(store.get(completed.getToken())).isEmpty();
    }

    @Test
    @DisplayName("cleanup: 削除対象がない場合はサイレントに完了する")
    void cleanup_isNoOp_whenAllSessionsAreFresh() {
        store.createSession(VenueId.KADERU, 1L, "はまなす", LocalDate.now(), 0);
        store.createSession(VenueId.HIGASHI, 2L, "中ホール", LocalDate.now(), 1);

        store.cleanupExpiredSessions(Instant.now());

        assertThat(store.size()).isEqualTo(2);
    }
}
