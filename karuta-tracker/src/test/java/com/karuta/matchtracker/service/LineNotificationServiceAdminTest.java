package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LineNotificationService 管理者通知テスト")
class LineNotificationServiceAdminTest {

    @Mock
    private LineChannelRepository lineChannelRepository;
    @Mock
    private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @Mock
    private LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    @Mock
    private LineMessageLogService lineMessageLogService;
    @Mock
    private LineMessagingService lineMessagingService;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private PlayerOrganizationRepository playerOrganizationRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private LotteryQueryService lotteryQueryService;
    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private LineNotificationService lineNotificationService;

    // ===== ヘルパーメソッド =====

    private PracticeSession buildSession(Long id, Long orgId) {
        return PracticeSession.builder()
                .id(id)
                .sessionDate(LocalDate.of(2026, 4, 5))
                .capacity(6)
                .totalMatches(2)
                .organizationId(orgId)
                .build();
    }

    private Player buildPlayer(Long id, String name, Player.Role role) {
        return Player.builder()
                .id(id)
                .name(name)
                .password("dummy")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(role)
                .build();
    }

    private PracticeParticipant buildWonParticipant(Long id, Long sessionId, Long playerId, int matchNumber) {
        return PracticeParticipant.builder()
                .id(id)
                .sessionId(sessionId)
                .playerId(playerId)
                .matchNumber(matchNumber)
                .status(ParticipantStatus.WON)
                .build();
    }

    // ===== テストグループ =====

    @Nested
    @DisplayName("sendSameDayCancelNotification - 管理者受信者")
    class SendSameDayCancelNotificationAdminRecipients {

