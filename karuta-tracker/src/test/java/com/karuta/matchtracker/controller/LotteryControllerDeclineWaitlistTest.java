package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
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
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LotteryController キャンセル待ち辞退/復帰のADMINスコープ検証")
class LotteryControllerDeclineWaitlistTest {

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

    private static final Long SESSION_ID = 100L;
    private static final Long PLAYER_ID = 200L;
    private static final Long ADMIN_ORG_ID = 1L;
    private static final Long OTHER_ORG_ID = 2L;

    private Map<String, Long> body() {
        Map<String, Long> body = new HashMap<>();
        body.put("sessionId", SESSION_ID);
        body.put("playerId", PLAYER_ID);
        return body;
    }

    private PracticeSession sessionForOrg(Long orgId) {
        return PracticeSession.builder()
                .id(SESSION_ID)
                .organizationId(orgId)
                .build();
    }

    @Test
    @DisplayName("decline-waitlist: ADMINが他団体セッションを指定した場合 403")
    void declineWaitlist_adminCrossOrg_throwsForbidden() {
        when(request.getAttribute("currentUserId")).thenReturn(999L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(ADMIN_ORG_ID);
        when(practiceSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(sessionForOrg(OTHER_ORG_ID)));

        assertThatThrownBy(() -> controller.declineWaitlist(body(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("他団体のキャンセル待ちは操作できません");

        verify(waitlistPromotionService, never()).declineWaitlistBySession(any(), any());
    }

    @Test
    @DisplayName("decline-waitlist: ADMINが自団体セッションなら成功")
    void declineWaitlist_adminSameOrg_succeeds() {
        when(request.getAttribute("currentUserId")).thenReturn(999L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(ADMIN_ORG_ID);
        when(practiceSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(sessionForOrg(ADMIN_ORG_ID)));
        when(waitlistPromotionService.declineWaitlistBySession(SESSION_ID, PLAYER_ID)).thenReturn(2);

        ResponseEntity<Map<String, Object>> response = controller.declineWaitlist(body(), request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("declinedCount", 2);
        verify(waitlistPromotionService).declineWaitlistBySession(SESSION_ID, PLAYER_ID);
    }

    @Test
    @DisplayName("decline-waitlist: SUPER_ADMINは団体スコープ検証なしで成功（セッション取得不要）")
    void declineWaitlist_superAdmin_skipsScopeCheck() {
        when(request.getAttribute("currentUserId")).thenReturn(1L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.SUPER_ADMIN.name());
        when(waitlistPromotionService.declineWaitlistBySession(SESSION_ID, PLAYER_ID)).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = controller.declineWaitlist(body(), request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(practiceSessionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("decline-waitlist: PLAYERが自分以外を指定した場合 403（スコープ検証より前）")
    void declineWaitlist_playerOther_throwsForbidden() {
        when(request.getAttribute("currentUserId")).thenReturn(999L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());

        assertThatThrownBy(() -> controller.declineWaitlist(body(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("他の参加者のキャンセル待ちは辞退できません");

        verify(practiceSessionRepository, never()).findById(any());
    }

    @Test
    @DisplayName("decline-waitlist: ADMINが存在しないセッションを指定した場合 ResourceNotFound")
    void declineWaitlist_adminMissingSession_throwsNotFound() {
        when(request.getAttribute("currentUserId")).thenReturn(999L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(ADMIN_ORG_ID);
        when(practiceSessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.declineWaitlist(body(), request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(waitlistPromotionService, never()).declineWaitlistBySession(any(), any());
    }

    @Test
    @DisplayName("rejoin-waitlist: ADMINが他団体セッションを指定した場合 403")
    void rejoinWaitlist_adminCrossOrg_throwsForbidden() {
        when(request.getAttribute("currentUserId")).thenReturn(999L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(ADMIN_ORG_ID);
        when(practiceSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(sessionForOrg(OTHER_ORG_ID)));

        assertThatThrownBy(() -> controller.rejoinWaitlist(body(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("他団体のキャンセル待ちは操作できません");

        verify(waitlistPromotionService, never()).rejoinWaitlistBySession(any(), any());
    }

    @Test
    @DisplayName("rejoin-waitlist: ADMINが自団体セッションなら成功")
    void rejoinWaitlist_adminSameOrg_succeeds() {
        when(request.getAttribute("currentUserId")).thenReturn(999L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.ADMIN.name());
        when(request.getAttribute("adminOrganizationId")).thenReturn(ADMIN_ORG_ID);
        when(practiceSessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(sessionForOrg(ADMIN_ORG_ID)));
        when(waitlistPromotionService.rejoinWaitlistBySession(SESSION_ID, PLAYER_ID)).thenReturn(1);

        ResponseEntity<Map<String, Object>> response = controller.rejoinWaitlist(body(), request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).containsEntry("rejoinedCount", 1);
        verify(waitlistPromotionService).rejoinWaitlistBySession(SESSION_ID, PLAYER_ID);
    }

    @Test
    @DisplayName("rejoin-waitlist: PLAYERが自分以外を指定した場合 403")
    void rejoinWaitlist_playerOther_throwsForbidden() {
        when(request.getAttribute("currentUserId")).thenReturn(999L);
        when(request.getAttribute("currentUserRole")).thenReturn(Role.PLAYER.name());

        assertThatThrownBy(() -> controller.rejoinWaitlist(body(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("他の参加者のキャンセル待ちは復帰できません");

        verify(practiceSessionRepository, never()).findById(any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
