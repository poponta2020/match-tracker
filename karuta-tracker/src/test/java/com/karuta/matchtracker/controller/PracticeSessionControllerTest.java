package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.support.AuthTestSupport;
import com.karuta.matchtracker.entity.Player.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PracticeParticipationRequest;
import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.PracticeSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PracticeSessionControllerのテスト
 */
@WebMvcTest(PracticeSessionController.class)
@Import(com.karuta.matchtracker.util.OrganizationScopeResolver.class)
@DisplayName("PracticeSessionController 単体テスト")
class PracticeSessionControllerTest extends com.karuta.matchtracker.support.BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PracticeSessionService practiceSessionService;

    @MockitoBean
    private com.karuta.matchtracker.service.PracticeParticipantService practiceParticipantService;

    @MockitoBean
    private com.karuta.matchtracker.service.DensukeImportService densukeImportService;

    @MockitoBean
    private com.karuta.matchtracker.repository.DensukeUrlRepository densukeUrlRepository;

    @MockitoBean
    private PlayerRepository playerRepository;

    @MockitoBean
    private com.karuta.matchtracker.service.OrganizationService organizationService;

    @MockitoBean
    private com.karuta.matchtracker.service.DensukeWriteService densukeWriteService;

    @MockitoBean
    private com.karuta.matchtracker.service.DensukeSyncService densukeSyncService;

    @MockitoBean
    private com.karuta.matchtracker.service.AdjacentRoomService adjacentRoomService;

    @MockitoBean
    private com.karuta.matchtracker.service.DensukePageCreateService densukePageCreateService;

    private PracticeSessionDto testSessionDto;
    private PracticeSessionCreateRequest createRequest;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        testSessionDto = PracticeSessionDto.builder()
                .id(1L)
                .sessionDate(today)
                .totalMatches(10)
                .build();

        createRequest = PracticeSessionCreateRequest.builder()
                .sessionDate(today)
                .totalMatches(12)
                .build();
    }

    @Test
    @DisplayName("GET /api/practice-sessions/{id} - IDで練習日を取得できる")
    void testGetSessionById() throws Exception {
        // Given
        when(practiceSessionService.findById(1L)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/1")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalMatches").value(10));

        verify(practiceSessionService).findById(1L);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/{id} - 存在しないIDは404を返す")
    void testGetSessionByIdNotFound() throws Exception {
        // Given
        when(practiceSessionService.findById(999L))
                .thenThrow(new ResourceNotFoundException("PracticeSession", 999L));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/999")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(practiceSessionService).findById(999L);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - PLAYER は organizationId=null で取得する（日付のみ検索）")
    void testGetSessionByDate() throws Exception {
        // Given: PLAYER は adminOrganizationId を持たないので、サービスには null が渡る
        when(practiceSessionService.findByDateWithParticipants(today, null)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString())
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));

        verify(practiceSessionService).findByDateWithParticipants(today, null);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - ADMIN は organizationId 未指定なら会員団体スコープ（非限定 null）で取得する")
    void testGetSessionByDateAdminUnscopedWhenNoOrg() throws Exception {
        // Given: ADMIN（admin_org=7L）。閲覧は resolveViewingOrganizationId により admin_org 強制せず、
        // organizationId 未指定なら null（非限定）でサービスに伝播する（他団体会員でもある ADMIN が
        // その会員団体のセッションを閲覧できるようにするため）。
        Long adminOrgId = 7L;
        Long adminUserId = 99L;
        com.karuta.matchtracker.entity.Player adminPlayer = new com.karuta.matchtracker.entity.Player();
        adminPlayer.setId(adminUserId);
        adminPlayer.setName("管理者");
        adminPlayer.setPassword("dummy");
        adminPlayer.setGender(com.karuta.matchtracker.entity.Player.Gender.その他);
        adminPlayer.setDominantHand(com.karuta.matchtracker.entity.Player.DominantHand.右);
        adminPlayer.setRole(com.karuta.matchtracker.entity.Player.Role.ADMIN);
        adminPlayer.setAdminOrganizationId(adminOrgId);
        when(playerRepository.findById(adminUserId)).thenReturn(java.util.Optional.of(adminPlayer));
        when(practiceSessionService.findByDateWithParticipants(today, null)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString())
                        .header("Authorization", AuthTestSupport.bearer(adminUserId, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));

        // サービスは null（非限定）で呼ばれる（admin_org=7L では呼ばれない）
        verify(practiceSessionService).findByDateWithParticipants(today, null);
        verify(practiceSessionService, org.mockito.Mockito.never())
                .findByDateWithParticipants(today, adminOrgId);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - SUPER_ADMIN は organizationId=null で取得する")
    void testGetSessionByDateForSuperAdmin() throws Exception {
        // Given: SUPER_ADMIN は adminOrganizationId を持たないため null が渡る
        when(practiceSessionService.findByDateWithParticipants(today, null)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString())
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));

        verify(practiceSessionService).findByDateWithParticipants(today, null);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - 認証トークンなしは 401")
    void testGetSessionByDateWithoutAuthIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString()))
                .andExpect(status().isUnauthorized());

        verify(practiceSessionService, org.mockito.Mockito.never())
                .findByDateWithParticipants(any(), any());
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - ADMIN が他団体IDを指定すると 403")
    void testGetSessionByDateAdminMismatchOrgIsForbidden() throws Exception {
        // Given: ADMIN (adminOrganizationId=7L) が他団体 99L を指定
        Long adminOrgId = 7L;
        Long adminUserId = 99L;
        com.karuta.matchtracker.entity.Player adminPlayer = new com.karuta.matchtracker.entity.Player();
        adminPlayer.setId(adminUserId);
        adminPlayer.setName("管理者");
        adminPlayer.setPassword("dummy");
        adminPlayer.setGender(com.karuta.matchtracker.entity.Player.Gender.その他);
        adminPlayer.setDominantHand(com.karuta.matchtracker.entity.Player.DominantHand.右);
        adminPlayer.setRole(com.karuta.matchtracker.entity.Player.Role.ADMIN);
        adminPlayer.setAdminOrganizationId(adminOrgId);
        when(playerRepository.findById(adminUserId)).thenReturn(java.util.Optional.of(adminPlayer));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString())
                        .param("organizationId", "99")
                        .header("Authorization", AuthTestSupport.bearer(adminUserId, Role.ADMIN)))
                .andExpect(status().isForbidden());

        verify(practiceSessionService, org.mockito.Mockito.never())
                .findByDateWithParticipants(any(), any());
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - PLAYER が所属団体IDを指定すると組織スコープで取得する")
    void testGetSessionByDatePlayerWithBelongingOrgIsScoped() throws Exception {
        // Given
        Long playerUserId = 10L;
        Long orgId = 7L;
        when(organizationService.getPlayerOrganizationIds(playerUserId))
                .thenReturn(List.of(orgId, 8L));
        when(practiceSessionService.findByDateWithParticipants(today, orgId)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString())
                        .param("organizationId", orgId.toString())
                        .header("Authorization", AuthTestSupport.bearer(playerUserId, Role.PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(practiceSessionService).findByDateWithParticipants(today, orgId);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - PLAYER が所属外の団体IDを指定すると 403")
    void testGetSessionByDatePlayerWithNonBelongingOrgIsForbidden() throws Exception {
        // Given
        Long playerUserId = 10L;
        when(organizationService.getPlayerOrganizationIds(playerUserId))
                .thenReturn(List.of(7L));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString())
                        .param("organizationId", "99")
                        .header("Authorization", AuthTestSupport.bearer(playerUserId, Role.PLAYER)))
                .andExpect(status().isForbidden());

        verify(practiceSessionService, org.mockito.Mockito.never())
                .findByDateWithParticipants(any(), any());
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - SUPER_ADMIN が organizationId を指定すると組織スコープで取得する")
    void testGetSessionByDateSuperAdminWithExplicitOrgIsScoped() throws Exception {
        // Given
        Long orgId = 7L;
        when(practiceSessionService.findByDateWithParticipants(today, orgId)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString())
                        .param("organizationId", orgId.toString())
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(practiceSessionService).findByDateWithParticipants(today, orgId);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/year-month - 年月別で練習日を取得できる")
    void testGetSessionsByYearMonth() throws Exception {
        // Given
        int year = today.getYear();
        int month = today.getMonthValue();
        when(practiceSessionService.findSessionsByYearMonthAndPlayer(year, month, 1L))
                .thenReturn(List.of(testSessionDto));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/year-month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month))
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(practiceSessionService).findSessionsByYearMonthAndPlayer(year, month, 1L);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/exists - 練習日の存在確認ができる")
    void testExistsSessionOnDate() throws Exception {
        // Given
        when(practiceSessionService.existsSessionOnDate(today)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/exists")
                        .param("date", today.toString())
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("true"));

        verify(practiceSessionService).existsSessionOnDate(today);
    }

    @Test
    @DisplayName("POST /api/practice-sessions - 練習日を登録できる")
    void testCreateSession() throws Exception {
        // Given
        when(practiceSessionService.createSession(any(PracticeSessionCreateRequest.class), anyLong()))
                .thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(post("/api/practice-sessions")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalMatches").value(10));

        verify(practiceSessionService).createSession(any(PracticeSessionCreateRequest.class), anyLong());
    }

    @Test
    @DisplayName("POST /api/practice-sessions - 重複した日付は409を返す")
    void testCreateSessionDuplicateDate() throws Exception {
        // Given
        when(practiceSessionService.createSession(any(PracticeSessionCreateRequest.class), anyLong()))
                .thenThrow(new DuplicateResourceException("PracticeSession", "sessionDate", today));

        // When & Then
        mockMvc.perform(post("/api/practice-sessions")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409));

        verify(practiceSessionService).createSession(any(PracticeSessionCreateRequest.class), anyLong());
    }

    @Test
    @DisplayName("PUT /api/practice-sessions/{id}/total-matches - 総試合数を更新できる")
    void testUpdateTotalMatches() throws Exception {
        // Given
        PracticeSessionDto updatedSession = PracticeSessionDto.builder()
                .id(1L)
                .sessionDate(today)
                .totalMatches(15)
                .build();
        when(practiceSessionService.updateTotalMatches(1L, 15)).thenReturn(updatedSession);

        // When & Then
        mockMvc.perform(put("/api/practice-sessions/1/total-matches")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .param("totalMatches", "15"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalMatches").value(15));

        verify(practiceSessionService).updateTotalMatches(1L, 15);
    }

    @Test
    @DisplayName("PUT /api/practice-sessions/{id}/total-matches - 負の値は400を返す")
    void testUpdateTotalMatchesNegative() throws Exception {
        // Given
        when(practiceSessionService.updateTotalMatches(1L, -1))
                .thenThrow(new IllegalArgumentException("Total matches cannot be negative"));

        // When & Then
        mockMvc.perform(put("/api/practice-sessions/1/total-matches")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .param("totalMatches", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(practiceSessionService).updateTotalMatches(1L, -1);
    }

    // ========== 隣室予約確認 ==========

    @Test
    @DisplayName("POST /api/practice-sessions/{id}/confirm-reservation - ADMIN成功")
    void testConfirmReservation() throws Exception {
        // Given
        doNothing().when(adjacentRoomService).confirmReservation(eq(1L), any());
        when(practiceSessionService.findById(1L)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/1/confirm-reservation")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.ADMIN))
                        .header("X-Admin-Organization-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(adjacentRoomService).confirmReservation(eq(1L), any());
    }

    @Test
    @DisplayName("POST /api/practice-sessions/{id}/confirm-reservation - PLAYERロールは403")
    void testConfirmReservationPlayerForbidden() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/practice-sessions/1/confirm-reservation")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                .andExpect(status().isForbidden());

        verify(adjacentRoomService, never()).confirmReservation(any(), any());
    }

    @Test
    @DisplayName("POST /api/practice-sessions/{id}/confirm-reservation - サービス例外時は400")
    void testConfirmReservationServiceException() throws Exception {
        // Given
        doThrow(new IllegalStateException("かでる2・7の部屋ではないため、予約確認できません"))
                .when(adjacentRoomService).confirmReservation(eq(1L), any());

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/1/confirm-reservation")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.ADMIN))
                        .header("X-Admin-Organization-Id", "1"))
                .andExpect(status().isBadRequest());

        verify(adjacentRoomService).confirmReservation(eq(1L), any());
    }

    // ========== 会場拡張 ==========

    @Test
    @DisplayName("POST /api/practice-sessions/{id}/expand-venue - 会場を拡張できる")
    void testExpandVenue() throws Exception {
        // Given
        doNothing().when(adjacentRoomService).expandVenue(eq(1L), any());
        when(practiceSessionService.findById(1L)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/1/expand-venue")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.ADMIN))
                        .header("X-Admin-Organization-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(adjacentRoomService).expandVenue(eq(1L), any());
    }

    @Test
    @DisplayName("POST /api/practice-sessions/{id}/expand-venue - PLAYERロールは403")
    void testExpandVenuePlayerForbidden() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/practice-sessions/1/expand-venue")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.PLAYER)))
                .andExpect(status().isForbidden());

        verify(adjacentRoomService, never()).expandVenue(any(), any());
    }

    @Test
    @DisplayName("POST /api/practice-sessions/{id}/expand-venue - 隣室空きなしで400")
    void testExpandVenueNotAvailable() throws Exception {
        // Given
        doThrow(new IllegalStateException("隣室が空いていないため、会場を拡張できません"))
                .when(adjacentRoomService).expandVenue(eq(1L), any());

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/1/expand-venue")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.ADMIN))
                        .header("X-Admin-Organization-Id", "1"))
                .andExpect(status().isBadRequest());

        verify(adjacentRoomService).expandVenue(eq(1L), any());
    }

    @Test
    @DisplayName("DELETE /api/practice-sessions/{id} - 練習日を削除できる")
    void testDeleteSession() throws Exception {
        // Given
        doNothing().when(practiceSessionService).deleteSession(1L);

        // When & Then
        mockMvc.perform(delete("/api/practice-sessions/1")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN)))
                .andExpect(status().isNoContent());

        verify(practiceSessionService).deleteSession(1L);
    }

    // ========== 参加登録 playerId 検証 ==========

    @Test
    @DisplayName("POST /api/practice-sessions/participations - PLAYERが自分のplayerIdで参加登録できる（201）")
    void testRegisterParticipationsPlayerOwnId() throws Exception {
        // Given
        PracticeParticipationRequest participationRequest = PracticeParticipationRequest.builder()
                .playerId(10L)
                .year(2026)
                .month(4)
                .participations(List.of(
                        PracticeParticipationRequest.SessionMatchParticipation.builder()
                                .sessionId(1L).matchNumber(1).build()
                ))
                .build();

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/participations")
                        .header("Authorization", AuthTestSupport.bearer(10L, Role.PLAYER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participationRequest)))
                .andExpect(status().isCreated());

        verify(practiceParticipantService).registerParticipations(any(PracticeParticipationRequest.class));
    }

    @Test
    @DisplayName("POST /api/practice-sessions/participations - PLAYERが他人のplayerIdで参加登録すると403")
    void testRegisterParticipationsPlayerOtherIdForbidden() throws Exception {
        // Given
        PracticeParticipationRequest participationRequest = PracticeParticipationRequest.builder()
                .playerId(99L)
                .year(2026)
                .month(4)
                .participations(List.of(
                        PracticeParticipationRequest.SessionMatchParticipation.builder()
                                .sessionId(1L).matchNumber(1).build()
                ))
                .build();

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/participations")
                        .header("Authorization", AuthTestSupport.bearer(10L, Role.PLAYER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participationRequest)))
                .andExpect(status().isForbidden());

        verify(practiceParticipantService, never()).registerParticipations(any());
    }

    @Test
    @DisplayName("POST /api/practice-sessions/participations - ADMINが他人のplayerIdで参加登録できる（201）")
    void testRegisterParticipationsAdminOtherIdAllowed() throws Exception {
        // Given
        PracticeParticipationRequest participationRequest = PracticeParticipationRequest.builder()
                .playerId(99L)
                .year(2026)
                .month(4)
                .participations(List.of(
                        PracticeParticipationRequest.SessionMatchParticipation.builder()
                                .sessionId(1L).matchNumber(1).build()
                ))
                .build();

        // When & Then（ADMIN userId=1 が playerId=99 で登録）
        mockMvc.perform(post("/api/practice-sessions/participations")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participationRequest)))
                .andExpect(status().isCreated());

        verify(practiceParticipantService).registerParticipations(any(PracticeParticipationRequest.class));
    }

    @Test
    @DisplayName("POST /api/practice-sessions/participations - SUPER_ADMINが他人のplayerIdで参加登録できる（201）")
    void testRegisterParticipationsSuperAdminOtherIdAllowed() throws Exception {
        // Given
        PracticeParticipationRequest participationRequest = PracticeParticipationRequest.builder()
                .playerId(99L)
                .year(2026)
                .month(4)
                .participations(List.of(
                        PracticeParticipationRequest.SessionMatchParticipation.builder()
                                .sessionId(1L).matchNumber(1).build()
                ))
                .build();

        // When & Then（SUPER_ADMIN userId=1 が playerId=99 で登録）
        mockMvc.perform(post("/api/practice-sessions/participations")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participationRequest)))
                .andExpect(status().isCreated());

        verify(practiceParticipantService).registerParticipations(any(PracticeParticipationRequest.class));
    }

    @Test
    @DisplayName("POST /api/practice-sessions/participations - 認証ヘッダ欠落時は403")
    void testRegisterParticipationsNoAuthHeaders() throws Exception {
        // Given
        PracticeParticipationRequest participationRequest = PracticeParticipationRequest.builder()
                .playerId(10L)
                .year(2026)
                .month(4)
                .participations(List.of(
                        PracticeParticipationRequest.SessionMatchParticipation.builder()
                                .sessionId(1L).matchNumber(1).build()
                ))
                .build();

        // When & Then（認証ヘッダなし）
        mockMvc.perform(post("/api/practice-sessions/participations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participationRequest)))
                .andExpect(status().isUnauthorized());

        verify(practiceParticipantService, never()).registerParticipations(any());
    }

    // ===== POST /api/practice-sessions/densuke/create-page テスト =====

    @Test
    @DisplayName("POST /densuke/create-page: 正常作成で 200 とレスポンス DTO を返す")
    void createDensukePage_returns200() throws Exception {
        com.karuta.matchtracker.dto.DensukePageCreateRequest req =
                new com.karuta.matchtracker.dto.DensukePageCreateRequest();
        req.setYear(2026);
        req.setMonth(5);
        req.setOrganizationId(1L);

        com.karuta.matchtracker.dto.DensukePageCreateResponse res =
                com.karuta.matchtracker.dto.DensukePageCreateResponse.builder()
                        .cd("wAeAQgkBpAAgeMrK")
                        .url("https://densuke.biz/list?cd=wAeAQgkBpAAgeMrK")
                        .createdDateCount(4)
                        .createdMatchSlotCount(12)
                        .build();
        when(densukePageCreateService.createPage(any())).thenReturn(res);

        mockMvc.perform(post("/api/practice-sessions/densuke/create-page")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cd").value("wAeAQgkBpAAgeMrK"))
                .andExpect(jsonPath("$.url").value("https://densuke.biz/list?cd=wAeAQgkBpAAgeMrK"))
                .andExpect(jsonPath("$.createdDateCount").value(4))
                .andExpect(jsonPath("$.createdMatchSlotCount").value(12));
    }

    @Test
    @DisplayName("POST /densuke/create-page: IllegalStateException は 400 で message を返す")
    void createDensukePage_returns400_onIllegalState() throws Exception {
        com.karuta.matchtracker.dto.DensukePageCreateRequest req =
                new com.karuta.matchtracker.dto.DensukePageCreateRequest();
        req.setYear(2026);
        req.setMonth(5);
        req.setOrganizationId(1L);

        when(densukePageCreateService.createPage(any()))
                .thenThrow(new IllegalStateException("2026年5月に練習日が登録されていません"));

        mockMvc.perform(post("/api/practice-sessions/densuke/create-page")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("2026年5月に練習日が登録されていません"));
    }

    @Test
    @DisplayName("POST /densuke/create-page: year/month/organizationId が無いと 400")
    void createDensukePage_returns400_whenRequiredFieldsMissing() throws Exception {
        com.karuta.matchtracker.dto.DensukePageCreateRequest req =
                new com.karuta.matchtracker.dto.DensukePageCreateRequest();
        // 空リクエスト → @NotNull 違反

        mockMvc.perform(post("/api/practice-sessions/densuke/create-page")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ===== DELETE /api/practice-sessions/densuke-url テスト =====

    @Test
    @DisplayName("DELETE /densuke-url: 既存レコード削除成功で 204 No Content")
    void deleteDensukeUrl_returns204_onSuccess() throws Exception {
        when(practiceSessionService.deleteDensukeUrl(2026, 5, 1L)).thenReturn(true);

        mockMvc.perform(delete("/api/practice-sessions/densuke-url")
                        .param("year", "2026")
                        .param("month", "5")
                        .param("organizationId", "1")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN)))
                .andExpect(status().isNoContent());

        verify(practiceSessionService).deleteDensukeUrl(2026, 5, 1L);
    }

    @Test
    @DisplayName("DELETE /densuke-url: 該当レコードが存在しない場合は 404 を返す")
    void deleteDensukeUrl_returns404_whenNotFound() throws Exception {
        when(practiceSessionService.deleteDensukeUrl(2026, 5, 1L)).thenReturn(false);

        mockMvc.perform(delete("/api/practice-sessions/densuke-url")
                        .param("year", "2026")
                        .param("month", "5")
                        .param("organizationId", "1")
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("2026年5月の伝助URLは登録されていません"));
    }

    // ===== POST /api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId} テスト =====
    // 対戦組み合わせ画面「参加者追加」の PLAYER 開放（団体スコープは checkScopeByDate で検証）

    @Test
    @DisplayName("POST 参加者追加: PLAYER は自分の所属団体のセッションなら追加できる（200）")
    void addParticipantToMatch_playerOwnOrg_returns200() throws Exception {
        // Given: checkScopeByDate が所属団体ID(7L)を返す（= 所属団体内で一意に解決）
        Long playerUserId = 10L;
        Long orgId = 7L;
        when(practiceSessionService.checkScopeByDate(eq(today), eq("PLAYER"), eq(playerUserId)))
                .thenReturn(orgId);
        when(practiceSessionService.findByDate(eq(today), eq(orgId))).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId}",
                        today.toString(), 3, 20L)
                        .header("Authorization", AuthTestSupport.bearer(playerUserId, Role.PLAYER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        // 検証で確定した organizationId が実更新にもレスポンス取得にも渡る（検証・更新・応答の対象一致）
        verify(practiceSessionService).checkScopeByDate(eq(today), eq("PLAYER"), eq(playerUserId));
        verify(practiceParticipantService).addParticipantToMatch(today, 3, 20L, orgId);
        verify(practiceSessionService).findByDate(today, orgId);
    }

    @Test
    @DisplayName("POST 参加者追加: PLAYER が所属外団体のセッションに追加しようとすると403")
    void addParticipantToMatch_playerOutsideOwnOrg_returns403() throws Exception {
        // Given: checkScopeByDate が ForbiddenException を投げる（所属外）
        Long playerUserId = 10L;
        doThrow(new ForbiddenException("他団体の練習日は編集できません"))
                .when(practiceSessionService).checkScopeByDate(eq(today), eq("PLAYER"), eq(playerUserId));

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId}",
                        today.toString(), 3, 20L)
                        .header("Authorization", AuthTestSupport.bearer(playerUserId, Role.PLAYER)))
                .andExpect(status().isForbidden());

        // スコープ検証で弾かれるため実際の追加処理は呼ばれない
        verify(practiceParticipantService, never()).addParticipantToMatch(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST 参加者追加: ADMIN は所属団体のセッションに追加できる（200・会員パリティ）")
    void addParticipantToMatch_admin_returns200() throws Exception {
        // Given: checkScopeByDate が所属団体ID(1L)を返す（ADMIN も会員団体スコープで解決）
        when(practiceSessionService.checkScopeByDate(eq(today), eq("ADMIN"), any()))
                .thenReturn(1L);
        when(practiceSessionService.findByDate(eq(today), eq(1L))).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(post("/api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId}",
                        today.toString(), 3, 20L)
                        .header("Authorization", AuthTestSupport.bearer(1L, Role.ADMIN)))
                .andExpect(status().isOk());

        verify(practiceParticipantService).addParticipantToMatch(today, 3, 20L, 1L);
        verify(practiceSessionService).findByDate(today, 1L);
    }

    @Test
    @DisplayName("POST 参加者追加: 認可ヘッダーなしは403")
    void addParticipantToMatch_noAuth_returns403() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/practice-sessions/date/{date}/matches/{matchNumber}/participants/{playerId}",
                        today.toString(), 3, 20L))
                .andExpect(status().isUnauthorized());

        verify(practiceParticipantService, never()).addParticipantToMatch(any(), any(), any(), any());
    }
}
