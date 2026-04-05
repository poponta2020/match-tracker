package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DeadlineType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DensukeImportService phase coverage tests")
class DensukeImportServicePhaseCoverageTest {

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

    private static final Long ORG_ID = 1L;
    private static final int MATCH_NUMBER = 1;

    private Player playerA;
    private Player playerB;

    @BeforeEach
    void setUp() {
        playerA = Player.builder().id(1L).name("A").role(Player.Role.PLAYER).build();
        playerB = Player.builder().id(2L).name("B").role(Player.Role.PLAYER).build();
    }

    @Test
    @DisplayName("Phase3 3-B1: △ and unregistered creates WAITLISTED at tail")
    void phase3Sankaku_unregistered_createsWaitlisted() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of("A"), List.of("A"));
        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.findMaxWaitlistNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Optional.of(2));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceParticipantRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getPlayerId().equals(1L)
                        && p.getStatus() == ParticipantStatus.WAITLISTED
                        && p.getWaitlistNumber() == 3
                        && p.isDirty()));
    }

    @Test
    @DisplayName("Phase3 3-B2: △ and WON demotes to waitlist")
    void phase3Sankaku_won_demotesToWaitlist() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of("A"), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(11L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.WON).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(waitlistPromotionService).demoteToWaitlistSuppressed(11L);
    }

    @ParameterizedTest(name = "Phase3 3-B4/3-B6: △ with status={0} is skipped")
    @EnumSource(value = ParticipantStatus.class, names = {"WAITLISTED", "OFFERED"})
    void phase3Sankaku_waitlistedOrOffered_skips(ParticipantStatus status) throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of("A"), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(11L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(status).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(waitlistPromotionService, never()).demoteToWaitlistSuppressed(11L);
        verify(practiceParticipantRepository, never()).save(existing);
    }

    @Test
    @DisplayName("Phase3 3-B8: △ and terminal status reactivates as WAITLISTED")
    void phase3Sankaku_terminalStatus_reactivatesAsWaitlisted() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of("A"), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(11L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.DECLINED).dirty(false)
                .cancelReason("REASON")
                .cancelledAt(LocalDateTime.of(2026, 4, 1, 10, 0))
                .offeredAt(LocalDateTime.of(2026, 4, 1, 8, 0))
                .offerDeadline(LocalDateTime.of(2026, 4, 1, 12, 0))
                .respondedAt(LocalDateTime.of(2026, 4, 1, 9, 0))
                .build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));
        when(practiceParticipantRepository.findMaxWaitlistNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Optional.of(4));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(existing.getWaitlistNumber()).isEqualTo(5);
        assertThat(existing.isDirty()).isTrue();
        assertThat(existing.getCancelReason()).isNull();
        assertThat(existing.getCancelledAt()).isNull();
        assertThat(existing.getOfferedAt()).isNull();
        assertThat(existing.getOfferDeadline()).isNull();
        assertThat(existing.getRespondedAt()).isNull();
        verify(practiceParticipantRepository).save(existing);
    }

    @Test
    @DisplayName("Phase3: △ with dirty=true is skipped")
    void phase3Sankaku_dirtyParticipant_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of("A"), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(12L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.WON).dirty(true).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(waitlistPromotionService, never()).demoteToWaitlistSuppressed(12L);
        verify(practiceParticipantRepository, never()).save(existing);
    }

    @Test
    @DisplayName("Phase3: ○ WAITLISTED after noon with no vacancy stays waitlisted and becomes dirty")
    void phase3Maru_waitlistedAfterNoonWithoutVacancy_setsDirty() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        session.setCapacity(10);
        DensukeData data = data(date, List.of("A"), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(13L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.WAITLISTED).waitlistNumber(2).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(lotteryDeadlineHelper.isAfterSameDayNoon(date)).thenReturn(true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                session.getId(), MATCH_NUMBER, ParticipantStatus.WON)).thenReturn(10L);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(existing.isDirty()).isTrue();
        verify(practiceParticipantRepository).save(existing);
        verify(practiceParticipantRepository, never()).decrementWaitlistNumbersAfter(session.getId(), MATCH_NUMBER, 2);
    }

    @Test
    @DisplayName("Phase3 3-C2: × and WON cancels participation")
    void phase3Batsu_won_cancelsParticipation() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(21L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.WON).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(waitlistPromotionService).cancelParticipationSuppressed(21L, null, null);
    }

    @Test
    @DisplayName("Phase3 3-C4: × and WAITLISTED marks WAITLIST_DECLINED and compacts queue")
    void phase3Batsu_waitlisted_marksDeclinedAndCompacts() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(22L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.WAITLISTED)
                .waitlistNumber(3).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.WAITLIST_DECLINED);
        assertThat(existing.getWaitlistNumber()).isNull();
        assertThat(existing.isDirty()).isTrue();
        verify(practiceParticipantRepository).save(existing);
        verify(practiceParticipantRepository).decrementWaitlistNumbersAfter(session.getId(), MATCH_NUMBER, 3);
    }

    @Test
    @DisplayName("Phase3 3-C6: × and OFFERED declines offer")
    void phase3Batsu_offered_declinesOffer() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(23L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.OFFERED).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(waitlistPromotionService).respondToOffer(23L, false);
    }

    @Test
    @DisplayName("Phase3 3-C6: offer decline exception is swallowed and processing continues")
    void phase3Batsu_offeredDeclineException_isHandled() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(230L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.OFFERED).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));
        doThrow(new IllegalStateException("decline failed"))
                .when(waitlistPromotionService).respondToOffer(230L, false);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(waitlistPromotionService).respondToOffer(230L, false);
    }

    @Test
    @DisplayName("Phase3 3-C1: × and unregistered is skipped")
    void phase3Batsu_unregistered_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of(), List.of("A"));

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Collections.emptyList());

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(waitlistPromotionService, never()).cancelParticipationSuppressed(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Phase3: × with dirty=true is skipped")
    void phase3Batsu_dirtyParticipant_skips() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(25L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.WON).dirty(true).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(waitlistPromotionService, never()).cancelParticipationSuppressed(org.mockito.ArgumentMatchers.eq(25L), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(practiceParticipantRepository, never()).save(existing);
    }

    @ParameterizedTest(name = "Phase3 3-C8: × with status={0} is skipped")
    @EnumSource(value = ParticipantStatus.class, names = {"CANCELLED", "DECLINED", "WAITLIST_DECLINED"})
    void phase3Batsu_terminalStatuses_skip(ParticipantStatus status) throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 6);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(24L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(status).dirty(false).build();

        mockPhase3Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(waitlistPromotionService, never()).cancelParticipationSuppressed(org.mockito.ArgumentMatchers.eq(24L), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(waitlistPromotionService, never()).respondToOffer(24L, false);
        verify(practiceParticipantRepository, never()).save(existing);
    }

    @Test
    @DisplayName("Phase1 SAME_DAY: full capacity registers as WAITLISTED")
    void phase1SameDay_fullCapacity_registersWaitlisted() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 7);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of("A"), List.of(), List.of("A"));

        mockSameDay(data, session, date, true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantService.isFreeRegistrationOpen(session, MATCH_NUMBER)).thenReturn(false);
        when(practiceParticipantRepository.findMaxWaitlistNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Optional.of(1));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceParticipantRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getPlayerId().equals(1L)
                        && p.getStatus() == ParticipantStatus.WAITLISTED
                        && p.getWaitlistNumber() == 2
                        && !p.isDirty()));
    }

    @Test
    @DisplayName("Phase1 MONTHLY: terminal status reactivates as PENDING")
    void phase1Monthly_terminalStatus_reactivatesPending() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 7);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of("A"), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(31L).sessionId(session.getId()).playerId(1L).matchNumber(MATCH_NUMBER)
                .status(ParticipantStatus.CANCELLED).waitlistNumber(5).dirty(false)
                .cancelReason("REASON").cancelledAt(LocalDateTime.of(2026, 4, 1, 11, 0))
                .build();

        mockPhase1Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.PENDING);
        assertThat(existing.getWaitlistNumber()).isNull();
        assertThat(existing.isDirty()).isTrue();
        assertThat(existing.getCancelReason()).isNull();
        assertThat(existing.getCancelledAt()).isNull();
        verify(practiceParticipantRepository).save(existing);
        verify(practiceParticipantService, never()).isFreeRegistrationOpen(session, MATCH_NUMBER);
    }

    @Test
    @DisplayName("Phase1 SAME_DAY: terminal status reactivates as WAITLISTED when full")
    void phase1SameDay_terminalStatus_reactivatesWaitlisted() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 7);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of("A"), List.of(), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(32L).sessionId(session.getId()).playerId(1L).matchNumber(MATCH_NUMBER)
                .status(ParticipantStatus.WAITLIST_DECLINED).dirty(false).build();

        mockSameDay(data, session, date, true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));
        when(practiceParticipantService.isFreeRegistrationOpen(session, MATCH_NUMBER)).thenReturn(false);
        when(practiceParticipantRepository.findMaxWaitlistNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Optional.of(2));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(existing.getWaitlistNumber()).isEqualTo(3);
        assertThat(existing.isDirty()).isTrue();
        verify(practiceParticipantRepository).save(existing);
    }

    @Test
    @DisplayName("Phase1: duplicate guard skips insert when unique key already exists")
    void phase1_duplicateGuard_skipsInsert() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 7);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of("A"), List.of(), List.of("A"));

        mockPhase1Monthly(data, session, date);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantRepository.existsBySessionIdAndPlayerIdAndMatchNumber(session.getId(), 1L, MATCH_NUMBER))
                .thenReturn(true);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(0);
        verify(practiceParticipantRepository, never()).save(org.mockito.ArgumentMatchers.any(PracticeParticipant.class));
    }

    @Test
    @DisplayName("SAME_DAY phase decision: after deadline uses Phase3")
    void sameDay_afterDeadline_usesPhase3() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 8);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of(), List.of("A"), List.of("A"));
        PracticeParticipant existing = PracticeParticipant.builder()
                .id(41L).sessionId(session.getId()).playerId(1L)
                .matchNumber(MATCH_NUMBER).status(ParticipantStatus.WON).dirty(false).build();

        mockSameDay(data, session, date, false);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(List.of(existing));

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(waitlistPromotionService).demoteToWaitlistSuppressed(41L);
        verify(lotteryDeadlineHelper).isBeforeSameDayDeadline(date);
        verify(lotteryDeadlineHelper, never()).isBeforeDeadline(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(ORG_ID));
        verify(lotteryService, never()).isLotteryConfirmed(anyInt(), anyInt(), org.mockito.ArgumentMatchers.eq(ORG_ID));
    }

    @Test
    @DisplayName("SAME_DAY phase decision: before deadline uses Phase1")
    void sameDay_beforeDeadline_usesPhase1() throws IOException {
        LocalDate date = LocalDate.of(2026, 4, 8);
        PracticeSession session = session(date);
        DensukeData data = data(date, List.of("A"), List.of(), List.of("A"));

        mockSameDay(data, session, date, true);
        when(practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), MATCH_NUMBER))
                .thenReturn(Collections.emptyList());
        when(practiceParticipantService.isFreeRegistrationOpen(session, MATCH_NUMBER)).thenReturn(true);

        ImportResult result = densukeImportService.importFromDensuke("http://example.com", null, 0L, ORG_ID);

        assertThat(result.getRegisteredCount()).isEqualTo(1);
        verify(practiceParticipantRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getPlayerId().equals(1L)
                        && p.getStatus() == ParticipantStatus.WON
                        && !p.isDirty()));
        verify(waitlistPromotionService, never()).demoteToWaitlistSuppressed(org.mockito.ArgumentMatchers.anyLong());
        verify(lotteryDeadlineHelper).isBeforeSameDayDeadline(date);
    }

    private void mockPhase1Monthly(DensukeData data, PracticeSession session, LocalDate date) throws IOException {
        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(playerA, playerB));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(date, ORG_ID))
                .thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(date.getYear(), date.getMonthValue(), ORG_ID)).thenReturn(true);
    }

    private void mockPhase3Monthly(DensukeData data, PracticeSession session, LocalDate date) throws IOException {
        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(playerA, playerB));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(date, ORG_ID))
                .thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.MONTHLY);
        when(lotteryDeadlineHelper.isBeforeDeadline(date.getYear(), date.getMonthValue(), ORG_ID)).thenReturn(false);
        when(lotteryService.isLotteryConfirmed(date.getYear(), date.getMonthValue(), ORG_ID)).thenReturn(true);
    }

    private void mockSameDay(DensukeData data, PracticeSession session, LocalDate date, boolean beforeSameDayDeadline)
            throws IOException {
        when(densukeScraper.scrape(anyString(), anyInt())).thenReturn(data);
        when(playerService.findAllPlayersRaw()).thenReturn(List.of(playerA, playerB));
        when(venueRepository.findAll()).thenReturn(Collections.emptyList());
        when(practiceSessionRepository.findBySessionDateAndOrganizationId(date, ORG_ID))
                .thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.getDeadlineType(ORG_ID)).thenReturn(DeadlineType.SAME_DAY);
        when(lotteryDeadlineHelper.isBeforeSameDayDeadline(date)).thenReturn(beforeSameDayDeadline);
    }

    private PracticeSession session(LocalDate date) {
        return PracticeSession.builder()
                .id(100L)
                .sessionDate(date)
                .totalMatches(1)
                .capacity(10)
                .organizationId(ORG_ID)
                .build();
    }

    private DensukeData data(LocalDate date, List<String> maru, List<String> sankaku, List<String> members) {
        DensukeData data = new DensukeData();
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(date);
        entry.setMatchNumber(MATCH_NUMBER);
        entry.getParticipants().addAll(maru);
        entry.getMaybeParticipants().addAll(sankaku);
        data.getEntries().add(entry);
        data.setMemberNames(members);
        return data;
    }
}
