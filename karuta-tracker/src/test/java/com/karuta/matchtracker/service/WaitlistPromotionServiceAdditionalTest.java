package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitlistPromotionService additional tests")
class WaitlistPromotionServiceAdditionalTest {

    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock private NotificationService notificationService;
    @Mock private LineNotificationService lineNotificationService;
    @Mock private DensukeSyncService densukeSyncService;

    @InjectMocks
    private WaitlistPromotionService service;

    @Test
    @DisplayName("demoteToWaitlist excludes demoted player from immediate promotion")
    void demoteToWaitlist_excludesDemotedPlayerFromImmediatePromotion() {
        PracticeParticipant participant = PracticeParticipant.builder()
                .id(1L)
                .sessionId(100L)
                .playerId(10L)
                .matchNumber(1)
                .status(ParticipantStatus.WON)
                .build();
        PracticeSession session = PracticeSession.builder()
                .id(100L)
                .sessionDate(LocalDate.of(2026, 5, 1))
                .build();
        Player triggerPlayer = Player.builder().id(10L).name("A").build();

        when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1)).thenReturn(Optional.of(0));
        when(practiceParticipantRepository
                .findFirstBySessionIdAndMatchNumberAndStatusAndPlayerIdNotOrderByWaitlistNumberAsc(
                        100L, 1, ParticipantStatus.WAITLISTED, 10L))
                .thenReturn(Optional.empty());
        when(playerRepository.findById(10L)).thenReturn(Optional.of(triggerPlayer));
        when(practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                        eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                .thenReturn(List.of());

        service.demoteToWaitlist(1L);

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(participant.getWaitlistNumber()).isEqualTo(1);
        verify(practiceParticipantRepository)
                .findFirstBySessionIdAndMatchNumberAndStatusAndPlayerIdNotOrderByWaitlistNumberAsc(
                        100L, 1, ParticipantStatus.WAITLISTED, 10L);
        verify(practiceParticipantRepository, never())
                .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                        100L, 1, ParticipantStatus.WAITLISTED);
        verify(densukeSyncService).triggerWriteAsync();
    }

    @Test
    @DisplayName("cancelParticipation before noon triggers densuke write-back")
    void cancelBeforeNoon_triggersWriteBack() {
        PracticeParticipant participant = PracticeParticipant.builder()
                .id(1L)
                .sessionId(100L)
                .playerId(10L)
                .matchNumber(1)
                .status(ParticipantStatus.WON)
                .build();
        PracticeSession session = PracticeSession.builder()
                .id(100L)
                .sessionDate(LocalDate.of(2026, 4, 15))
                .capacity(6)
                .build();

        when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())).thenReturn(false);
        when(practiceParticipantRepository.findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                100L, 1, ParticipantStatus.WAITLISTED)).thenReturn(Optional.empty());

        ParticipantStatus result = service.cancelParticipation(1L);

        assertThat(result).isEqualTo(ParticipantStatus.CANCELLED);
        verify(densukeSyncService).triggerWriteAsync();
    }

    @Test
    @DisplayName("rejoinWaitlistBySession triggers densuke write-back")
    void rejoinWaitlist_triggersWriteBack() {
        PracticeParticipant declined = PracticeParticipant.builder()
                .id(1L)
                .sessionId(100L)
                .playerId(10L)
                .matchNumber(1)
                .status(ParticipantStatus.WAITLIST_DECLINED)
                .build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(
                100L, 10L, ParticipantStatus.WAITLIST_DECLINED)).thenReturn(List.of(declined));
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1)).thenReturn(Optional.of(3));

        int count = service.rejoinWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(1);
        assertThat(declined.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(declined.getWaitlistNumber()).isEqualTo(4);
        verify(densukeSyncService).triggerWriteAsync();
    }
}