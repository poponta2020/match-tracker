package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.entity.Player.Role;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryController same-day join tests")
class LotteryControllerSameDayJoinTest {

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

    @Test
    @DisplayName("PLAYER cannot register as another player")
    void sameDayJoin_playerCannotImpersonate() {
        Map<String, Object> body = Map.of(
                "sessionId", 100L,
                "matchNumber", 1,
                "playerId", 20L
        );

        when(request.getAttribute("currentUserId")).thenReturn(10L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());

        assertThatThrownBy(() -> controller.sameDayJoin(body, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("参加登録することはできません");
    }

    @Test
    @DisplayName("PLAYER can register self")
    void sameDayJoin_playerCanRegisterSelf() {
        Map<String, Object> body = Map.of(
                "sessionId", 100L,
                "matchNumber", 1,
                "playerId", 10L
        );

        when(request.getAttribute("currentUserId")).thenReturn(10L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());

        ResponseEntity<?> response = controller.sameDayJoin(body, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("status")).isEqualTo("WON");
        verify(waitlistPromotionService).handleSameDayJoin(100L, 1, 10L);
    }

    @Test
    @DisplayName("ADMIN scope is validated by session organization")
    void sameDayJoin_adminScopeValidated() {
        Map<String, Object> body = Map.of(
                "sessionId", 100L,
                "matchNumber", 2,
                "playerId", 20L
        );
        PracticeSession session = PracticeSession.builder()
                .id(100L)
                .organizationId(7L)
                .build();

        when(request.getAttribute("currentUserId")).thenReturn(1L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(7L);
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        ResponseEntity<?> response = controller.sameDayJoin(body, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(practiceSessionRepository).findById(100L);
        verify(waitlistPromotionService).handleSameDayJoin(100L, 2, 20L);
    }

    @Test
    @DisplayName("ADMIN cannot register out-of-scope session")
    void sameDayJoin_adminOutOfScopeForbidden() {
        Map<String, Object> body = Map.of(
                "sessionId", 100L,
                "matchNumber", 2,
                "playerId", 20L
        );
        PracticeSession session = PracticeSession.builder()
                .id(100L)
                .organizationId(8L)
                .build();

        when(request.getAttribute("currentUserId")).thenReturn(1L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(7L);
        when(practiceSessionRepository.findById(100L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> controller.sameDayJoin(body, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("IllegalStateException from service returns 409")
    void sameDayJoin_serviceConflictReturns409() {
        Map<String, Object> body = Map.of(
                "sessionId", 100L,
                "matchNumber", 1,
                "playerId", 10L
        );

        when(request.getAttribute("currentUserId")).thenReturn(10L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());
        doThrow(new IllegalStateException("already full"))
                .when(waitlistPromotionService).handleSameDayJoin(100L, 1, 10L);

        ResponseEntity<?> response = controller.sameDayJoin(body, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = (Map<String, Object>) response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.get("message")).isEqualTo("already full");
    }
}
