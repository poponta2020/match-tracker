package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.LineBroadcastSend;
import com.karuta.matchtracker.entity.LineBroadcastSend.BroadcastStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LineBroadcastSendRepository の結合テスト（AC-6: 原子的な一度きり送信＋クラッシュ回復の再送）。
 *
 * <p>部分ユニークインデックス idx_lbs_dedupe は Hibernate(create-drop) では生成されないため、
 * テスト内で明示的に作成してから ON CONFLICT の挙動を検証する（本番は migration/DataInitializer で作成）。
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("LineBroadcastSendRepository 結合テスト")
class LineBroadcastSendRepositoryTest {

    @Autowired
    private LineBroadcastSendRepository repository;

    @Autowired
    private DataSource dataSource;

    private static final Long GROUP = 1L;
    private static final Long SESSION = 100L;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // Hibernate create-drop は部分ユニークインデックスを生成しないため手動作成する
        new JdbcTemplate(dataSource).execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_lbs_dedupe "
                + "ON line_broadcast_send (broadcast_group_id, session_id) "
                + "WHERE status IN ('SUCCESS', 'RESERVED')");
    }

    @Test
    @DisplayName("同一(グループ,セッション)の2回目 tryAcquire は0行（二重送信防止）")
    void secondAcquireReturnsZero() {
        LocalDateTime now = LocalDateTime.now();
        int first = repository.tryAcquireBroadcastRight(GROUP, SESSION, 10L, 70, now);
        int second = repository.tryAcquireBroadcastRight(GROUP, SESSION, 11L, 70, now);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(repository.findTop100ByBroadcastGroupIdOrderBySentAtDesc(GROUP)).hasSize(1);
    }

    @Test
    @DisplayName("SUCCESS 確定後は再確保できない")
    void cannotReacquireAfterSuccess() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(repository.tryAcquireBroadcastRight(GROUP, SESSION, 10L, 70, now)).isEqualTo(1);
        assertThat(repository.markBroadcastSucceeded(GROUP, SESSION)).isEqualTo(1);

        assertThat(repository.tryAcquireBroadcastRight(GROUP, SESSION, 11L, 70, now)).isZero();
    }

    @Test
    @DisplayName("FAILED に解放後は同一(グループ,セッション)を再確保できる（クラッシュ回復の再送）")
    void canReacquireAfterFailed() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(repository.tryAcquireBroadcastRight(GROUP, SESSION, 10L, 70, now)).isEqualTo(1);
        assertThat(repository.markBroadcastFailed(GROUP, SESSION, "LINE API送信失敗")).isEqualTo(1);

        assertThat(repository.tryAcquireBroadcastRight(GROUP, SESSION, 11L, 70, now)).isEqualTo(1);
    }

    @Test
    @DisplayName("releaseStale で古いRESERVEDがFAILEDに解放され、再確保できる")
    void releaseStaleReservationsAllowsRetry() {
        LocalDateTime old = LocalDateTime.now().minusMinutes(30);
        repository.saveAndFlush(LineBroadcastSend.builder()
                .broadcastGroupId(GROUP).sessionId(SESSION).lineChannelId(10L).recipientCount(70)
                .status(BroadcastStatus.RESERVED).sentAt(old).build());

        int released = repository.releaseStaleBroadcastReservations(LocalDateTime.now().minusMinutes(10));
        assertThat(released).isEqualTo(1);

        assertThat(repository.tryAcquireBroadcastRight(GROUP, SESSION, 11L, 70, LocalDateTime.now()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("別グループ・別セッションは独立して確保できる")
    void independentAcquireAcrossGroupAndSession() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(repository.tryAcquireBroadcastRight(GROUP, SESSION, 10L, 70, now)).isEqualTo(1);
        assertThat(repository.tryAcquireBroadcastRight(GROUP, 101L, 10L, 70, now)).isEqualTo(1);
        assertThat(repository.tryAcquireBroadcastRight(2L, SESSION, 10L, 70, now)).isEqualTo(1);
    }

    @Test
    @DisplayName("existsByBroadcastGroupIdAndSessionIdAndStatusIn で SUCCESS/RESERVED の存在を判定できる")
    void existsBlockingSend() {
        LocalDateTime now = LocalDateTime.now();
        assertThat(repository.existsByBroadcastGroupIdAndSessionIdAndStatusIn(
                GROUP, SESSION, List.of(BroadcastStatus.SUCCESS, BroadcastStatus.RESERVED))).isFalse();

        repository.tryAcquireBroadcastRight(GROUP, SESSION, 10L, 70, now);
        assertThat(repository.existsByBroadcastGroupIdAndSessionIdAndStatusIn(
                GROUP, SESSION, List.of(BroadcastStatus.SUCCESS, BroadcastStatus.RESERVED))).isTrue();
    }
}
