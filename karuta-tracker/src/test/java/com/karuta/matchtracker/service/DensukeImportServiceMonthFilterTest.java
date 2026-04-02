package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.service.DensukeImportService.ImportResult;
import com.karuta.matchtracker.service.DensukeScraper.DensukeData;
import com.karuta.matchtracker.service.DensukeScraper.ScheduleEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeImportService month filter tests")
class DensukeImportServiceMonthFilterTest {

    @Mock private DensukeScraper densukeScraper;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private PlayerService playerService;
    @Mock private VenueRepository venueRepository;
    @Mock private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationService notificationService;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock private LotteryService lotteryService;
    @Mock private WaitlistPromotionService waitlistPromotionService;
    @Mock private PracticeParticipantService practiceParticipantService;
    @Mock private LineNotificationService lineNotificationService;

    @InjectMocks
    private DensukeImportService densukeImportService;

    @Test
    @DisplayName("targetDate applies month-level filter and imports all days in that month")
    void targetDateFiltersByMonth() throws IOException {
        Player p1 = Player.builder().id(1L).name("A").role(Player.Role.PLAYER).build();
        Player p2 = Player.builder().id(2L).name("B").role(Player.Role.PLAYER).build();

        DensukeData data = new DensukeData();

        ScheduleEntry april1 = new ScheduleEntry();
        april1.setDate(LocalDate.of(2026, 4, 1));
        april1.setMatchNumber(1);
        april1.getParticipants().add("A");
        data.getEntries().add(april1);

        ScheduleEntry april8 = new ScheduleEntry();
        april8.setDate(LocalDate.of(2026, 4, 8));
        april8.setMatchNumber(1);
        april8.getParticipants().add("B");
        data.getEntries().add(april8);

        ScheduleEntry may10 = new ScheduleEntry();
        may10.setDate(LocalDate.of(2026, 5, 10));
        may10.setMatchNumber(1);
        may10.getParticipants().add("A");
        data.getEntries().add(may10);

        PracticeSession session1 = PracticeSession.builder()
                .id(11L)
                .sessionDate(LocalDate.of(2026, 4, 1))
                .totalMatches(3)
                .build();
        PracticeSession session2 = PracticeSession.builder()
                .id(12L)
                .sessionDate(LocalDate.of(2026, 4, 8))
                .totalMatches(3)
                .build();

        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(p1, p2));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(eq(LocalDate.of(2026, 4, 1)), eq(1L)))
                .thenReturn(Optional.of(session1));
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(eq(LocalDate.of(2026, 4, 8)), eq(1L)))
                .thenReturn(Optional.of(session2));
        when(lotteryDeadlineHelper.getDeadlineType(1L)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(2026, 4, 1L)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(11L, 1))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(12L, 1))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke(
                "http://example.com", LocalDate.of(2026, 4, 1), 10L, 1L);

        assertThat(result.getRegisteredCount()).isEqualTo(2);
        verify(practiceSessionRepository).findBySessionDateAndOrganizationId(eq(LocalDate.of(2026, 4, 1)), eq(1L));
        verify(practiceSessionRepository).findBySessionDateAndOrganizationId(eq(LocalDate.of(2026, 4, 8)), eq(1L));
        verify(practiceSessionRepository, never())
                .findBySessionDateAndOrganizationId(eq(LocalDate.of(2026, 5, 10)), eq(1L));
    }
}
