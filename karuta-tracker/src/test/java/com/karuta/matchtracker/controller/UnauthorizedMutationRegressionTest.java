package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.ByeActivityDto;
import com.karuta.matchtracker.dto.ByeActivityUpdateRequest;
import com.karuta.matchtracker.dto.LineNotificationPreferenceDto;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.repository.ByeActivityRepository;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.ByeActivityService;
import com.karuta.matchtracker.service.LineChannelService;
import com.karuta.matchtracker.service.LineLinkingService;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.MatchService;
import com.karuta.matchtracker.service.OrganizationService;
import com.karuta.matchtracker.service.PlayerProfileService;
import com.karuta.matchtracker.service.PlayerService;
import com.karuta.matchtracker.service.VenueService;
import com.karuta.matchtracker.support.AuthTestSupport;
import com.karuta.matchtracker.support.BaseControllerTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @RequireRole 欠落により未認証で叩けた状態変更エンドポイントの回帰テスト（Issue #1105）
 *
 * RoleCheckInterceptor は @RequireRole が無いハンドラを素通りさせる（fail-open）。
 * Spring Security も導入されていないため、注釈の付け忘れがそのまま未認証公開になっていた。
 *
 * ＜アサーションの方針＞
 * ステータス 403 だけでなく「サービスが呼ばれていないこと」も必ず検証する。
 * 認可はハンドラに到達する前に落ちなければ意味がなく、ステータスだけでは
 * 「処理された上でエラーになった」ケースと区別できないため。
 */
