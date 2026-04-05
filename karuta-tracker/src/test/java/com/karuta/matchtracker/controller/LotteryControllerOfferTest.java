package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.OfferBatchResponseRequest;
import com.karuta.matchtracker.dto.WaitlistStatusDto;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.LotteryDeadlineHelper;
import com.karuta.matchtracker.service.LotteryService;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryController オファー関連テスト")
class LotteryControllerOfferTest {

    @Mock private LotteryService lotteryService;
    @Mock private WaitlistPromotionService waitlistPromotionService;
    @Mock private LineNotificationService lineNotificationService;
    @Mock private NotificationService notificationService;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;

    @InjectMocks
    private LotteryController controller;

    @Mock
    private HttpServletRequest request;

    @Nested
    @DisplayName("POST /respond-offer-all")
    class RespondOfferAllTests {

        @Test
        @DisplayName("一括承諾が成功し、resultとcountが返却される")
        void respondOfferAll_accept_returnsCorrectResponse() {
            OfferBatchResponseRequest req = OfferBatchResponseRequest.builder()
                    .sessionId(100L).accept(true).build();

            when(request.getAttribute("currentUserId")).thenReturn(10L);
            when(waitlistPromotionService.respondToOfferAll(100L, 10L, true)).thenReturn(3);

            ResponseEntity<Map<String, Object>> response = controller.respondToOfferAll(req, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("result", "accepted");
            assertThat(response.getBody()).containsEntry("count", 3);
            verify(waitlistPromotionService).respondToOfferAll(100L, 10L, true);
            // LINE確認通知が送信される
            verify(lineNotificationService).sendBatchOfferResponseConfirmation(100L, 10L, true, 3);
        }

        @Test
        @DisplayName("一括辞退が成功し、resultがdeclinedで返却される")
        void respondOfferAll_decline_returnsDeclined() {
            OfferBatchResponseRequest req = OfferBatchResponseRequest.builder()
                    .sessionId(100L).accept(false).build();

            when(request.getAttribute("currentUserId")).thenReturn(10L);
            when(waitlistPromotionService.respondToOfferAll(100L, 10L, false)).thenReturn(2);

            ResponseEntity<Map<String, Object>> response = controller.respondToOfferAll(req, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("result", "declined");
            assertThat(response.getBody()).containsEntry("count", 2);
            // LINE確認通知が送信される
            verify(lineNotificationService).sendBatchOfferResponseConfirmation(100L, 10L, false, 2);
        }

        @Test
        @DisplayName("部分成功時のcountが実処理件数を反映する")
        void respondOfferAll_partialAccept_returnsActualCount() {
            OfferBatchResponseRequest req = OfferBatchResponseRequest.builder()
                    .sessionId(100L).accept(true).build();

            when(request.getAttribute("currentUserId")).thenReturn(10L);
            when(waitlistPromotionService.respondToOfferAll(100L, 10L, true)).thenReturn(1);

            ResponseEntity<Map<String, Object>> response = controller.respondToOfferAll(req, request);

            assertThat(response.getBody()).containsEntry("count", 1);
        }
    }

    @Nested
    @DisplayName("GET /session-offers/{sessionId}")
    class GetSessionOffersTests {

        @Test
        @DisplayName("自分のOFFEREDがmatchNumber順で返却される")
        void getSessionOffers_returnsOfferedSorted() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 5, 10)).build();
            PracticeParticipant p1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(3)
                    .status(ParticipantStatus.OFFERED)
                    .offerDeadline(LocalDateTime.of(2026, 5, 9, 18, 0)).build();
            PracticeParticipant p2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED)
                    .offerDeadline(LocalDateTime.of(2026, 5, 9, 18, 0)).build();

            when(request.getAttribute("currentUserId")).thenReturn(10L);
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of(p1, p2));

            ResponseEntity<List<WaitlistStatusDto.WaitlistEntry>> response =
                    controller.getSessionOffers(100L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getMatchNumber()).isEqualTo(1);
            assertThat(response.getBody().get(1).getMatchNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("OFFEREDがない場合は空リストが返却される")
        void getSessionOffers_empty() {
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(request.getAttribute("currentUserId")).thenReturn(10L);
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));
            when(practiceParticipantRepository
                    .findBySessionIdAndPlayerIdAndStatus(100L, 10L, ParticipantStatus.OFFERED))
                    .thenReturn(List.of());

            ResponseEntity<List<WaitlistStatusDto.WaitlistEntry>> response =
                    controller.getSessionOffers(100L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("GET /offer-detail/{participantId}")
    class GetOfferDetailTests {

        @Test
        @DisplayName("PLAYERは他人のオファー情報を参照できない")
        void getOfferDetail_playerCannotViewOthers() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED).build();

            when(request.getAttribute("currentUserId")).thenReturn(10L);
            when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());
            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));

            assertThatThrownBy(() -> controller.getOfferDetail(1L, request))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("他の参加者のオファー情報は参照できません");
        }

        @Test
        @DisplayName("PLAYERは自分のオファー情報を参照できる")
        void getOfferDetail_playerCanViewOwn() {
            PracticeParticipant participant = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.OFFERED)
                    .offerDeadline(LocalDateTime.of(2026, 5, 9, 18, 0)).build();
            PracticeSession session = PracticeSession.builder()
                    .id(100L).sessionDate(LocalDate.of(2026, 5, 10)).build();

            when(request.getAttribute("currentUserId")).thenReturn(10L);
            when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());
            when(practiceParticipantRepository.findById(1L)).thenReturn(Optional.of(participant));
            when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

            ResponseEntity<WaitlistStatusDto.WaitlistEntry> response =
                    controller.getOfferDetail(1L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getMatchNumber()).isEqualTo(1);
            assertThat(response.getBody().getStatus()).isEqualTo(ParticipantStatus.OFFERED);
        }
    }
}
