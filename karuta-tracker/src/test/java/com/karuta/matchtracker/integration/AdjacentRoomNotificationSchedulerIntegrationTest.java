package com.karuta.matchtracker.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.repository.AdjacentRoomNotificationRepository;
import com.karuta.matchtracker.scheduler.AdjacentRoomNotificationScheduler;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * AdjacentRoomNotificationScheduler の実DBトランザクション回帰テスト（Issue #1034）
 *
 * 重複通知防止を「同一トランザクション内で一意制約違反を catch して握りつぶす」方式で
 * 実装すると、Hibernate が flush 失敗時点でトランザクションを rollback-only にマークするため、
 * TransactionTemplate のコミットで UnexpectedRollbackException が発生し ERROR ログになる。
 * モックの TransactionTemplate ではコミット時の rollback-only 検査を再現できないため、
 * TestContainers の実 PostgreSQL + 実トランザクションで検証する。
 */
@SpringBootTest
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("隣室通知スケジューラー 統合回帰テスト（rollback-only）")
class AdjacentRoomNotificationSchedulerIntegrationTest {

    @Autowired
    private AdjacentRoomNotificationScheduler scheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** TOCTOU競合テストで事前チェックのすり抜けを再現するための spy（他のテストでは素通し） */
    @MockitoSpyBean
    private AdjacentRoomNotificationRepository adjacentRoomNotificationRepository;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger schedulerLogger;

    // 監視期間（翌日〜40日先）に確実に入る対象日
    private final LocalDate sessionDate = JstDateTimeUtil.today().plusDays(3);

    private void resetTables() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE practice_sessions, adjacent_room_notifications, " +
                "room_availability_cache, notifications, players RESTART IDENTITY CASCADE");
    }

    @BeforeEach
    void setUp() {
        resetTables();

        // venue 3（すずらん・隣室チェック対象）の未来セッション。
        // capacity=4・参加者0人 → 残り4人 = 閾値ちょうどで通知対象になる
        jdbcTemplate.update(
                "INSERT INTO practice_sessions " +
                "(session_date, total_matches, capacity, venue_id, organization_id, " +
                " created_by, updated_by, created_at, updated_at) " +
                "VALUES (?, 1, 4, 3, 1, 1, 1, NOW(), NOW())", sessionDate);

        // 隣室（はまなす）の夜間枠を空き「○」にする
        jdbcTemplate.update(
                "INSERT INTO room_availability_cache (room_name, target_date, time_slot, status, checked_at) " +
                "VALUES ('はまなす', ?, 'evening', '○', NOW())", sessionDate);

        schedulerLogger = (Logger) LoggerFactory.getLogger(AdjacentRoomNotificationScheduler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        schedulerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        schedulerLogger.detachAppender(logAppender);
        resetTables();
    }

    private Long sessionId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM practice_sessions WHERE session_date = ?", Long.class, sessionDate);
    }

    private List<ILoggingEvent> errorLogs() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .toList();
    }

    @Test
    @DisplayName("未通知の段階では通知レコードが作成され、ERRORログは出ない")
    void firstRun_createsNotificationRecord_withoutError() {
        scheduler.checkCapacityAndNotify();

        Integer recordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM adjacent_room_notifications WHERE session_id = ? AND remaining_count = 4",
                Integer.class, sessionId());
        assertThat(recordCount).isEqualTo(1);
        assertThat(errorLogs()).isEmpty();
    }

    @Test
    @DisplayName("回帰: 通知済み段階の再実行で rollback-only の ERROR が発生しない（Issue #1034）")
    void rerunAtNotifiedLevel_doesNotLogRollbackOnlyError() {
        // 本番の 12:00Z 実行相当: この (session, remaining=4) は通知済み
        jdbcTemplate.update(
                "INSERT INTO adjacent_room_notifications (session_id, remaining_count, notified_at) " +
                "VALUES (?, 4, NOW())", sessionId());

        // 本番の 12:30Z 実行相当: 残り人数が変わらないまま30分後に再実行
        scheduler.checkCapacityAndNotify();

        // 修正前はここで "Failed to process adjacent room check for session {}:
        // Transaction silently rolled back because it has been marked as rollback-only" が記録される
        assertThat(errorLogs())
                .as("通知済み段階の再実行で ERROR ログが出ないこと")
                .isEmpty();

        // 重複レコードが増えず、通知も再送されない
        Integer recordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM adjacent_room_notifications WHERE session_id = ?",
                Integer.class, sessionId());
        assertThat(recordCount).isEqualTo(1);

        Integer sentNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(sentNotifications).isZero();
    }

    @Test
    @DisplayName("回帰: TOCTOU競合（事前チェックすり抜け→実DBの一意制約違反）でも rollback-only の ERROR にならない")
    void concurrentDuplicateInsert_doesNotLogRollbackOnlyError() {
        // 他インスタンスが insert 済みの状態
        jdbcTemplate.update(
                "INSERT INTO adjacent_room_notifications (session_id, remaining_count, notified_at) " +
                "VALUES (?, 4, NOW())", sessionId());

        // 事前チェックだけをすり抜けさせ（チェック後に他インスタンスが insert した競合を再現）、
        // save() を実DBの一意制約に衝突させて catch → setRollbackOnly() 経路を実トランザクションで通す
        doReturn(false).when(adjacentRoomNotificationRepository)
                .existsBySessionIdAndRemainingCount(anyLong(), eq(4));

        scheduler.checkCapacityAndNotify();

        // setRollbackOnly() によりコミット試行が行われず、静かにロールバックされること
        // （setRollbackOnly() を外すとグローバル rollback-only だけが残り、コミット時の
        //  UnexpectedRollbackException が外側 catch で ERROR ログになりこの assert が落ちる）
        assertThat(errorLogs())
                .as("TOCTOU競合スキップで ERROR ログが出ないこと")
                .isEmpty();

        Integer recordCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM adjacent_room_notifications WHERE session_id = ?",
                Integer.class, sessionId());
        assertThat(recordCount).isEqualTo(1);

        Integer sentNotifications = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications", Integer.class);
        assertThat(sentNotifications).isZero();
    }
}
