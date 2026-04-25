package com.karuta.matchtracker.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DensukeImportService#formatDriftLog} / {@link DensukeImportService#warnIfDrifted}
 * の単体テスト（Issue #545）。
 *
 * 挙動の副作用（DB/通知）を持たないログヘルパなので、実インスタンスをそのまま生成して呼び出す。
 */
@DisplayName("DensukeImportService drift ログヘルパ単体テスト (#545)")
class DensukeImportServiceDriftLogTest {

    private DensukeImportService service;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        // ログヘルパは依存を使わないので、コラボレータはすべて null で構築
        service = new DensukeImportService(
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null);

        Logger logger = (Logger) LoggerFactory.getLogger(DensukeImportService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        Logger logger = (Logger) LoggerFactory.getLogger(DensukeImportService.class);
        logger.detachAppender(appender);
    }

    // ====================================================================
    // formatDriftLog
    // ====================================================================

    @Test
    @DisplayName("formatDriftLog: title 取得済みで drift 計算が正しく組み立てられる")
    void formatDriftLog_withTitle_returnsFormattedString() {
        LocalDateTime titleTime = LocalDateTime.of(2026, 4, 23, 12, 45);
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of("鮎川知佳", titleTime);

        String log = service.formatDriftLog(20L, playerIdMap, memberLastChangeTimes, detectedAt);

        assertThat(log).isEqualTo(
                "densukeTitle=2026-04-23T12:45 detectedAt=2026-04-23T12:50:56 drift=5m");
    }

    @Test
    @DisplayName("formatDriftLog: detectedAt のナノ秒部は切り捨てられる")
    void formatDriftLog_detectedAtTruncatesBelowSecond() {
        LocalDateTime titleTime = LocalDateTime.of(2026, 4, 23, 12, 45);
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56, 789_000_000);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of("鮎川知佳", titleTime);

        String log = service.formatDriftLog(20L, playerIdMap, memberLastChangeTimes, detectedAt);

        assertThat(log).contains("detectedAt=2026-04-23T12:50:56 ");
        assertThat(log).doesNotContain(".789");
    }

    @Test
    @DisplayName("formatDriftLog: title 未取得なら (unknown) を返す")
    void formatDriftLog_withoutTitle_returnsUnknown() {
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of(); // title 情報なし

        String log = service.formatDriftLog(20L, playerIdMap, memberLastChangeTimes, detectedAt);

        assertThat(log).isEqualTo(
                "densukeTitle=(unknown) detectedAt=2026-04-23T12:50:56 drift=(unknown)");
    }

    @Test
    @DisplayName("formatDriftLog: playerIdMap が null でも (unknown) を返してNPEを投げない")
    void formatDriftLog_nullPlayerIdMap_returnsUnknown() {
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56);

        String log = service.formatDriftLog(20L, null, null, detectedAt);

        assertThat(log).startsWith("densukeTitle=(unknown)");
    }

    @Test
    @DisplayName("formatDriftLog: 24時間を超える drift も分単位で正しく出る")
    void formatDriftLog_largeDrift_reportsMinutes() {
        LocalDateTime titleTime = LocalDateTime.of(2026, 4, 22, 22, 6);
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of("鮎川知佳", titleTime);

        String log = service.formatDriftLog(20L, playerIdMap, memberLastChangeTimes, detectedAt);

        assertThat(log).isEqualTo(
                "densukeTitle=2026-04-22T22:06 detectedAt=2026-04-23T12:50:56 drift=884m");
    }

    // ====================================================================
    // warnIfDrifted
    // ====================================================================

    @Test
    @DisplayName("warnIfDrifted: drift > 10分 で WARN が出る")
    void warnIfDrifted_aboveThreshold_emitsWarn() {
        LocalDateTime titleTime = LocalDateTime.of(2026, 4, 22, 22, 6);
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of("鮎川知佳", titleTime);

        service.warnIfDrifted("Phase3-C2", 934L, 1, 20L, playerIdMap, memberLastChangeTimes, detectedAt);

        List<ILoggingEvent> warns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warns).hasSize(1);
        String msg = warns.get(0).getFormattedMessage();
        assertThat(msg).contains("Densuke change-time drift detected:");
        assertThat(msg).contains("phase=Phase3-C2");
        assertThat(msg).contains("session=934");
        assertThat(msg).contains("match=1");
        assertThat(msg).contains("player=20");
        assertThat(msg).contains("(鮎川知佳)");
        assertThat(msg).contains("densukeTitle=2026-04-22T22:06");
        assertThat(msg).contains("detectedAt=2026-04-23T12:50:56");
        assertThat(msg).contains("driftMinutes=884");
    }

    @Test
    @DisplayName("warnIfDrifted: drift ちょうど10分は WARN を出さない（境界値）")
    void warnIfDrifted_atThreshold_suppressesWarn() {
        LocalDateTime titleTime = LocalDateTime.of(2026, 4, 23, 12, 40);
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of("鮎川知佳", titleTime);

        service.warnIfDrifted("Phase3-C2", 934L, 1, 20L, playerIdMap, memberLastChangeTimes, detectedAt);

        assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN))
                .isEmpty();
    }

    @Test
    @DisplayName("warnIfDrifted: drift が閾値以下なら WARN を出さない")
    void warnIfDrifted_belowThreshold_suppressesWarn() {
        LocalDateTime titleTime = LocalDateTime.of(2026, 4, 23, 12, 45);
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of("鮎川知佳", titleTime);

        service.warnIfDrifted("Phase3-C2", 934L, 1, 20L, playerIdMap, memberLastChangeTimes, detectedAt);

        assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN))
                .isEmpty();
    }

    @Test
    @DisplayName("warnIfDrifted: title 未取得の場合は drift が大きくても WARN を抑制する")
    void warnIfDrifted_withoutTitle_suppressesWarn() {
        LocalDateTime detectedAt = LocalDateTime.of(2026, 4, 23, 12, 50, 56);
        Map<Long, String> playerIdMap = Map.of(20L, "鮎川知佳");
        Map<String, LocalDateTime> memberLastChangeTimes = Map.of(); // title 情報なし

        service.warnIfDrifted("Phase3-C2", 934L, 1, 20L, playerIdMap, memberLastChangeTimes, detectedAt);

        assertThat(appender.list.stream().filter(e -> e.getLevel() == Level.WARN))
                .isEmpty();
    }
}
