package com.karuta.matchtracker.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 隣室通知スケジューラーの {@link Scheduled} cron を静的に検証する（AC-A1）。
 *
 * アノテーションの cron 文字列をリフレクションで取得し、{@link CronExpression} で解釈して
 * Asia/Tokyo 基準の発火時刻を再現する。実行頻度の間引き（JST 1〜5時台スキップ・毎時0分発火）が
 * アノテーション値そのものに反映されていることを保証する。
 */
@DisplayName("AdjacentRoomNotificationScheduler cron 検証")
class AdjacentRoomScheduleCronTest {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private Scheduled scheduledAnnotation() throws NoSuchMethodException {
        return AdjacentRoomNotificationScheduler.class
                .getMethod("checkCapacityAndNotify")
                .getAnnotation(Scheduled.class);
    }

    /** JST で丸一日ぶんの発火「時」を集める（分は 0 固定のはず） */
    private Set<Integer> firedHoursInJst(String cron) {
        CronExpression expr = CronExpression.parse(cron);
        // 適当な平日 0:00 JST の直前から開始し、25時間ぶんの発火を収集
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, JST).minusSeconds(1);
        ZonedDateTime end = start.plusHours(25);
        Set<Integer> hours = new TreeSet<>();
        ZonedDateTime cursor = start;
        while (true) {
            ZonedDateTime next = expr.next(cursor);
            if (next == null || next.isAfter(end)) {
                break;
            }
            assertEquals(0, next.getMinute(), "毎時0分に発火すること: " + next);
            hours.add(next.getHour());
            cursor = next;
        }
        return hours;
    }

    @Test
    @DisplayName("zone は Asia/Tokyo")
    void zoneIsAsiaTokyo() throws NoSuchMethodException {
        assertEquals("Asia/Tokyo", scheduledAnnotation().zone());
    }

    @Test
    @DisplayName("JST 1〜5時台に発火せず、0,6,7…23時に毎時発火する")
    void firesHourlyExceptJst1To5() throws NoSuchMethodException {
        String cron = scheduledAnnotation().cron();

        Set<Integer> firedHours = firedHoursInJst(cron);

        // 期待: 0時 + 6〜23時（= 1日19回）。1〜5時台はスキップ。
        Set<Integer> expected = new TreeSet<>();
        expected.add(0);
        for (int h = 6; h <= 23; h++) {
            expected.add(h);
        }
        assertEquals(expected, firedHours);

        // 深夜帯（JST 1〜5時台）は発火しないことを明示的に確認
        for (int h = 1; h <= 5; h++) {
            assertFalse(firedHours.contains(h), "JST " + h + "時台には発火しないこと");
        }
        // 発火回数は 1日19回
        assertEquals(19, firedHours.size());
    }
}
