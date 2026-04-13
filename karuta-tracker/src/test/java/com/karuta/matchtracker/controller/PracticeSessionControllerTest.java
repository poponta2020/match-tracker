package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PracticeParticipationRequest;
import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.PracticeSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
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
@DisplayName("PracticeSessionController 単体テスト")
class PracticeSessionControllerTest {

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
        mockMvc.perform(get("/api/practice-sessions/1"))
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
        mockMvc.perform(get("/api/practice-sessions/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(practiceSessionService).findById(999L);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/date - 日付で練習日を取得できる")
    void testGetSessionByDate() throws Exception {
        // Given
        when(practiceSessionService.findByDateWithParticipants(today)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));

        verify(practiceSessionService).findByDateWithParticipants(today);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/year-month - 年月別で練習日を取得できる")
    void testGetSessionsByYearMonth() throws Exception {
        // Given
        int year = today.getYear();
        int month = today.getMonthValue();
        when(practiceSessionService.findSessionsByYearMonth(year, month))
                .thenReturn(List.of(testSessionDto));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/year-month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(practiceSessionService).findSessionsByYearMonth(year, month);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/exists - 練習日の存在確認ができる")
    void testExistsSessionOnDate() throws Exception {
        // Given
        when(practiceSessionService.existsSessionOnDate(today)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/exists")
                        .param("date", today.toString()))
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
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "PLAYER").header("X-User-Id", "1"))
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
                        .header("X-User-Role", "ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "PLAYER").header("X-User-Id", "1"))
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
                        .header("X-User-Role", "ADMIN").header("X-User-Id", "1")
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
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1"))
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
                        .header("X-User-Role", "PLAYER").header("X-User-Id", "10")
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
                        .header("X-User-Role", "PLAYER").header("X-User-Id", "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(participationRequest)))
                .andExpect(status().isForbidden());

        verify(practiceParticipantService, never()).registerParticipations(any());
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
                .andExpect(status().isForbidden());

        verify(practiceParticipantService, never()).registerParticipations(any());
    }
}
