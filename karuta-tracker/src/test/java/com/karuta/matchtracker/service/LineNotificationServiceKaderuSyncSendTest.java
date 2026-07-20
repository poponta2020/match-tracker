package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Kaderu予約取り込み通知の宛先拡大（{@code sendKaderuSyncCompletedNotification} /
 * {@code sendKaderuSyncFailedNotification}）の単体テスト。
 *
 * <p>宛先は「対象団体の ACTIVE な ADMIN 全員 ＋ ACTIVE な SUPER_ADMIN 全員（重複排除）」。
 * 実送信の {@code sendToPlayer} は spy でスタブ化し、受信者集合・本文・通知タイプ・
 * 例外分離を検証する（チャネル解決のリポジトリ配線には依存しない）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LineNotificationService Kaderu同期通知 宛先拡大")
class LineNotificationServiceKaderuSyncSendTest {

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

    private static final Long ORG = 1L;
    private static final String ORG_CODE = "hokudai";
    private static final Long TRIGGERED_BY = 7L;

    private static final String COMPLETED_MSG =
            "Kaderu予約取り込みが完了しました\n（団体: hokudai）\n結果: 新規 3件 / 拡張 1件 / スキップ 5件";
    private static final String FAILED_MSG =
            "Kaderu予約取り込みに失敗しました\n（団体: hokudai）\n理由: workflow failure（GitHub Actions ログを確認してください）";

    @BeforeEach
    void setUp() {
        service = spy(new LineNotificationService(
                lineChannelRepository, lineChannelAssignmentRepository, lineNotificationPreferenceRepository,
                lineMessageLogService, lineMessagingService, practiceSessionRepository,
                practiceParticipantRepository, playerOrganizationRepository, playerRepository,
                lotteryQueryService, venueRepository, mentorRelationshipRepository));
        // 実送信はスタブ化（受信者解決の検証に集中する）
        doReturn(LineNotificationService.SendResult.SKIPPED)
                .when(service).sendToPlayer(anyLong(), any(), any());
    }

    private Player player(Long id, Player.Role role) {
        return Player.builder()
                .id(id).name("p" + id).password("dummy")
                .gender(Player.Gender.男性).dominantHand(Player.DominantHand.右).role(role).build();
    }

