package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.LotteryResultDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PlayerOrganization;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GET /api/lottery/results/{sessionId} の組織スコープ認可テスト。
 *
 * 一覧側 API（/results, /my-results, /executions）は organizationId 解決を経由するが、
 * セッション単位 API は sessionId 直接指定のため別経路で他団体迂回が起きないかを保証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryController#getSessionLotteryResult tests")
class LotteryControllerSessionResultTest {

    @Mock private LotteryService lotteryService;
    @Mock private WaitlistPromotionService waitlistPromotionService;
    @Mock private LineNotificationService lineNotificationService;
    @Mock private NotificationService notificationService;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private LotteryExecutionRepository lotteryExecutionRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private VenueRepository venueRepository;
    @Mock private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock private LotteryDeadlineHelper lotteryDeadlineHelper;

    @InjectMocks
    private LotteryController controller;

    @Mock
    private HttpServletRequest request;

    private static final Long SESSION_ID = 500L;
    private static final Long SESSION_ORG_ID = 10L;
    private static final Long OTHER_ORG_ID = 20L;

    private PracticeSession session() {
        return PracticeSession.builder()
                .id(SESSION_ID)
                .organizationId(SESSION_ORG_ID)
                .build();
    }

    @Test
    @DisplayName("SUPER_ADMIN は他団体のセッションでも閲覧できる")
    void superAdminCanReadAnySession() {
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());
        when(practiceSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        LotteryResultDto dto = LotteryResultDto.builder().sessionId(SESSION_ID).build();
        when(lotteryService.buildLotteryResult(any(PracticeSession.class))).thenReturn(dto);

        ResponseEntity<LotteryResultDto> response = controller.getSessionLotteryResult(SESSION_ID, request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    @DisplayName("ADMIN は自団体のセッションを閲覧できる")
    void adminCanReadOwnOrganizationSession() {
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(SESSION_ORG_ID);
        when(practiceSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        LotteryResultDto dto = LotteryResultDto.builder().sessionId(SESSION_ID).build();
        when(lotteryService.buildLotteryResult(any(PracticeSession.class))).thenReturn(dto);

        ResponseEntity<LotteryResultDto> response = controller.getSessionLotteryResult(SESSION_ID, request);

        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    @DisplayName("ADMIN は他団体のセッションを閲覧すると ForbiddenException")
    void adminCannotReadOtherOrganizationSession() {
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(OTHER_ORG_ID);
        when(practiceSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));

        assertThatThrownBy(() -> controller.getSessionLotteryResult(SESSION_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("他団体");

        verify(lotteryService, never()).buildLotteryResult(any(PracticeSession.class));
    }

    @Test
    @DisplayName("PLAYER は所属団体のセッションを閲覧できる")
    void playerCanReadAffiliatedOrganizationSession() {
        Long playerId = 30L;
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());
        when(request.getAttribute("currentUserId")).thenReturn(playerId);
        when(practiceSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(playerOrganizationRepository.findByPlayerId(playerId))
                .thenReturn(List.of(PlayerOrganization.builder()
                        .playerId(playerId).organizationId(SESSION_ORG_ID).build()));
        LotteryResultDto dto = LotteryResultDto.builder().sessionId(SESSION_ID).build();
        when(lotteryService.buildLotteryResult(any(PracticeSession.class))).thenReturn(dto);

        ResponseEntity<LotteryResultDto> response = controller.getSessionLotteryResult(SESSION_ID, request);

        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    @DisplayName("PLAYER は所属していない団体のセッションを閲覧すると ForbiddenException")
    void playerCannotReadUnaffiliatedOrganizationSession() {
        Long playerId = 30L;
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());
        when(request.getAttribute("currentUserId")).thenReturn(playerId);
        when(practiceSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(playerOrganizationRepository.findByPlayerId(playerId))
                .thenReturn(List.of(PlayerOrganization.builder()
                        .playerId(playerId).organizationId(OTHER_ORG_ID).build()));

        assertThatThrownBy(() -> controller.getSessionLotteryResult(SESSION_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("所属していない団体");

        verify(lotteryService, never()).buildLotteryResult(any(PracticeSession.class));
    }

    @Test
    @DisplayName("PLAYER で所属団体が空の場合は ForbiddenException")
    void playerWithNoOrganizationsIsForbidden() {
        Long playerId = 30L;
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());
        when(request.getAttribute("currentUserId")).thenReturn(playerId);
        when(practiceSessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session()));
        when(playerOrganizationRepository.findByPlayerId(playerId)).thenReturn(List.of());

        assertThatThrownBy(() -> controller.getSessionLotteryResult(SESSION_ID, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("所属していない団体");

        verify(lotteryService, never()).buildLotteryResult(any(PracticeSession.class));
    }
}
