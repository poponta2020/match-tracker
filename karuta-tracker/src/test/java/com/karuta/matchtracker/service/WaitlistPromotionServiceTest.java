package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WaitlistPromotionService 辞退・復帰テスト")
class WaitlistPromotionServiceTest {

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private LotteryDeadlineHelper lotteryDeadlineHelper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private LineNotificationService lineNotificationService;
    @Mock
    private DensukeSyncService densukeSyncService;

    @InjectMocks
    private WaitlistPromotionService service;

    @Test
    @DisplayName("セッション単位でキャンセル待ちを辞退できる")
    void declineWaitlistBySession_success() {
        PracticeParticipant p1 = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
        PracticeParticipant p2 = PracticeParticipant.builder()
                .id(2L).sessionId(100L).playerId(10L).matchNumber(3)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of(p1, p2));
        when(practiceParticipantRepository.findWaitlistedAfterNumber(eq(100L), eq(1), eq(2)))
                .thenReturn(List.of());
        when(practiceParticipantRepository.findWaitlistedAfterNumber(eq(100L), eq(3), eq(1)))
                .thenReturn(List.of());

        int count = service.declineWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(2);
        assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.WAITLIST_DECLINED);
        assertThat(p1.getWaitlistNumber()).isNull();
        assertThat(p2.getStatus()).isEqualTo(ParticipantStatus.WAITLIST_DECLINED);
        verify(practiceParticipantRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("辞退時に後続のキャンセル待ち番号が繰り上がる")
    void declineWaitlistBySession_renumbersSubsequent() {
        PracticeParticipant target = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
        PracticeParticipant next = PracticeParticipant.builder()
                .id(3L).sessionId(100L).playerId(30L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(3).build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of(target));
        when(practiceParticipantRepository.findWaitlistedAfterNumber(100L, 1, 2))
                .thenReturn(List.of(next));

        service.declineWaitlistBySession(100L, 10L);

        assertThat(next.getWaitlistNumber()).isEqualTo(2);
        verify(practiceParticipantRepository, times(2)).save(any()); // target + next
    }

    @Test
    @DisplayName("辞退対象がない場合はエラー")
    void declineWaitlistBySession_noTarget() {
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.declineWaitlistBySession(100L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("辞退対象");
    }

    @Test
    @DisplayName("セッション単位でキャンセル待ちに復帰できる（最後尾）")
    void rejoinWaitlistBySession_success() {
        PracticeParticipant p1 = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLIST_DECLINED).build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLIST_DECLINED))
                .thenReturn(List.of(p1));
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1))
                .thenReturn(Optional.of(3));

        int count = service.rejoinWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(1);
        assertThat(p1.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(p1.getWaitlistNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("復帰時にキャンセル待ちがいない場合は番号1になる")
    void rejoinWaitlistBySession_emptyWaitlist() {
        PracticeParticipant p1 = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLIST_DECLINED).build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLIST_DECLINED))
                .thenReturn(List.of(p1));
        when(practiceParticipantRepository.findMaxWaitlistNumber(100L, 1))
                .thenReturn(Optional.empty());

        service.rejoinWaitlistBySession(100L, 10L);

        assertThat(p1.getWaitlistNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("復帰対象がない場合はエラー")
    void rejoinWaitlistBySession_noTarget() {
        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.WAITLIST_DECLINED))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.rejoinWaitlistBySession(100L, 10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("復帰対象");
    }
}
