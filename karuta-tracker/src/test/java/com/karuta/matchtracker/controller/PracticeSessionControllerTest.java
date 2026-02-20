package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
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
    @DisplayName("GET /api/practice-sessions - 全練習日を取得できる")
    void testGetAllSessions() throws Exception {
        // Given
        PracticeSessionDto session2 = PracticeSessionDto.builder()
                .id(2L)
                .sessionDate(today.minusDays(1))
                .totalMatches(8)
                .build();
        when(practiceSessionService.findAllSessions()).thenReturn(List.of(testSessionDto, session2));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].totalMatches").value(10));

        verify(practiceSessionService).findAllSessions();
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
        when(practiceSessionService.findByDate(today)).thenReturn(testSessionDto);

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));

        verify(practiceSessionService).findByDate(today);
    }

    @Test
    @DisplayName("GET /api/practice-sessions/range - 期間内の練習日を取得できる")
    void testGetSessionsInRange() throws Exception {
        // Given
        LocalDate startDate = today.minusDays(7);
        LocalDate endDate = today;
        when(practiceSessionService.findSessionsInRange(startDate, endDate))
                .thenReturn(List.of(testSessionDto));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/range")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(practiceSessionService).findSessionsInRange(startDate, endDate);
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
    @DisplayName("GET /api/practice-sessions/upcoming - 今後の練習日を取得できる")
    void testGetUpcomingSessions() throws Exception {
        // Given
        when(practiceSessionService.findUpcomingSessions(today)).thenReturn(List.of(testSessionDto));

        // When & Then
        mockMvc.perform(get("/api/practice-sessions/upcoming")
                        .param("fromDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(practiceSessionService).findUpcomingSessions(today);
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
                        .param("totalMatches", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(practiceSessionService).updateTotalMatches(1L, -1);
    }

    @Test
    @DisplayName("DELETE /api/practice-sessions/{id} - 練習日を削除できる")
    void testDeleteSession() throws Exception {
        // Given
        doNothing().when(practiceSessionService).deleteSession(1L);

        // When & Then
        mockMvc.perform(delete("/api/practice-sessions/1"))
                .andExpect(status().isNoContent());

        verify(practiceSessionService).deleteSession(1L);
    }
}
