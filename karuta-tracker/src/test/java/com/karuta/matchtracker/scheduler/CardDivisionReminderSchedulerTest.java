package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.service.CardDivisionTextService;
import com.karuta.matchtracker.service.LineNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 札分けリマインダースケジューラの単体テスト。
 *
 * <ul>
 *   <li>AC-6: 「1試合目開始の3時間前ウィンドウ [開始-3h, 開始)」に入る当日セッションのみ送信対象。</li>
 *   <li>AC-7: 開始時刻が venue_match_schedules にも PracticeSession.startTime にも無いセッションは送信しない。</li>
 * </ul>
 * 開始時刻の解決順（venue_match_schedules match_number=1 → PracticeSession.startTime → なし）も検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardDivisionReminderScheduler")
class CardDivisionReminderSchedulerTest {

    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private VenueMatchScheduleRepository venueMatchScheduleRepository;
    @Mock private CardDivisionTextService cardDivisionTextService;
    @Mock private LineNotificationService lineNotificationService;

    @InjectMocks private CardDivisionReminderScheduler scheduler;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 5);
    private static final LocalDateTime NOW_6AM = LocalDateTime.of(2026, 7, 5, 6, 0);
    private static final Long ORG = 1L;

    // ------------------------------------------------------------------
    // 3時間前ウィンドウ（AC-6）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AC-6: 開始3時間前ウィンドウ内（開始8:00・now 6:00）→ 送信")
    void sendsWhenInWindow() {
        PracticeSession session = session(100L, LocalTime.of(8, 0), null);
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(session));
        when(cardDivisionTextService.buildTextForSession(session)).thenReturn("TEXT");

        scheduler.processReminders(TODAY, NOW_6AM);

        verify(lineNotificationService, times(1)).sendCardDivisionReminder(100L, ORG, "TEXT");
    }

    @Test
    @DisplayName("ウィンドウ前（開始10:00・windowOpen 7:00・now 6:00）→ 送信しない")
    void skipsBeforeWindow() {
        PracticeSession session = session(101L, LocalTime.of(10, 0), null);
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(session));

        scheduler.processReminders(TODAY, NOW_6AM);

        verify(lineNotificationService, never()).sendCardDivisionReminder(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("開始時刻を過ぎている（開始5:00・now 6:00）→ 送信しない")
    void skipsAfterStart() {
        PracticeSession session = session(102L, LocalTime.of(5, 0), null);
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(session));

        scheduler.processReminders(TODAY, NOW_6AM);

        verify(lineNotificationService, never()).sendCardDivisionReminder(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("AC-7: 開始時刻が特定できない（venue schedule 無し＋startTime null）→ 送信しない")
    void skipsWhenNoStartTime() {
        PracticeSession session = session(103L, null, null); // venueId null, startTime null
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(session));

        scheduler.processReminders(TODAY, NOW_6AM);

        verify(lineNotificationService, never()).sendCardDivisionReminder(anyLong(), anyLong(), any());
        verify(cardDivisionTextService, never()).buildTextForSession(any());
    }

    // ------------------------------------------------------------------
    // 開始時刻の解決順
    // ------------------------------------------------------------------

    @Test
    @DisplayName("開始時刻は venue_match_schedules の match_number=1 を優先")
    void resolvesFromVenueMatch1() {
        PracticeSession session = session(200L, LocalTime.of(8, 0), 77L); // startTime 8:00 だが venue が優先
        when(venueMatchScheduleRepository.findByVenueIdOrderByMatchNumberAsc(77L)).thenReturn(List.of(
                vms(2, LocalTime.of(10, 0)),
                vms(1, LocalTime.of(9, 0))));

        assertThat(scheduler.resolveFirstMatchStartTime(session)).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("venue schedule に match_number=1 が無ければ PracticeSession.startTime にフォールバック")
    void fallsBackToSessionStartWhenNoMatch1() {
        PracticeSession session = session(201L, LocalTime.of(8, 0), 77L);
        when(venueMatchScheduleRepository.findByVenueIdOrderByMatchNumberAsc(77L)).thenReturn(List.of(
                vms(2, LocalTime.of(10, 0))));

        assertThat(scheduler.resolveFirstMatchStartTime(session)).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    @DisplayName("venueId が無ければ PracticeSession.startTime を使う")
    void usesSessionStartWhenNoVenue() {
        PracticeSession session = session(202L, LocalTime.of(8, 0), null);
        assertThat(scheduler.resolveFirstMatchStartTime(session)).isEqualTo(LocalTime.of(8, 0));
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private PracticeSession session(Long id, LocalTime startTime, Long venueId) {
        return PracticeSession.builder()
                .id(id).sessionDate(TODAY).organizationId(ORG)
                .venueId(venueId).startTime(startTime).totalMatches(3).build();
    }

    private VenueMatchSchedule vms(int matchNumber, LocalTime startTime) {
        return VenueMatchSchedule.builder().matchNumber(matchNumber).startTime(startTime).venueId(77L).build();
    }
}
