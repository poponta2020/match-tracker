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
        when(practiceParticipantRepository.findMaxWaitlistNumberIncludingOffered(100L, 1)).thenReturn(Optional.of(0));
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
        when(practiceParticipantRepository.findMaxWaitlistNumberIncludingOffered(100L, 1)).thenReturn(Optional.of(3));

        int count = service.rejoinWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(1);
        assertThat(declined.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(declined.getWaitlistNumber()).isEqualTo(4);
        verify(densukeSyncService).triggerWriteAsync();
    }

    @Test
    @DisplayName("demoteToWaitlist assigns number after OFFERED (no duplicate)")
    void demoteToWaitlist_withOfferedExisting_noDuplicate() {
        // OFFERED#1が存在する状態でdemote → #2が割り当てられること
        PracticeParticipant participant = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WON).build();
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 5, 1)).build();
        Player triggerPlayer = Player.builder().id(10L).name("A").build();

        when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        // OFFERED#1が残っているので最大番号は1
        when(practiceParticipantRepository.findMaxWaitlistNumberIncludingOffered(100L, 1)).thenReturn(Optional.of(1));
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
        // OFFERED#1と重複せず#2が割り当てられる
        assertThat(participant.getWaitlistNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("rejoinWaitlistBySession assigns number after OFFERED (no duplicate)")
    void rejoinWaitlist_withOfferedExisting_noDuplicate() {
        // OFFERED#1が存在する状態で復帰 → #2が割り当てられること
        PracticeParticipant declined = PracticeParticipant.builder()
                .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                .status(ParticipantStatus.WAITLIST_DECLINED).build();

        when(practiceParticipantRepository.findBySessionIdAndPlayerIdAndStatus(
                100L, 10L, ParticipantStatus.WAITLIST_DECLINED)).thenReturn(List.of(declined));
        // OFFERED#1が残っているので最大番号は1
        when(practiceParticipantRepository.findMaxWaitlistNumberIncludingOffered(100L, 1)).thenReturn(Optional.of(1));

        int count = service.rejoinWaitlistBySession(100L, 10L);

        assertThat(count).isEqualTo(1);
        assertThat(declined.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        // OFFERED#1と重複せず#2が割り当てられる
        assertThat(declined.getWaitlistNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("promoteWaitlistedAfterCapacityIncrease: 定員内なら全WAITLISTED→OFFERED（応答期限なし）")
    void promoteOnExpand_allWithinCapacity() {
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 5, 1)).capacity(24).build();
        PracticeParticipant w1 = PracticeParticipant.builder()
                .id(10L).sessionId(100L).playerId(201L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();
        PracticeParticipant w2 = PracticeParticipant.builder()
                .id(11L).sessionId(100L).playerId(202L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();

        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                .thenReturn(List.of());
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of(w1, w2));
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                .thenReturn(14L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(0L);
        when(practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                        eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                .thenReturn(List.of(w1, w2));

        service.promoteWaitlistedAfterCapacityIncrease(100L);

        assertThat(w1.getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        assertThat(w1.getOfferDeadline()).isNull();
        assertThat(w1.getOfferedAt()).isNotNull();
        assertThat(w1.isDirty()).isTrue();
        assertThat(w2.getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        assertThat(w2.getOfferDeadline()).isNull();
        assertThat(w2.getOfferedAt()).isNotNull();
        assertThat(w2.isDirty()).isTrue();
    }

    @Test
    @DisplayName("promoteWaitlistedAfterCapacityIncrease: 定員超過分はWAITLISTEDのまま（waitlist_number順で昇格）")
    void promoteOnExpand_partialPromotion() {
        // capacity=20, WON=18 → 残2枠。WAITLISTED 4人のうち #1,#2 のみ OFFERED、#3,#4 は据え置き
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 5, 1)).capacity(20).build();
        PracticeParticipant w1 = PracticeParticipant.builder()
                .id(10L).sessionId(100L).playerId(201L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(1).build();
        PracticeParticipant w2 = PracticeParticipant.builder()
                .id(11L).sessionId(100L).playerId(202L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(2).build();
        PracticeParticipant w3 = PracticeParticipant.builder()
                .id(12L).sessionId(100L).playerId(203L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(3).build();
        PracticeParticipant w4 = PracticeParticipant.builder()
                .id(13L).sessionId(100L).playerId(204L).matchNumber(1)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(4).build();

        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                .thenReturn(List.of());
        // 入力順をシャッフルしても waitlist_number 順で処理されることを確認
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of(w3, w1, w4, w2));
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.WON))
                .thenReturn(18L);
        when(practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(100L, 1, ParticipantStatus.OFFERED))
                .thenReturn(0L);
        // renumberRemainingWaitlist 用：OFFERED→#1,#2、残WAITLISTED→#3,#4 の並び
        when(practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                        eq(100L), eq(1), eq(List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED))))
                .thenReturn(List.of(w1, w2, w3, w4));

        service.promoteWaitlistedAfterCapacityIncrease(100L);

        assertThat(w1.getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        assertThat(w2.getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        assertThat(w3.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(w4.getStatus()).isEqualTo(ParticipantStatus.WAITLISTED);
        // waitlist_number は再採番で 1..N に保たれる
        assertThat(w1.getWaitlistNumber()).isEqualTo(1);
        assertThat(w2.getWaitlistNumber()).isEqualTo(2);
        assertThat(w3.getWaitlistNumber()).isEqualTo(3);
        assertThat(w4.getWaitlistNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("promoteWaitlistedAfterCapacityIncrease: 既存OFFEREDの offer_deadline をクリア")
    void promoteOnExpand_clearsExistingOfferDeadline() {
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 5, 1)).capacity(24).build();
        PracticeParticipant existingOffered = PracticeParticipant.builder()
                .id(20L).sessionId(100L).playerId(301L).matchNumber(1)
                .status(ParticipantStatus.OFFERED).waitlistNumber(1)
                .offerDeadline(java.time.LocalDateTime.of(2026, 5, 1, 12, 0))
                .build();

        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                .thenReturn(List.of(existingOffered));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of());

        service.promoteWaitlistedAfterCapacityIncrease(100L);

        assertThat(existingOffered.getOfferDeadline()).isNull();
        assertThat(existingOffered.isDirty()).isTrue();
    }

    @Test
    @DisplayName("promoteWaitlistedAfterCapacityIncrease: WAITLISTED 0件なら何もしない")
    void promoteOnExpand_noWaitlistedNoop() {
        PracticeSession session = PracticeSession.builder()
                .id(100L).sessionDate(LocalDate.of(2026, 5, 1)).capacity(24).build();

        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.OFFERED))
                .thenReturn(List.of());
        when(practiceParticipantRepository.findBySessionIdAndStatus(100L, ParticipantStatus.WAITLISTED))
                .thenReturn(List.of());

        service.promoteWaitlistedAfterCapacityIncrease(100L);

        // count 系は呼ばれない
        verify(practiceParticipantRepository, never())
                .countBySessionIdAndMatchNumberAndStatus(eq(100L), org.mockito.ArgumentMatchers.anyInt(), eq(ParticipantStatus.WON));
    }
}