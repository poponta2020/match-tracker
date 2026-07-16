package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.LineChatReservation;
import com.karuta.matchtracker.entity.LineChatReservation.ReservationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LineChatReservationRepository の結合テスト（AC-1 の冪等・AC-3 の下支え）。
 *
 * <p>部分ユニークインデックス idx_lcr_group_session_active は Hibernate(create-drop) では生成されないため、
 * テスト内で明示的に作成してから ON CONFLICT / 一意性の挙動を検証する（本番は migration/DataInitializer で作成）。
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("LineChatReservationRepository 結合テスト")
class LineChatReservationRepositoryTest {

    @Autowired
    private LineChatReservationRepository repository;

    @Autowired
    private DataSource dataSource;

    private static final Long GROUP = 1L;
    private static final Long SESSION = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 18, 20, 0);
    private static final LocalDateTime SCHEDULED = LocalDateTime.of(2026, 7, 19, 8, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        // Hibernate create-drop は部分ユニークインデックスを生成しないため手動作成する
        new JdbcTemplate(dataSource).execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_lcr_group_session_active "
                + "ON line_chat_reservations (broadcast_group_id, session_id) "
                + "WHERE status <> 'CANCELLED'");
    }

    @Test
    @DisplayName("同一(グループ,セッション)の2回目 tryInsertPending は0行（冪等・重複予約防止）")
    void secondInsertReturnsZero() {
        int first = repository.tryInsertPendingReservation(GROUP, SESSION, "本文A", SCHEDULED, NOW);
        int second = repository.tryInsertPendingReservation(GROUP, SESSION, "本文B", SCHEDULED, NOW);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(repository.findAll()).hasSize(1);
        // 冪等: 2回目は無視され、1回目の本文が保持される
        assertThat(repository.findAll().get(0).getMessageText()).isEqualTo("本文A");
    }

    @Test
    @DisplayName("CANCELLED は部分ユニークの対象外 — 取消後に同一(グループ,セッション)を再予約できる")
    void canReinsertAfterCancelled() {
        assertThat(repository.tryInsertPendingReservation(GROUP, SESSION, "旧", SCHEDULED, NOW)).isEqualTo(1);

        // 取消（active 行を CANCELLED へ）
        LineChatReservation active = repository.findAll().get(0);
        active.setStatus(ReservationStatus.CANCELLED);
        repository.saveAndFlush(active);

        // 取消後は再予約が通る（CANCELLED 行は履歴として残る）
        assertThat(repository.tryInsertPendingReservation(GROUP, SESSION, "新", SCHEDULED, NOW)).isEqualTo(1);
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findByStatusIn(List.of(ReservationStatus.CANCELLED))).hasSize(1);
        assertThat(repository.findByStatusIn(List.of(ReservationStatus.PENDING))).hasSize(1);
    }

    @Test
    @DisplayName("findFirst...StatusNot(CANCELLED) は active 行のみ返す")
    void findsActiveRowOnly() {
        repository.tryInsertPendingReservation(GROUP, SESSION, "旧", SCHEDULED, NOW);
        LineChatReservation active = repository.findAll().get(0);
        active.setStatus(ReservationStatus.CANCELLED);
        repository.saveAndFlush(active);
        repository.tryInsertPendingReservation(GROUP, SESSION, "新", SCHEDULED, NOW);

        Optional<LineChatReservation> found = repository
                .findFirstByBroadcastGroupIdAndSessionIdAndStatusNot(GROUP, SESSION, ReservationStatus.CANCELLED);
        assertThat(found).isPresent();
        assertThat(found.get().getMessageText()).isEqualTo("新");
        assertThat(found.get().getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    @DisplayName("別グループ・別セッションは独立して予約できる")
    void independentAcrossGroupAndSession() {
        assertThat(repository.tryInsertPendingReservation(GROUP, SESSION, "x", SCHEDULED, NOW)).isEqualTo(1);
        assertThat(repository.tryInsertPendingReservation(GROUP, 101L, "x", SCHEDULED, NOW)).isEqualTo(1);
        assertThat(repository.tryInsertPendingReservation(2L, SESSION, "x", SCHEDULED, NOW)).isEqualTo(1);
    }

    @Test
    @DisplayName("findByStatusInOrderByScheduledSendAtAsc は送信予定時刻の昇順で返す")
    void ordersByScheduledAsc() {
        repository.saveAndFlush(reservation(GROUP, 1L, ReservationStatus.PENDING,
                LocalDateTime.of(2026, 7, 19, 10, 0)));
        repository.saveAndFlush(reservation(GROUP, 2L, ReservationStatus.CANCEL_PENDING,
                LocalDateTime.of(2026, 7, 19, 8, 0)));
        repository.saveAndFlush(reservation(GROUP, 3L, ReservationStatus.RESERVED,
                LocalDateTime.of(2026, 7, 19, 9, 0)));

        List<LineChatReservation> tasks = repository.findByStatusInOrderByScheduledSendAtAsc(
                List.of(ReservationStatus.PENDING, ReservationStatus.CANCEL_PENDING));

        assertThat(tasks).extracting(LineChatReservation::getSessionId).containsExactly(2L, 1L);
    }

    @Test
    @DisplayName("findByStatusAndUpdatedAtBefore で滞留 RESERVING を抽出できる")
    void findsStaleReserving() {
        // @PrePersist/@PreUpdate が updated_at を now() で上書きするため、永続化後に JdbcTemplate で古い値へ矯正する
        Long staleId = repository.saveAndFlush(
                reservation(GROUP, 1L, ReservationStatus.RESERVING, SCHEDULED)).getId();
        Long freshId = repository.saveAndFlush(
                reservation(GROUP, 2L, ReservationStatus.RESERVING, SCHEDULED)).getId();
        JdbcTemplate jt = new JdbcTemplate(dataSource);
        jt.update("UPDATE line_chat_reservations SET updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 18, 19, 0)), staleId);
        jt.update("UPDATE line_chat_reservations SET updated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.of(2026, 7, 18, 21, 0)), freshId);

        List<LineChatReservation> found = repository.findByStatusAndUpdatedAtBefore(
                ReservationStatus.RESERVING, LocalDateTime.of(2026, 7, 18, 20, 0));

        assertThat(found).extracting(LineChatReservation::getId).containsExactly(staleId);
    }

    private static LineChatReservation reservation(Long group, Long session, ReservationStatus status,
                                                   LocalDateTime scheduled) {
        return LineChatReservation.builder()
                .broadcastGroupId(group)
                .sessionId(session)
                .status(status)
                .messageText("本文")
                .scheduledSendAt(scheduled)
                .attemptCount(0)
                .build();
    }
}
