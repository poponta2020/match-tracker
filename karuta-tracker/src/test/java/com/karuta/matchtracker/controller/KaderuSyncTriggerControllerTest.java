package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.support.AuthTestSupport;
import com.karuta.matchtracker.entity.Player.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.KaderuSyncStatusResponse;
import com.karuta.matchtracker.dto.KaderuSyncTriggerEventDto;
import com.karuta.matchtracker.dto.KaderuSyncTriggerRequest;
import com.karuta.matchtracker.entity.KaderuSyncTriggerEvent.SyncStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.KaderuSyncTriggerService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import com.karuta.matchtracker.util.OrganizationScopeResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KaderuSyncTriggerController.class)
@DisplayName("KaderuSyncTriggerController 単体テスト")
class KaderuSyncTriggerControllerTest extends com.karuta.matchtracker.support.BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KaderuSyncTriggerService kaderuSyncTriggerService;

    @MockitoBean
    private OrganizationScopeResolver organizationScopeResolver;

    @MockitoBean
    private PlayerRepository playerRepository;

    @Test
    @DisplayName("POST /trigger: ADMIN が自団体を起動 → 201 + DTO")
    void trigger_admin_returns201() throws Exception {
        Player admin = Player.builder().id(7L).adminOrganizationId(1L).build();
        when(playerRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(null))).thenReturn(1L);
        when(kaderuSyncTriggerService.triggerSync(7L, 1L))
                .thenReturn(KaderuSyncTriggerEventDto.builder()
                        .id(100L).organizationId(1L).organizationCode("hokudai")
                        .triggeredByPlayerId(7L).triggeredAt(JstDateTimeUtil.now())
                        .status(SyncStatus.PENDING).build());

        KaderuSyncTriggerRequest body = new KaderuSyncTriggerRequest();
        mockMvc.perform(post("/api/kaderu-sync/trigger")
                        .header("Authorization", AuthTestSupport.bearer(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.organizationCode").value("hokudai"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /trigger: SUPER_ADMIN が organizationId 指定で起動 → 201")
    void trigger_superAdmin_withOrgId_returns201() throws Exception {
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(2L))).thenReturn(2L);
        when(kaderuSyncTriggerService.triggerSync(1L, 2L))
                .thenReturn(KaderuSyncTriggerEventDto.builder()
                        .id(101L).organizationId(2L).organizationCode("wasura")
                        .triggeredByPlayerId(1L).triggeredAt(JstDateTimeUtil.now())
                        .status(SyncStatus.PENDING).build());

        KaderuSyncTriggerRequest body = new KaderuSyncTriggerRequest();
        body.setOrganizationId(2L);
        mockMvc.perform(post("/api/kaderu-sync/trigger")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationCode").value("wasura"));
    }

    @Test
    @DisplayName("POST /trigger: SUPER_ADMIN が organizationId を省略 → 400 BadRequest")
    void trigger_superAdmin_withoutOrgId_returns400() throws Exception {
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(null))).thenReturn(null);

        KaderuSyncTriggerRequest body = new KaderuSyncTriggerRequest();
        mockMvc.perform(post("/api/kaderu-sync/trigger")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /trigger: ADMIN が他団体IDを渡す → 403 (resolver が ForbiddenException)")
    void trigger_admin_otherOrg_returns403() throws Exception {
        Player admin = Player.builder().id(7L).adminOrganizationId(1L).build();
        when(playerRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(2L)))
                .thenThrow(new ForbiddenException("他団体のリソースにはアクセスできません"));

        KaderuSyncTriggerRequest body = new KaderuSyncTriggerRequest();
        body.setOrganizationId(2L);
        mockMvc.perform(post("/api/kaderu-sync/trigger")
                        .header("Authorization", AuthTestSupport.bearer(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /trigger: 同一団体 PENDING 中 → 409")
    void trigger_duplicate_returns409() throws Exception {
        Player admin = Player.builder().id(7L).adminOrganizationId(1L).build();
        when(playerRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(null))).thenReturn(1L);
        when(kaderuSyncTriggerService.triggerSync(7L, 1L))
                .thenThrow(new DuplicateResourceException("同一団体の同期が既に実行中です"));

        KaderuSyncTriggerRequest body = new KaderuSyncTriggerRequest();
        mockMvc.perform(post("/api/kaderu-sync/trigger")
                        .header("Authorization", AuthTestSupport.bearer(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /trigger: GITHUB_PAT 未設定 → 503 (ResponseStatusException)")
    void trigger_patMissing_returns503() throws Exception {
        Player admin = Player.builder().id(7L).adminOrganizationId(1L).build();
        when(playerRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(null))).thenReturn(1L);
        when(kaderuSyncTriggerService.triggerSync(7L, 1L))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "GITHUB_PAT が未設定のため Kaderu 同期トリガーは利用できません"));

        KaderuSyncTriggerRequest body = new KaderuSyncTriggerRequest();
        mockMvc.perform(post("/api/kaderu-sync/trigger")
                        .header("Authorization", AuthTestSupport.bearer(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("POST /trigger: PLAYER → 403 (interceptor が ForbiddenException)")
    void trigger_player_returns403() throws Exception {
        KaderuSyncTriggerRequest body = new KaderuSyncTriggerRequest();
        mockMvc.perform(post("/api/kaderu-sync/trigger")
                        .header("Authorization", AuthTestSupport.bearer(7L, Role.PLAYER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /status: PENDING あり → 200 + pendingEvent")
    void getStatus_returnsPending() throws Exception {
        Player admin = Player.builder().id(7L).adminOrganizationId(1L).build();
        when(playerRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(null))).thenReturn(1L);
        when(kaderuSyncTriggerService.getStatus(1L)).thenReturn(KaderuSyncStatusResponse.builder()
                .pendingEvent(KaderuSyncTriggerEventDto.builder()
                        .id(100L).organizationId(1L).organizationCode("hokudai")
                        .triggeredByPlayerId(7L).triggeredAt(JstDateTimeUtil.now())
                        .status(SyncStatus.PENDING).elapsedSeconds(30L).build())
                .build());

        mockMvc.perform(get("/api/kaderu-sync/status")
                        .header("Authorization", AuthTestSupport.bearer(7L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingEvent.id").value(100))
                .andExpect(jsonPath("$.pendingEvent.organizationCode").value("hokudai"))
                .andExpect(jsonPath("$.pendingEvent.elapsedSeconds").value(30));
    }

    @Test
    @DisplayName("GET /status: PENDING なし → 200 + pendingEvent: null")
    void getStatus_returnsNullPending() throws Exception {
        Player admin = Player.builder().id(7L).adminOrganizationId(1L).build();
        when(playerRepository.findById(7L)).thenReturn(Optional.of(admin));
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(null))).thenReturn(1L);
        when(kaderuSyncTriggerService.getStatus(1L))
                .thenReturn(KaderuSyncStatusResponse.builder().pendingEvent(null).build());

        mockMvc.perform(get("/api/kaderu-sync/status")
                        .header("Authorization", AuthTestSupport.bearer(7L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingEvent").doesNotExist());
    }

    @Test
    @DisplayName("GET /status: SUPER_ADMIN が organizationId 未指定 → 200 + pendingEvent: null (DB参照なし)")
    void getStatus_superAdminWithoutOrg_returnsNullPending() throws Exception {
        when(organizationScopeResolver.resolveEffectiveOrganizationId(any(), eq(null))).thenReturn(null);

        mockMvc.perform(get("/api/kaderu-sync/status")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingEvent").doesNotExist());
    }
}
