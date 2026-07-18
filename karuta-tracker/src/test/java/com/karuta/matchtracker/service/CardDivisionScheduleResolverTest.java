package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link CardDivisionScheduleResolver} の単体テスト。
 *
 * <p>送信予定時刻の10分境界への切り捨て（LINE OAM の予約時刻入力 step=600 スナップ対策）を中心に検証する。
 * 実物の resolver に、モックの venue リポジトリを包んで（{@link CardDivisionBroadcastSchedulerTest} と同じ構成）
 * venue_match_schedules 経路と {@code session.startTime} 経路の双方を通す。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardDivisionScheduleResolver")
class CardDivisionScheduleResolverTest {

    @Mock private VenueMatchScheduleRepository venueMatchScheduleRepository;

    private CardDivisionScheduleResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CardDivisionScheduleResolver(venueMatchScheduleRepository);
    }

    private static final LocalDate DATE = LocalDate.of(2026, 7, 18);
    private static final Long VENUE_ID = 77L;

    /** venueId 無し・startTime のみのセッション（session.startTime 経路）。 */
    private PracticeSession sessionWithStart(LocalTime start) {
        return PracticeSession.builder()
                .id(1L).sessionDate(DATE).organizationId(2L)
                .venueId(null).startTime(start).totalMatches(4).build();
    }

    /** venueId 有りのセッション（venue_match_schedules match_number=1 経路）。 */
    private PracticeSession sessionWithVenue() {
        return PracticeSession.builder()
                .id(1L).sessionDate(DATE).organizationId(2L)
                .venueId(VENUE_ID).startTime(LocalTime.of(8, 0)).totalMatches(4).build();
    }

    private void stubVenueMatch1(LocalTime start) {
        when(venueMatchScheduleRepository.findByVenueIdOrderByMatchNumberAsc(VENUE_ID)).thenReturn(List.of(
                VenueMatchSchedule.builder().matchNumber(1).startTime(start).venueId(VENUE_ID).build()));
    }

    @Test
    @DisplayName(":45開始 → 30分前(非10分境界) は10分境界へ切り捨てる（session.startTime 経路）")
    void floorsNonBoundaryFromSessionStart() {
        // 10:45 開始 → 10:15（非境界）→ 10:10 へ切り捨て（早める）
        PracticeSession s = sessionWithStart(LocalTime.of(10, 45));

        assertThat(resolver.resolveScheduledSendAt(s)).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(10, 10)));
    }

    @Test
    @DisplayName(":15開始 → 30分前(非10分境界) は10分境界へ切り捨てる（venue_match_schedules 経路）")
    void floorsNonBoundaryFromVenueMatch() {
        // 10:15 開始 → 09:45（非境界）→ 09:40 へ切り捨て（早める）
        stubVenueMatch1(LocalTime.of(10, 15));

        assertThat(resolver.resolveScheduledSendAt(sessionWithVenue()))
                .isEqualTo(LocalDateTime.of(DATE, LocalTime.of(9, 40)));
    }

    @Test
    @DisplayName("既に10分境界の送信予定時刻は不変")
    void keepsBoundaryUnchanged() {
        // 09:00 開始 → 08:30（境界）→ そのまま
        PracticeSession s = sessionWithStart(LocalTime.of(9, 0));

        assertThat(resolver.resolveScheduledSendAt(s)).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(8, 30)));
    }

    @Test
    @DisplayName("開始時刻が両情報源とも無ければ 8:00 フォールバック（10分境界なので不変）")
    void fallbackEightAmUnchanged() {
        PracticeSession s = sessionWithStart(null); // venueId=null, startTime=null

        assertThat(resolver.resolveScheduledSendAt(s)).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(8, 0)));
    }

    @Test
    @DisplayName("開始時刻の秒は切り捨てられる（分は10分境界・秒/ナノ秒は0）")
    void zeroesSecondsAndNanos() {
        // 10:15:30 開始 → 09:45:30 → 09:40:00
        PracticeSession s = sessionWithStart(LocalTime.of(10, 15, 30));

        assertThat(resolver.resolveScheduledSendAt(s)).isEqualTo(LocalDateTime.of(DATE, LocalTime.of(9, 40)));
    }

    @Test
    @DisplayName("push配信が参照する resolveFirstMatchStartTime は生の開始時刻のまま（切り捨てない）")
    void firstMatchStartTimeIsNotFloored() {
        // 送信予定時刻は floor するが、push側が使う開始時刻解決は素通し＝push挙動は不変
        PracticeSession s = sessionWithStart(LocalTime.of(10, 15));

        assertThat(resolver.resolveFirstMatchStartTime(s)).isEqualTo(LocalTime.of(10, 15));
    }
}
