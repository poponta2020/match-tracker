package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * sendDensukeScheduleSyncFailedNotification の管理者解決ルールが
 * 既存の {@code getAdminRecipientsForSession} と同じであることを検証する単体テスト
 * （Codex Round 4 WARNING 対応）。
 *
 * <p>解決ルール: 全 {@code SUPER_ADMIN} + {@code admin_organization_id} が該当団体の {@code ADMIN}。
 * 旧実装の {@code player_organizations} 経由（メンバー所属）からの抽出は誤りで、
 * SUPER_ADMIN が全団体の player_organizations に所属している保証がないため通知漏れが発生していた。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineNotificationService.sendDensukeScheduleSyncFailedNotification 管理者解決ルール")
class LineNotificationServiceDensukePushFailedTest {

    @Mock private LineChannelRepository lineChannelRepository;
    @Mock private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @Mock private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    @Mock private LineMessageLogService lineMessageLogService;
    @Mock private LineMessagingService lineMessagingService;
    @Mock private PracticeSessionRepository practiceSessionRepository;
    @Mock private PracticeParticipantRepository practiceParticipantRepository;
    @Mock private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private LotteryQueryService lotteryQueryService;
    @Mock private VenueRepository venueRepository;
    @Mock private MentorRelationshipRepository mentorRelationshipRepository;

    private LineNotificationService service;

    private static final Long TARGET_ORG_ID = 1L;
    private static final Long OTHER_ORG_ID = 2L;

    @BeforeEach
    void setUp() {
        service = new LineNotificationService(
                lineChannelRepository,
                lineChannelAssignmentRepository,
                lineNotificationPreferenceRepository,
                lineMessageLogService,
                lineMessagingService,
                practiceSessionRepository,
                practiceParticipantRepository,
                playerOrganizationRepository,
                playerRepository,
                lotteryQueryService,
                venueRepository,
                mentorRelationshipRepository
        );
    }

    private Player buildPlayer(Long id, Player.Role role) {
        return Player.builder()
                .id(id)
                .name("user-" + id)
                .password("dummy")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(role)
                .build();
    }

    @Test
    @DisplayName("該当団体 ADMIN と全 SUPER_ADMIN の両方が対象になる（getAdminRecipientsForSession と同じ解決ルール）")
    void resolvesTargetOrgAdminAndAllSuperAdmin() {
        Player superAdmin = buildPlayer(100L, Player.Role.SUPER_ADMIN);
        Player targetOrgAdmin = buildPlayer(200L, Player.Role.ADMIN);
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN))
                .thenReturn(List.of(superAdmin));
        when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, TARGET_ORG_ID))
                .thenReturn(List.of(targetOrgAdmin));

        service.sendDensukeScheduleSyncFailedNotification(TARGET_ORG_ID, "test error");

        // SUPER_ADMIN は role ベースで全件取得
        verify(playerRepository).findByRoleAndActive(Player.Role.SUPER_ADMIN);
        // ADMIN は admin_organization_id ベースで該当団体のみ取得
        verify(playerRepository).findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, TARGET_ORG_ID);
        // player_organizations 経由（旧実装の誤り）は使わない
        verify(playerOrganizationRepository, never()).findByOrganizationId(any());
    }

    @Test
    @DisplayName("SUPER_ADMIN は player_organizations に所属していなくても通知対象になる（role ベース解決の必須要件）")
    void superAdminIncludedWithoutOrgMembership() {
        Player superAdmin = buildPlayer(100L, Player.Role.SUPER_ADMIN);
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN))
                .thenReturn(List.of(superAdmin));
        when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, TARGET_ORG_ID))
                .thenReturn(List.of()); // 該当団体に ADMIN 無し

        service.sendDensukeScheduleSyncFailedNotification(TARGET_ORG_ID, "test error");

        // SUPER_ADMIN は player_organizations を一切参照せずに取得される
        verify(playerRepository).findByRoleAndActive(Player.Role.SUPER_ADMIN);
        verify(playerOrganizationRepository, never()).findByOrganizationId(any());
        // 旧実装ではここで player_organizations 経由になり、未所属の SUPER_ADMIN が漏れていた
    }

    @Test
    @DisplayName("別団体 ADMIN は対象外（findByRoleAndAdminOrganizationIdAndActive が orgId スコープで除外）")
    void otherOrgAdminExcluded() {
        Player targetOrgAdmin = buildPlayer(200L, Player.Role.ADMIN);
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN))
                .thenReturn(List.of());
        when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, TARGET_ORG_ID))
                .thenReturn(List.of(targetOrgAdmin));

        service.sendDensukeScheduleSyncFailedNotification(TARGET_ORG_ID, "test error");

        // TARGET_ORG_ID のクエリのみ発行され、別 org のクエリは発行されない
        verify(playerRepository).findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, TARGET_ORG_ID);
        verify(playerRepository, never())
                .findByRoleAndAdminOrganizationIdAndActive(eq(Player.Role.ADMIN), eq(OTHER_ORG_ID));
    }
}