@WebMvcTest({
        PlayerController.class,
        MatchController.class,
        LineUserController.class,
        VenueController.class,
        PlayerProfileController.class,
        ByeActivityController.class,
})
@DisplayName("未認証で叩ける状態変更エンドポイントの回帰テスト（Issue #1105）")
class UnauthorizedMutationRegressionTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private PlayerService playerService;
    @MockitoBean private MatchService matchService;
    @MockitoBean private LineChannelService lineChannelService;
    @MockitoBean private LineLinkingService lineLinkingService;
    @MockitoBean private LineNotificationService lineNotificationService;
    @MockitoBean private LineChannelAssignmentRepository lineChannelAssignmentRepository;
    @MockitoBean private LineChannelRepository lineChannelRepository;
    @MockitoBean private VenueService venueService;
    @MockitoBean private PlayerProfileService playerProfileService;
    @MockitoBean private ByeActivityService byeActivityService;
    @MockitoBean private ByeActivityRepository byeActivityRepository;
    @MockitoBean private PracticeSessionRepository practiceSessionRepository;
    @MockitoBean private OrganizationService organizationService;
    @MockitoBean private PlayerRepository playerRepository;

    /** 認証ヘッダーを持つ本人（PLAYER） */
    private static final long SELF_ID = 5L;
    /** 他人 */
    private static final long OTHER_ID = 9L;

    // ---------------------------------------------------------------
    // AC-1: 認証ヘッダー無しはすべて 403（ハンドラに到達しない）
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("AC-1: 認証ヘッダー無しのリクエストは 403 で拒否される")
    class WithoutAuthenticationHeaders {

        @Test
        @DisplayName("PUT /api/players/{id} は 403（任意アカウントのパスワード変更＝SUPER_ADMIN 奪取の経路）")
        void updatePlayerIsRejected() throws Exception {
            PlayerUpdateRequest request = PlayerUpdateRequest.builder()
                    .password("hacked123")
                    .build();

            mockMvc.perform(json(put("/api/players/1"), request))
                    .andExpect(status().isUnauthorized());

            verify(playerService, never()).updatePlayer(anyLong(), any());
        }

        @Test
        @DisplayName("DELETE /api/matches/{id} は 403（試合記録の物理削除）")
        void deleteMatchIsRejected() throws Exception {
            mockMvc.perform(delete("/api/matches/1"))
                    .andExpect(status().isUnauthorized());

            verify(matchService, never()).deleteMatch(anyLong(), any(), any());
        }

        @Test
        @DisplayName("POST /api/line/{channelType}/enable は 403（他人の LINE 連携コード窃取）")
        void enableLineIsRejected() throws Exception {
            mockMvc.perform(json(post("/api/line/PLAYER/enable"), playerIdBody(OTHER_ID)))
                    .andExpect(status().isUnauthorized());

            verify(lineChannelService, never()).assignChannel(anyLong(), any());
            verify(lineLinkingService, never()).issueCode(anyLong(), anyLong());
        }

        @Test
        @DisplayName("POST /api/line/{channelType}/reissue-code は 403（連携コードの再発行・窃取）")
        void reissueLineCodeIsRejected() throws Exception {
            mockMvc.perform(json(post("/api/line/PLAYER/reissue-code"), playerIdBody(OTHER_ID)))
                    .andExpect(status().isUnauthorized());

            verify(lineLinkingService, never()).reissueCode(anyLong(), any());
        }

        @Test
        @DisplayName("DELETE /api/line/{channelType}/disable は 403（他人の通知を停止）")
        void disableLineIsRejected() throws Exception {
            mockMvc.perform(json(delete("/api/line/PLAYER/disable"), playerIdBody(OTHER_ID)))
                    .andExpect(status().isUnauthorized());

            verify(lineChannelService, never()).releaseChannel(anyLong(), any());
        }

        @Test
        @DisplayName("PUT /api/line/preferences は 403（他人の通知設定を改変）")
        void updateLinePreferencesIsRejected() throws Exception {
            mockMvc.perform(json(put("/api/line/preferences"), preferenceOf(OTHER_ID)))
                    .andExpect(status().isUnauthorized());

            verify(lineNotificationService, never()).updatePreferences(any());
        }

        @Test
        @DisplayName("POST /api/venues は 403")
        void createVenueIsRejected() throws Exception {
            mockMvc.perform(json(post("/api/venues"), "{\"name\":\"不正な会場\"}"))
                    .andExpect(status().isUnauthorized());

            verify(venueService, never()).createVenue(any());
        }

        @Test
        @DisplayName("PUT /api/venues/{id} は 403")
        void updateVenueIsRejected() throws Exception {
            mockMvc.perform(json(put("/api/venues/1"), "{\"name\":\"改変された会場\"}"))
                    .andExpect(status().isUnauthorized());

            verify(venueService, never()).updateVenue(anyLong(), any());
        }

        @Test
        @DisplayName("DELETE /api/venues/{id} は 403")
        void deleteVenueIsRejected() throws Exception {
            mockMvc.perform(delete("/api/venues/1"))
                    .andExpect(status().isUnauthorized());

            verify(venueService, never()).deleteVenue(anyLong());
        }

        @Test
        @DisplayName("POST /api/player-profiles は 403")
        void createProfileIsRejected() throws Exception {
            mockMvc.perform(json(post("/api/player-profiles"), "{\"playerId\":1}"))
                    .andExpect(status().isUnauthorized());

            verify(playerProfileService, never()).createProfile(any());
        }

        @Test
        @DisplayName("PUT /api/player-profiles/{id}/valid-to は 403")
        void setValidToIsRejected() throws Exception {
            mockMvc.perform(put("/api/player-profiles/1/valid-to"))
                    .andExpect(status().isUnauthorized());

            verify(playerProfileService, never()).setValidTo(anyLong(), any());
        }

        @Test
        @DisplayName("DELETE /api/player-profiles/{id} は 403")
        void deleteProfileIsRejected() throws Exception {
            mockMvc.perform(delete("/api/player-profiles/1"))
                    .andExpect(status().isUnauthorized());

            verify(playerProfileService, never()).deleteProfile(anyLong());
        }

        @Test
        @DisplayName("PUT /api/bye-activities/{id} は 403")
        void updateByeActivityIsRejected() throws Exception {
            mockMvc.perform(json(put("/api/bye-activities/1"), updateByeActivityRequest()))
                    .andExpect(status().isUnauthorized());

            verify(byeActivityService, never()).update(anyLong(), any(), any());
        }
    }

    // ---------------------------------------------------------------
    // AC-3: PUT /api/players/{id} は本人 or SUPER_ADMIN のみ
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("AC-3: PUT /api/players/{id} の本人性チェック")
    class UpdatePlayerOwnership {

        @Test
        @DisplayName("PLAYER が他人を更新しようとすると 403（パスワード奪取を防ぐ）")
        void playerCannotUpdateOtherPlayer() throws Exception {
            PlayerUpdateRequest request = PlayerUpdateRequest.builder()
                    .password("hacked123")
                    .build();

            mockMvc.perform(json(put("/api/players/" + OTHER_ID), request)
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isForbidden());

            verify(playerService, never()).updatePlayer(anyLong(), any());
        }

        @Test
        @DisplayName("PLAYER は自分自身を更新できる（ProfileEdit 経路の維持）")
        void playerCanUpdateSelf() throws Exception {
            when(playerService.updatePlayer(eq(SELF_ID), any())).thenReturn(new PlayerDto());

            mockMvc.perform(json(put("/api/players/" + SELF_ID), PlayerUpdateRequest.builder().build())
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isOk());

            verify(playerService).updatePlayer(eq(SELF_ID), any());
        }

        @Test
        @DisplayName("SUPER_ADMIN は他人も更新できる（PlayerEdit 経路の維持）")
        void superAdminCanUpdateOtherPlayer() throws Exception {
            when(playerService.updatePlayer(eq(OTHER_ID), any())).thenReturn(new PlayerDto());

            mockMvc.perform(json(put("/api/players/" + OTHER_ID), PlayerUpdateRequest.builder().build())
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.SUPER_ADMIN)))
                    .andExpect(status().isOk());

            verify(playerService).updatePlayer(eq(OTHER_ID), any());
        }
    }

    // ---------------------------------------------------------------
    // AC-5: /api/line/** は本人 or SUPER_ADMIN のみ
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("AC-5: /api/line/** の本人性チェック")
    class LineUserOwnership {

        @Test
        @DisplayName("PLAYER が他人の playerId で enable すると 403（連携コードが返らない）")
        void playerCannotEnableForOtherPlayer() throws Exception {
            mockMvc.perform(json(post("/api/line/PLAYER/enable"), playerIdBody(OTHER_ID))
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isForbidden());

            verify(lineChannelService, never()).assignChannel(anyLong(), any());
            verify(lineLinkingService, never()).issueCode(anyLong(), anyLong());
        }

        @Test
        @DisplayName("PLAYER が他人の playerId で reissue-code すると 403")
        void playerCannotReissueCodeForOtherPlayer() throws Exception {
            mockMvc.perform(json(post("/api/line/PLAYER/reissue-code"), playerIdBody(OTHER_ID))
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isForbidden());

            verify(lineLinkingService, never()).reissueCode(anyLong(), any());
        }

        @Test
        @DisplayName("PLAYER が他人の playerId で disable すると 403")
        void playerCannotDisableForOtherPlayer() throws Exception {
            mockMvc.perform(json(delete("/api/line/PLAYER/disable"), playerIdBody(OTHER_ID))
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isForbidden());

            verify(lineChannelService, never()).releaseChannel(anyLong(), any());
        }

        @Test
        @DisplayName("PLAYER が他人の playerId で通知設定を更新すると 403")
        void playerCannotUpdateOtherPreferences() throws Exception {
            mockMvc.perform(json(put("/api/line/preferences"), preferenceOf(OTHER_ID))
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isForbidden());

            verify(lineNotificationService, never()).updatePreferences(any());
        }

        @Test
        @DisplayName("PLAYER は自分の通知設定を更新できる（NotificationSettings 経路の維持）")
        void playerCanUpdateOwnPreferences() throws Exception {
            mockMvc.perform(json(put("/api/line/preferences"), preferenceOf(SELF_ID))
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isOk());

            verify(lineNotificationService).updatePreferences(any());
        }
    }

    // ---------------------------------------------------------------
    // AC-6: PUT /api/bye-activities/{id} は本人 or ADMIN+ のみ
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("AC-6: PUT /api/bye-activities/{id} の本人性チェック")
    class ByeActivityOwnership {

        @Test
        @DisplayName("PLAYER が他人の記録を更新しようとすると 403")
        void playerCannotUpdateOthersActivity() throws Exception {
            when(byeActivityService.getPlayerIdForActivity(1L)).thenReturn(OTHER_ID);

            mockMvc.perform(json(put("/api/bye-activities/1"), updateByeActivityRequest())
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isForbidden());

            verify(byeActivityService, never()).update(anyLong(), any(), any());
        }

        @Test
        @DisplayName("PLAYER は自分の記録を更新できる")
        void playerCanUpdateOwnActivity() throws Exception {
            when(byeActivityService.getPlayerIdForActivity(1L)).thenReturn(SELF_ID);
            when(byeActivityService.update(eq(1L), any(), eq(SELF_ID))).thenReturn(new ByeActivityDto());

            mockMvc.perform(json(put("/api/bye-activities/1"), updateByeActivityRequest())
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isOk());

            verify(byeActivityService).update(eq(1L), any(), eq(SELF_ID));
        }

        @Test
        @DisplayName("ADMIN は他人の記録も更新できる（管理者による代理修正の維持）")
        void adminCanUpdateOthersActivity() throws Exception {
            when(byeActivityService.getPlayerIdForActivity(1L)).thenReturn(OTHER_ID);
            when(byeActivityService.update(eq(1L), any(), eq(SELF_ID))).thenReturn(new ByeActivityDto());

            mockMvc.perform(json(put("/api/bye-activities/1"), updateByeActivityRequest())
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.ADMIN)))
                    .andExpect(status().isOk());

            verify(byeActivityService).update(eq(1L), any(), eq(SELF_ID));
        }
    }

    // ---------------------------------------------------------------
    // AC-4: DELETE /api/matches/{id} は認証ユーザーをサービスへ伝搬する
    // （PLAYER の所有者判定そのものは MatchServiceTest 側で検証する）
    // ---------------------------------------------------------------

    @Nested
    @DisplayName("AC-4: DELETE /api/matches/{id} の認可")
    class DeleteMatchAuthorization {

        @Test
        @DisplayName("PLAYER の削除要求は currentUserId・ロールを添えてサービスへ渡る")
        void playerDeletePropagatesIdentity() throws Exception {
            mockMvc.perform(delete("/api/matches/1")
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.PLAYER)))
                    .andExpect(status().isNoContent());

            verify(matchService).deleteMatch(1L, SELF_ID, Player.Role.PLAYER);
        }

        @Test
        @DisplayName("ADMIN は削除できる")
        void adminCanDelete() throws Exception {
            mockMvc.perform(delete("/api/matches/1")
                            .header("Authorization", AuthTestSupport.bearer(SELF_ID, Role.ADMIN)))
                    .andExpect(status().isNoContent());

            verify(matchService).deleteMatch(1L, SELF_ID, Player.Role.ADMIN);
        }
    }

    // ---------------------------------------------------------------
    // ヘルパー
    // ---------------------------------------------------------------

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        String content = body instanceof String ? (String) body : objectMapper.writeValueAsString(body);
        return builder.contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private String playerIdBody(long playerId) {
        return "{\"playerId\":" + playerId + "}";
    }

    private LineNotificationPreferenceDto preferenceOf(long playerId) {
        LineNotificationPreferenceDto dto = new LineNotificationPreferenceDto();
        dto.setPlayerId(playerId);
        dto.setOrganizationId(1L);
        return dto;
    }

    private ByeActivityUpdateRequest updateByeActivityRequest() {
        ByeActivityUpdateRequest request = new ByeActivityUpdateRequest();
        request.setActivityType(ActivityType.OTHER);
        request.setFreeText("改変");
        return request;
    }
}
