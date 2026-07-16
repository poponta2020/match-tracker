package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.service.CardDivisionBroadcastService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CardDivisionBroadcastScheduler の単体テスト（AC-7）。
 * 30分前ウィンドウ [開始-30, 開始) の内外・8:00 フォールバック発火・ウィンドウ超過で非配信・団体分離（AC-8）を検証する。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CardDivisionBroadcastScheduler")
class CardDivisionBroadcastSchedulerTest {

    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private VenueMatchScheduleRepository venueMatchScheduleRepository;
    @Mock private LineBroadcastGroupRepository lineBroadcastGroupRepository;
    @Mock private CardDivisionBroadcastService cardDivisionBroadcastService;

    @InjectMocks private CardDivisionBroadcastScheduler scheduler;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 16);
    private static final Long ORG = 2L;
    private static final Long GROUP_ID = 1L;

    private PracticeSession session(Long id, Long org, LocalTime startTime, Long venueId) {
        return PracticeSession.builder()
                .id(id).sessionDate(TODAY).organizationId(org)
                .venueId(venueId).startTime(startTime).totalMatches(4).build();
    }

    private LineBroadcastGroup group(Long org) {
        return LineBroadcastGroup.builder().id(GROUP_ID).organizationId(org).name("g").enabled(true).build();
    }

    private void stub(PracticeSession session, LineBroadcastGroup group) {
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(session));
        when(lineBroadcastGroupRepository.findByEnabledTrue()).thenReturn(List.of(group));
    }

    @Test
    @DisplayName("30分前ウィンドウ内（開始10:00・now 9:40）→ 配信")
    void broadcastsInWindow() {
        PracticeSession s = session(100L, ORG, LocalTime.of(10, 0), null);
        stub(s, group(ORG));

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 9, 40));

        verify(cardDivisionBroadcastService, times(1))
                .processGroupBroadcast(any(LineBroadcastGroup.class), eq(s), any());
    }

    @Test
    @DisplayName("ウィンドウ前（開始10:00・windowOpen 9:30・now 9:20）→ 配信しない")
    void skipsBeforeWindow() {
        PracticeSession s = session(101L, ORG, LocalTime.of(10, 0), null);
        stub(s, group(ORG));

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 9, 20));

        verify(cardDivisionBroadcastService, never()).processGroupBroadcast(any(), any(), any());
    }

    @Test
    @DisplayName("開始時刻を過ぎている（開始10:00・now 10:05）→ 配信しない（未配信で残す）")
    void skipsAfterStart() {
        PracticeSession s = session(102L, ORG, LocalTime.of(10, 0), null);
        stub(s, group(ORG));

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 10, 5));

        verify(cardDivisionBroadcastService, never()).processGroupBroadcast(any(), any(), any());
    }

    @Test
    @DisplayName("AC-7: 開始時刻が両情報源とも無い → 8:00 フォールバックで配信（now 8:30）")
    void fallbackBroadcastsAt8() {
        PracticeSession s = session(103L, ORG, null, null); // startTime も venue も無し
        stub(s, group(ORG));

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 8, 30));

        verify(cardDivisionBroadcastService, times(1)).processGroupBroadcast(any(), eq(s), any());
    }

    @Test
    @DisplayName("フォールバック: 8:00 より前（now 7:30）→ 配信しない")
    void fallbackSkipsBeforeEight() {
        PracticeSession s = session(104L, ORG, null, null);
        stub(s, group(ORG));

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 7, 30));

        verify(cardDivisionBroadcastService, never()).processGroupBroadcast(any(), any(), any());
    }

    @Test
    @DisplayName("フォールバック: 正午ウィンドウ超過（now 12:30）→ 配信しない（未配信で残す）")
    void fallbackSkipsAfterNoon() {
        PracticeSession s = session(105L, ORG, null, null);
        stub(s, group(ORG));

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 12, 30));

        verify(cardDivisionBroadcastService, never()).processGroupBroadcast(any(), any(), any());
    }

    @Test
    @DisplayName("AC-8: 他団体のセッションは当該グループの配信対象にしない")
    void doesNotBroadcastOtherOrgSession() {
        PracticeSession otherOrg = session(106L, 999L, LocalTime.of(10, 0), null);
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of(otherOrg));
        when(lineBroadcastGroupRepository.findByEnabledTrue()).thenReturn(List.of(group(ORG)));

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 9, 40));

        verify(cardDivisionBroadcastService, never()).processGroupBroadcast(any(), any(), any());
    }

    @Test
    @DisplayName("開始時刻は venue_match_schedules の match_number=1 を優先する")
    void resolvesStartFromVenueMatch1() {
        PracticeSession s = session(200L, ORG, LocalTime.of(8, 0), 77L);
        when(venueMatchScheduleRepository.findByVenueIdOrderByMatchNumberAsc(77L)).thenReturn(List.of(
                VenueMatchSchedule.builder().matchNumber(2).startTime(LocalTime.of(10, 0)).venueId(77L).build(),
                VenueMatchSchedule.builder().matchNumber(1).startTime(LocalTime.of(9, 0)).venueId(77L).build()));

        assertThat(scheduler.resolveFirstMatchStartTime(s)).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    @DisplayName("当日にセッションが無ければ何もしない")
    void noSessionsNoop() {
        when(practiceSessionRepository.findAllBySessionDate(TODAY)).thenReturn(List.of());

        scheduler.processBroadcasts(TODAY, LocalDateTime.of(2026, 7, 16, 9, 40));

        verify(cardDivisionBroadcastService, never()).processGroupBroadcast(any(), any(), any());
    }
}