    /** 対象団体の SUPER_ADMIN / ADMIN 収集 finder をモックする。 */
    private void recipients(List<Player> superAdmins, List<Player> orgAdmins) {
        when(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN)).thenReturn(superAdmins);
        when(playerRepository.findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, ORG))
                .thenReturn(orgAdmins);
    }

    @Test
    @DisplayName("AC-1/AC-3: 完了通知が対象団体ADMIN全員＋SUPER_ADMIN全員へ・本文とタイプ不変")
    void completed_sendsToAllAdminsAndSuperAdmins() {
        recipients(List.of(player(100L, Player.Role.SUPER_ADMIN), player(101L, Player.Role.SUPER_ADMIN)),
                List.of(player(200L, Player.Role.ADMIN), player(201L, Player.Role.ADMIN)));

        service.sendKaderuSyncCompletedNotification(
                TRIGGERED_BY, ORG, ORG_CODE, "新規 3件 / 拡張 1件 / スキップ 5件");

        for (long id : new long[]{100L, 101L, 200L, 201L}) {
            verify(service).sendToPlayer(id, LineNotificationType.ADMIN_KADERU_SYNC_COMPLETED, COMPLETED_MSG);
        }
        // 送信は解決集合の4名のみ（押下者本人への個別送信を上乗せしない＝二重送信しない）
        verify(service, times(4)).sendToPlayer(anyLong(), any(), any());
    }

    @Test
    @DisplayName("AC-2/AC-3: 失敗通知も同じ宛先集合へ・本文とタイプ不変")
    void failed_sendsToAllAdminsAndSuperAdmins() {
        recipients(List.of(player(100L, Player.Role.SUPER_ADMIN)),
                List.of(player(200L, Player.Role.ADMIN)));

        service.sendKaderuSyncFailedNotification(TRIGGERED_BY, ORG, ORG_CODE, "workflow failure");

        verify(service).sendToPlayer(100L, LineNotificationType.ADMIN_KADERU_SYNC_FAILED, FAILED_MSG);
        verify(service).sendToPlayer(200L, LineNotificationType.ADMIN_KADERU_SYNC_FAILED, FAILED_MSG);
        verify(service, times(2)).sendToPlayer(anyLong(), any(), any());
    }

    @Test
    @DisplayName("AC-4: 他団体ADMINには送られない（org ADMIN は対象orgスコープのみ・SUPER_ADMINは全員）")
    void doesNotSendToOtherOrgAdmins() {
        // 対象org(=1L)の ADMIN=200 のみ返す。他団体 ADMIN=999 は別orgスコープなので解決集合に入らない
        recipients(List.of(player(100L, Player.Role.SUPER_ADMIN)),
                List.of(player(200L, Player.Role.ADMIN)));

        service.sendKaderuSyncCompletedNotification(TRIGGERED_BY, ORG, ORG_CODE, "新規 3件 / 拡張 1件 / スキップ 5件");

        // org ADMIN の収集は対象org(=1L)スコープで呼ばれる
        verify(playerRepository).findByRoleAndAdminOrganizationIdAndActive(Player.Role.ADMIN, ORG);
        // 他団体 ADMIN(999) には送られない
        verify(service, never()).sendToPlayer(eq(999L), any(), any());
        // 送信は解決集合の2名のみ
        verify(service, times(2)).sendToPlayer(anyLong(), any(), any());
    }

    @Test
    @DisplayName("AC-1: 同一人物が両finderに現れても1回だけ送信（防御的 distinct）")
    void dedupesSameRecipient() {
        // 役割は本来排他（SUPER_ADMIN かつ ADMIN はあり得ない）だが、防御的 distinct を
        // 検証するため両finderに同一id=100を混在させる（人工的なケース）
        recipients(List.of(player(100L, Player.Role.SUPER_ADMIN)),
                List.of(player(100L, Player.Role.ADMIN), player(200L, Player.Role.ADMIN)));

        service.sendKaderuSyncCompletedNotification(TRIGGERED_BY, ORG, ORG_CODE, "新規 3件 / 拡張 1件 / スキップ 5件");

        verify(service, times(1)).sendToPlayer(eq(100L), any(), any());
        verify(service, times(2)).sendToPlayer(anyLong(), any(), any()); // 100 と 200 の2名
    }

    @Test
    @DisplayName("§6: 1人の送信失敗が他の受信者に波及しない（受信者ごとに例外分離）")
    void perRecipientFailureIsIsolated() {
        recipients(List.of(player(100L, Player.Role.SUPER_ADMIN)),
                List.of(player(200L, Player.Role.ADMIN)));
        // 受信者100への送信が例外 → 200へは送信継続する
        doThrow(new RuntimeException("boom")).when(service).sendToPlayer(eq(100L), any(), any());

        // メソッドは例外を飲み込む（呼び出しスレッドへ伝播しない）
        service.sendKaderuSyncCompletedNotification(TRIGGERED_BY, ORG, ORG_CODE, "新規 3件 / 拡張 1件 / スキップ 5件");

        verify(service).sendToPlayer(eq(100L), any(), any());
        verify(service).sendToPlayer(eq(200L), any(), any());
    }

    @Test
    @DisplayName("§6: 受信者0件でも例外にせず no-op")
    void noRecipients_noOp() {
        recipients(List.of(), List.of());

        service.sendKaderuSyncCompletedNotification(TRIGGERED_BY, ORG, ORG_CODE, "新規 3件 / 拡張 1件 / スキップ 5件");

        verify(service, never()).sendToPlayer(anyLong(), any(), any());
    }
}
