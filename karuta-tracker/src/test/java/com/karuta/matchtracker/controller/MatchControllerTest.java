package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.MatchCreateRequest;
import com.karuta.matchtracker.dto.MatchDto;
import com.karuta.matchtracker.dto.MatchStatisticsDto;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.service.MatchService;
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
 * MatchControllerのテスト
 */
@WebMvcTest(MatchController.class)
@DisplayName("MatchController 単体テスト")
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MatchService matchService;

    private MatchDto testMatchDto;
    private MatchCreateRequest createRequest;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        testMatchDto = MatchDto.builder()
                .id(1L)
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player1Name("山田太郎")
                .player2Id(2L)
                .player2Name("佐藤花子")
                .winnerId(1L)
                .winnerName("山田太郎")
                .scoreDifference(5)
                .build();

        createRequest = MatchCreateRequest.builder()
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(2L)
                .winnerId(1L)
                .scoreDifference(5)
                .createdBy(1L)
                .build();
    }

    @Test
    @DisplayName("GET /api/matches - 日付別の試合結果を取得できる")
    void testGetMatchesByDate() throws Exception {
        // Given
        when(matchService.findMatchesByDate(today)).thenReturn(List.of(testMatchDto));

        // When & Then
        mockMvc.perform(get("/api/matches")
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].player1Name").value("山田太郎"))
                .andExpect(jsonPath("$[0].player2Name").value("佐藤花子"));

        verify(matchService).findMatchesByDate(today);
    }

    @Test
    @DisplayName("GET /api/matches/exists - 試合の存在確認ができる")
    void testExistsMatchOnDate() throws Exception {
        // Given
        when(matchService.existsMatchOnDate(today)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/matches/exists")
                        .param("date", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("true"));

        verify(matchService).existsMatchOnDate(today);
    }

    @Test
    @DisplayName("GET /api/matches/player/{playerId} - 選手の試合履歴を取得できる")
    void testGetPlayerMatches() throws Exception {
        // Given
        when(matchService.findPlayerMatches(1L)).thenReturn(List.of(testMatchDto));

        // When & Then
        mockMvc.perform(get("/api/matches/player/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].player1Id").value(1));

        verify(matchService).findPlayerMatches(1L);
    }

    @Test
    @DisplayName("GET /api/matches/player/{playerId} - 存在しない選手は404を返す")
    void testGetPlayerMatchesNotFound() throws Exception {
        // Given
        when(matchService.findPlayerMatches(999L))
                .thenThrow(new ResourceNotFoundException("Player", 999L));

        // When & Then
        mockMvc.perform(get("/api/matches/player/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(matchService).findPlayerMatches(999L);
    }

    @Test
    @DisplayName("GET /api/matches/player/{playerId}/period - 期間内の試合を取得できる")
    void testGetPlayerMatchesInPeriod() throws Exception {
        // Given
        LocalDate startDate = today.minusDays(7);
        LocalDate endDate = today;
        when(matchService.findPlayerMatchesInPeriod(1L, startDate, endDate))
                .thenReturn(List.of(testMatchDto));

        // When & Then
        mockMvc.perform(get("/api/matches/player/1/period")
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(matchService).findPlayerMatchesInPeriod(1L, startDate, endDate);
    }

    @Test
    @DisplayName("GET /api/matches/between - 対戦履歴を取得できる")
    void testGetMatchesBetweenPlayers() throws Exception {
        // Given
        when(matchService.findMatchesBetweenPlayers(1L, 2L)).thenReturn(List.of(testMatchDto));

        // When & Then
        mockMvc.perform(get("/api/matches/between")
                        .param("player1Id", "1")
                        .param("player2Id", "2"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        verify(matchService).findMatchesBetweenPlayers(1L, 2L);
    }

    @Test
    @DisplayName("GET /api/matches/player/{playerId}/statistics - 統計情報を取得できる")
    void testGetPlayerStatistics() throws Exception {
        // Given
        MatchStatisticsDto statistics = MatchStatisticsDto.create(1L, "山田太郎", 10L, 6L);
        when(matchService.getPlayerStatistics(1L)).thenReturn(statistics);

        // When & Then
        mockMvc.perform(get("/api/matches/player/1/statistics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.playerId").value(1))
                .andExpect(jsonPath("$.playerName").value("山田太郎"))
                .andExpect(jsonPath("$.totalMatches").value(10))
                .andExpect(jsonPath("$.wins").value(6))
                .andExpect(jsonPath("$.winRate").value(60.0));

        verify(matchService).getPlayerStatistics(1L);
    }

    @Test
    @DisplayName("POST /api/matches - 試合結果を登録できる")
    void testCreateMatch() throws Exception {
        // Given
        when(matchService.createMatch(any(MatchCreateRequest.class))).thenReturn(testMatchDto);

        // When & Then
        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.matchNumber").value(1))
                .andExpect(jsonPath("$.scoreDifference").value(5));

        verify(matchService).createMatch(any(MatchCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/matches - バリデーションエラーは400を返す")
    void testCreateMatchValidationError() throws Exception {
        // Given - 点差が範囲外のリクエスト
        MatchCreateRequest invalidRequest = MatchCreateRequest.builder()
                .matchDate(today)
                .matchNumber(1)
                .player1Id(1L)
                .player2Id(2L)
                .winnerId(1L)
                .scoreDifference(30) // 範囲外（0-25）
                .createdBy(1L)
                .build();

        // When & Then
        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.status").value(400));

        verify(matchService, never()).createMatch(any(MatchCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/matches - 不正な勝者は400を返す")
    void testCreateMatchInvalidWinner() throws Exception {
        // Given
        when(matchService.createMatch(any(MatchCreateRequest.class)))
                .thenThrow(new IllegalArgumentException("Winner must be one of the players"));

        // When & Then
        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(matchService).createMatch(any(MatchCreateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/matches/{id} - 試合結果を更新できる")
    void testUpdateMatch() throws Exception {
        // Given
        MatchDto updatedMatch = MatchDto.builder()
                .id(1L)
                .winnerId(2L)
                .scoreDifference(3)
                .build();
        when(matchService.updateMatch(1L, 2L, 3, 1L)).thenReturn(updatedMatch);

        // When & Then
        mockMvc.perform(put("/api/matches/1")
                        .param("winnerId", "2")
                        .param("scoreDifference", "3")
                        .param("updatedBy", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.winnerId").value(2))
                .andExpect(jsonPath("$.scoreDifference").value(3));

        verify(matchService).updateMatch(1L, 2L, 3, 1L);
    }

    @Test
    @DisplayName("DELETE /api/matches/{id} - 試合結果を削除できる")
    void testDeleteMatch() throws Exception {
        // Given
        doNothing().when(matchService).deleteMatch(1L);

        // When & Then
        mockMvc.perform(delete("/api/matches/1"))
                .andExpect(status().isNoContent());

        verify(matchService).deleteMatch(1L);
    }
}
