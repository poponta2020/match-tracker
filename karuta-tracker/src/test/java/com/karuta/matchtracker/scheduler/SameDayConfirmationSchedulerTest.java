package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SameDayConfirmationScheduler テスト")
class SameDayConfirmationSchedulerTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private LineNotificationService lineNotificationService;

    @InjectMocks
    private SameDayConfirmationScheduler scheduler;

    @Test
    @DisplayName("OFFERED参加者がDECLINEDに変更される")
    void confirmSameDayParticipants_expiresOffered() {
        LocalDate today = LocalDate.now();
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(today).capacity(6).build();
        PracticeParticipant offered = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.OFFERED).build();

        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                .thenReturn(List.of(offered));

        scheduler.confirmSameDayParticipants();

        assertThat(offered.getStatus()).isEqualTo(ParticipantStatus.DECLINED);
        verify(practiceParticipantRepository).save(offered);
        verify(lineNotificationService).sendSameDayConfirmationNotification(session);
    }

    @Test
    @DisplayName("OFFERED参加者がいない場合でも確定通知は送信される")
    void confirmSameDayParticipants_noOffered_stillNotifies() {
        LocalDate today = LocalDate.now();
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(today).capacity(6).build();

        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                .thenReturn(List.of());

        scheduler.confirmSameDayParticipants();

        verify(practiceParticipantRepository, never()).save(any());
        verify(lineNotificationService).sendSameDayConfirmationNotification(session);
    }

    @Test
    @DisplayName("セッションがない日は何もしない")
    void confirmSameDayParticipants_noSessions() {
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of());

        scheduler.confirmSameDayParticipants();

        verify(practiceParticipantRepository, never()).findBySessionIdAndStatus(any(), any());
        verify(lineNotificationService, never()).sendSameDayConfirmationNotification(any());
    }
}