        @Test
        @DisplayName("キャンセル通知で SUPER_ADMIN と該当団体 ADMIN の両方が管理者受信者として取得される")
        void adminRecipients_includeSuperAdminAndOrgAdmin() {
            PracticeSession session = buildSession(1L, 1L);
            Player superAdmin = buildPlayer(100L, "総管理者", Player.Role.SUPER_ADMIN);
            Player orgAdmin = buildPlayer(200L, "団体管理者", Player.Role.ADMIN);

            // WON参加者
            when(practiceParticipantRepository.findBySessionIdAndStatus(1L, ParticipantStatus.WON))
                    .thenReturn(List.of(
                            buildWonParticipant(1L, 1L, 10L, 1),
                            buildWonParticipant(2L, 1L, 20L, 1)
                    ));

            // 管理者受信者
            when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN))
                    .thenReturn(List.of(superAdmin));
            when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L))
                    .thenReturn(List.of(orgAdmin));

            lineNotificationService.sendSameDayCancelNotification(session, 1, "テスト選手", 10L);

            // SUPER_ADMIN と ADMIN の両方が管理者受信者として取得されることを検証
            verify(playerRepository).findByRoleAndActive(Player.Role.SUPER_ADMIN);
            verify(playerRepository).findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L);
        }
    }

    @Nested
    @DisplayName("sendSameDayConfirmationNotification - 管理者受信者")
    class SendSameDayConfirmationNotificationAdminRecipients {

        @Test
        @DisplayName("確定通知で WON の SUPER_ADMIN も管理者受信者として取得される（getAdminRecipientsForSession使用）")
        void adminRecipients_fetchedViaGetAdminRecipientsForSession() {
            PracticeSession session = buildSession(1L, 1L);
            Player superAdmin = buildPlayer(100L, "総管理者", Player.Role.SUPER_ADMIN);
            Player orgAdmin = buildPlayer(200L, "団体管理者", Player.Role.ADMIN);

            // WON参加者（SUPER_ADMINを含む）
            List<PracticeParticipant> wonParticipants = List.of(
                    buildWonParticipant(1L, 1L, 100L, 1),  // SUPER_ADMIN のplayerId
                    buildWonParticipant(2L, 1L, 10L, 1)
            );
            when(practiceParticipantRepository.findBySessionIdAndStatus(1L, ParticipantStatus.WON))
                    .thenReturn(wonParticipants);

            // プレイヤー情報一括取得
            when(playerRepository.findAllById(anyList()))
                    .thenReturn(List.of(superAdmin, buildPlayer(10L, "選手A", Player.Role.PLAYER)));

            // 管理者受信者
            when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN))
                    .thenReturn(List.of(superAdmin));
            when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L))
                    .thenReturn(List.of(orgAdmin));

            lineNotificationService.sendSameDayConfirmationNotification(session);

            // getAdminRecipientsForSession が使用されていることを検証
            verify(playerRepository).findByRoleAndActive(Player.Role.SUPER_ADMIN);
            verify(playerRepository).findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L);
        }
    }

    @Nested
    @DisplayName("isNotificationEnabled - ADMIN_ プレフィクスは orgId=0 で判定")
    class IsNotificationEnabledAdminPrefix {

        @Test
        @DisplayName("ADMIN_SAME_DAY_CANCEL の通知設定判定に orgId=0 のレコードが使用される")
        void adminNotificationType_usesOrgIdZero() {
            PracticeSession session = buildSession(1L, 1L);
            Player orgAdmin = buildPlayer(200L, "団体管理者", Player.Role.ADMIN);

            // WON参加者（空リスト: キャンセル通知の送信先は不要）
            when(practiceParticipantRepository.findBySessionIdAndStatus(1L, ParticipantStatus.WON))
                    .thenReturn(List.of());

            // 管理者受信者にorgAdminを返す
            when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN))
                    .thenReturn(List.of());
            when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, 1L))
                    .thenReturn(List.of(orgAdmin));

            lineNotificationService.sendSameDayCancelNotification(session, 1, "テスト選手", 10L);

            // ADMIN_SAME_DAY_CANCEL は orgId=0 のレコードで通知設定を判定する
            // resolveChannel -> isNotificationEnabled 経由で呼ばれる
            // lineChannelAssignmentRepository が呼ばれた後に isNotificationEnabled が呼ばれるため、
            // まず割り当てが見つからない場合は isNotificationEnabled まで到達しないが、
            // 割り当てがある場合に orgId=0 で判定されることを確認する
            //
            // ここでは resolveChannel でアサインメントが見つかった場合をセットアップ
            // → 既にsendSameDayCancelNotificationは呼ばれているので、再度呼ぶ

            // アサインメントを設定して再度テスト
            LineChannelAssignment assignment = LineChannelAssignment.builder()
                    .id(1L)
                    .playerId(200L)
                    .lineChannelId(10L)
                    .lineUserId("U_admin")
                    .channelType(ChannelType.ADMIN)
                    .status(LineChannelAssignment.AssignmentStatus.LINKED)
                    .build();
            when(lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                    eq(200L), eq(ChannelType.ADMIN), anyList()))
                    .thenReturn(java.util.Optional.of(assignment));

            // 通知設定: orgId=0 でadminSameDayCancel=false を返す
            LineNotificationPreference pref = LineNotificationPreference.builder()
                    .id(1L)
                    .playerId(200L)
                    .organizationId(0L)
                    .adminSameDayCancel(false)
                    .build();
            when(lineNotificationPreferenceRepository.findByPlayerIdAndOrganizationId(200L, 0L))
                    .thenReturn(java.util.Optional.of(pref));

            // 再度呼び出し
            lineNotificationService.sendSameDayCancelNotification(session, 1, "テスト選手2", 10L);

            // orgId=0 で通知設定が参照されたことを検証
            verify(lineNotificationPreferenceRepository).findByPlayerIdAndOrganizationId(200L, 0L);
        }
    }

    @Nested
    @DisplayName("sendSameDayVacancyNotification - 団体メンバーを使用")
    class SendSameDayVacancyNotificationOrgMembers {

        @Test
        @DisplayName("空き募集通知の送信先はセッション参加者ではなく団体メンバーから取得される")
        void vacancyRecipients_fromOrgMembers_notSessionParticipants() {
            PracticeSession session = buildSession(1L, 1L);

            // 現在のWON参加者（空き枠計算用）
            when(practiceParticipantRepository.findBySessionIdAndMatchNumberAndStatus(
                    1L, 1, ParticipantStatus.WON))
                    .thenReturn(List.of(
                            buildWonParticipant(1L, 1L, 10L, 1),
                            buildWonParticipant(2L, 1L, 20L, 1)
                    ));

            // 団体メンバー
            when(playerOrganizationRepository.findByOrganizationId(1L))
                    .thenReturn(List.of(
                            PlayerOrganization.builder().id(1L).playerId(10L).organizationId(1L).build(),
                            PlayerOrganization.builder().id(2L).playerId(20L).organizationId(1L).build(),
                            PlayerOrganization.builder().id(3L).playerId(30L).organizationId(1L).build(),
                            PlayerOrganization.builder().id(4L).playerId(40L).organizationId(1L).build()
                    ));

            lineNotificationService.sendSameDayVacancyNotification(session, 1, null);

            // playerOrganizationRepository.findByOrganizationId が呼ばれたことを検証
            verify(playerOrganizationRepository).findByOrganizationId(1L);

            // practiceParticipantRepository.findBySessionId は呼ばれないことを検証
            // （findBySessionIdAndStatus は WON数計算用に呼ばれるが、findBySessionId 単体は呼ばれない）
            verify(practiceParticipantRepository, never()).findBySessionIdAndStatus(eq(1L), eq(ParticipantStatus.WAITLISTED));
        }
    }
}
