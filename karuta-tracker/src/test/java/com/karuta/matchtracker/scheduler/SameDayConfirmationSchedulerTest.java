package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SameDayConfirmationScheduler テスト")
class SameDayConfirmationSchedulerTest {

    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private WaitlistPromotionService waitlistPromotionService;
    @Mock
    private LineNotificationService lineNotificationService;

    @InjectMocks
    private SameDayConfirmationScheduler scheduler;

    @Test
    @DisplayName("OFFERED参加者の期限切れ処理がWaitlistPromotionService経由で実行される")
    void confirmSameDayParticipants_delegatesToWaitlistPromotionService() {
        LocalDate today = LocalDate.now();
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(today).capacity(6).build();

        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        scheduler.confirmSameDayParticipants();

        verify(waitlistPromotionService).expireOfferedForSameDayConfirmation(session);
        verify(lineNotificationService).sendSameDayConfirmationNotification(session);
    }

    @Test
    @DisplayName("OFFERED参加者がいない場合でも確定通知は送信される")
    void confirmSameDayParticipants_noOffered_stillNotifies() {
        LocalDate today = LocalDate.now();
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(today).capacity(6).build();

        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of(session));

        scheduler.confirmSameDayParticipants();

        verify(waitlistPromotionService).expireOfferedForSameDayConfirmation(session);
        verify(lineNotificationService).sendSameDayConfirmationNotification(session);
    }

    @Test
    @DisplayName("セッションがない日は何もしない")
    void confirmSameDayParticipants_noSessions() {
        when(practiceSessionRepository.findByDateRange(any(), any())).thenReturn(List.of());

        scheduler.confirmSameDayParticipants();

        verify(waitlistPromotionService, never()).expireOfferedForSameDayConfirmation(any());
        verify(lineNotificationService, never()).sendSameDayConfirmationNotification(any());
    }
}
