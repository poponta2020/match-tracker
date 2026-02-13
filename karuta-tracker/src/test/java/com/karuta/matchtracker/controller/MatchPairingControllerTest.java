package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.service.MatchPairingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatchPairingController.class)
@DisplayName("MatchPairingController 単体テスト")
class MatchPairingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MatchPairingService matchPairingService;

    @Nested
    @DisplayName("GET /api/match-pairings/date")
    class GetByDateTests {

        @Test
        @DisplayName("指定日付の対戦ペアリングを取得できる")
        void shouldGetPairingsByDate() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            List<MatchPairingDto> pairings = Arrays.asList(
                    new MatchPairingDto(1L, date, 1, 10L, "選手A", 20L, "選手B", 1L, null),
                    new MatchPairingDto(2L, date, 2, 30L, "選手C", 40L, "選手D", 1L, null)
            );

            when(matchPairingService.getByDate(date)).thenReturn(pairings);

            // When & Then
            mockMvc.perform(get("/api/match-pairings/date")
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].sessionDate").value("2024-01-15"))
                    .andExpect(jsonPath("$[0].matchNumber").value(1))
                    .andExpect(jsonPath("$[0].player1Id").value(10))
                    .andExpect(jsonPath("$[0].player1Name").value("選手A"))
                    .andExpect(jsonPath("$[0].player2Id").value(20))
                    .andExpect(jsonPath("$[0].player2Name").value("選手B"))
                    .andExpect(jsonPath("$[1].matchNumber").value(2));

            verify(matchPairingService).getByDate(date);
        }

        @Test
        @DisplayName("対戦ペアリングが存在しない場合は空配列を返す")
        void shouldReturnEmptyArrayWhenNoPairings() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            when(matchPairingService.getByDate(date)).thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(get("/api/match-pairings/date")
                            .param("date", "2024-01-15"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("日付パラメータが不正な形式の場合は400エラー")
        void shouldReturn400ForInvalidDateFormat() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/match-pairings/date")
                            .param("date", "invalid-date"))
                    .andExpect(status().isBadRequest());

            verify(matchPairingService, never()).getByDate(any());
        }

        @Test
        @DisplayName("日付パラメータが欠落している場合は400エラー")
        void shouldReturn400ForMissingDateParameter() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/match-pairings/date"))
                    .andExpect(status().isBadRequest());

            verify(matchPairingService, never()).getByDate(any());
        }
    }

    @Nested
    @DisplayName("GET /api/match-pairings/date-and-match")
    class GetByDateAndMatchNumberTests {

        @Test
        @DisplayName("指定日付と試合番号の対戦ペアリングを取得できる")
        void shouldGetPairingByDateAndMatchNumber() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 3;
            MatchPairingDto pairing = new MatchPairingDto(
                    1L, date, matchNumber, 10L, "選手A", 20L, "選手B", 1L, null
            );

            when(matchPairingService.getByDateAndMatchNumber(date, matchNumber))
                    .thenReturn(pairing);

            // When & Then
            mockMvc.perform(get("/api/match-pairings/date-and-match")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sessionDate").value("2024-01-15"))
                    .andExpect(jsonPath("$.matchNumber").value(3))
                    .andExpect(jsonPath("$.player1Id").value(10))
                    .andExpect(jsonPath("$.player2Id").value(20));

            verify(matchPairingService).getByDateAndMatchNumber(date, matchNumber);
        }

        @Test
        @DisplayName("対戦ペアリングが見つからない場合は404エラー")
        void shouldReturn404WhenPairingNotFound() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 99;

            when(matchPairingService.getByDateAndMatchNumber(date, matchNumber))
                    .thenThrow(new ResourceNotFoundException("Match pairing not found"));

            // When & Then
            mockMvc.perform(get("/api/match-pairings/date-and-match")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "99"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("試合番号パラメータが欠落している場合は400エラー")
        void shouldReturn400ForMissingMatchNumber() throws Exception {
            // When & Then
            mockMvc.perform(get("/api/match-pairings/date-and-match")
                            .param("date", "2024-01-15"))
                    .andExpect(status().isBadRequest());

            verify(matchPairingService, never()).getByDateAndMatchNumber(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("GET /api/match-pairings/exists")
    class ExistsTests {

        @Test
        @DisplayName("対戦ペアリングが存在する場合はtrueを返す")
        void shouldReturnTrueWhenPairingExists() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            when(matchPairingService.existsByDateAndMatchNumber(date, matchNumber))
                    .thenReturn(true);

            // When & Then
            mockMvc.perform(get("/api/match-pairings/exists")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "1"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("true"));
        }

        @Test
        @DisplayName("対戦ペアリングが存在しない場合はfalseを返す")
        void shouldReturnFalseWhenPairingDoesNotExist() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 99;
            when(matchPairingService.existsByDateAndMatchNumber(date, matchNumber))
                    .thenReturn(false);

            // When & Then
            mockMvc.perform(get("/api/match-pairings/exists")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "99"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("false"));
        }
    }

    @Nested
    @DisplayName("POST /api/match-pairings")
    class CreateTests {

        @Test
        @DisplayName("SUPER_ADMIN権限で対戦ペアリングを作成できる")
        void shouldCreatePairingAsSuperAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    date, 1, 10L, 20L
            );

            MatchPairingDto created = new MatchPairingDto(
                    1L, date, 1, 10L, "選手A", 20L, "選手B", 1L, null
            );

            when(matchPairingService.create(any(MatchPairingCreateRequest.class), anyLong()))
                    .thenReturn(created);

            // When & Then
            mockMvc.perform(post("/api/match-pairings")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.sessionDate").value("2024-01-15"))
                    .andExpect(jsonPath("$.matchNumber").value(1))
                    .andExpect(jsonPath("$.player1Id").value(10))
                    .andExpect(jsonPath("$.player2Id").value(20));

            verify(matchPairingService).create(any(MatchPairingCreateRequest.class), eq(1L));
        }

        @Test
        @DisplayName("ADMIN権限で対戦ペアリングを作成できる")
        void shouldCreatePairingAsAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    date, 1, 10L, 20L
            );

            MatchPairingDto created = new MatchPairingDto(
                    1L, date, 1, 10L, "選手A", 20L, "選手B", 1L, null
            );

            when(matchPairingService.create(any(MatchPairingCreateRequest.class), anyLong()))
                    .thenReturn(created);

            // When & Then
            mockMvc.perform(post("/api/match-pairings")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("PLAYER権限では403エラー")
        void shouldReturn403ForPlayerRole() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    date, 1, 10L, 20L
            );

            // When & Then
            mockMvc.perform(post("/api/match-pairings")
                            .header("X-User-Role", "PLAYER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(matchPairingService, never()).create(any(), anyLong());
        }

        @Test
        @DisplayName("権限ヘッダーなしでは403エラー")
        void shouldReturn403WithoutRoleHeader() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    date, 1, 10L, 20L
            );

            // When & Then
            mockMvc.perform(post("/api/match-pairings")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(matchPairingService, never()).create(any(), anyLong());
        }

        @Test
        @DisplayName("同じ選手同士の対戦の場合は400エラー")
        void shouldReturn400ForSamePlayer() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    date, 1, 10L, 10L
            );

            when(matchPairingService.create(any(MatchPairingCreateRequest.class), anyLong()))
                    .thenThrow(new IllegalArgumentException("Player cannot pair with themselves"));

            // When & Then
            mockMvc.perform(post("/api/match-pairings")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("重複する対戦ペアリングの場合は409エラー")
        void shouldReturn409ForDuplicatePairing() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    date, 1, 10L, 20L
            );

            when(matchPairingService.create(any(MatchPairingCreateRequest.class), anyLong()))
                    .thenThrow(new DuplicateResourceException("Match pairing already exists"));

            // When & Then
            mockMvc.perform(post("/api/match-pairings")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("選手が見つからない場合は404エラー")
        void shouldReturn404ForNonexistentPlayer() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                    date, 1, 999L, 20L
            );

            when(matchPairingService.create(any(MatchPairingCreateRequest.class), anyLong()))
                    .thenThrow(new ResourceNotFoundException("Player not found"));

            // When & Then
            mockMvc.perform(post("/api/match-pairings")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/match-pairings/batch")
    class CreateBatchTests {

        @Test
        @DisplayName("SUPER_ADMIN権限で対戦ペアリングを一括作成できる")
        void shouldCreateBatchPairingsAsSuperAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            List<MatchPairingCreateRequest> requests = Arrays.asList(
                    new MatchPairingCreateRequest(date, 1, 10L, 20L),
                    new MatchPairingCreateRequest(date, 2, 30L, 40L)
            );

            List<MatchPairingDto> created = Arrays.asList(
                    new MatchPairingDto(1L, date, 1, 10L, "選手A", 20L, "選手B", 1L, null),
                    new MatchPairingDto(2L, date, 2, 30L, "選手C", 40L, "選手D", 1L, null)
            );

            when(matchPairingService.createBatch(eq(date), eq(matchNumber), anyList(), anyLong()))
                    .thenReturn(created);

            // When & Then
            mockMvc.perform(post("/api/match-pairings/batch")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].matchNumber").value(1))
                    .andExpect(jsonPath("$[1].matchNumber").value(2));

            verify(matchPairingService).createBatch(eq(date), eq(matchNumber), anyList(), eq(1L));
        }

        @Test
        @DisplayName("ADMIN権限で対戦ペアリングを一括作成できる")
        void shouldCreateBatchPairingsAsAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            List<MatchPairingCreateRequest> requests = Arrays.asList(
                    new MatchPairingCreateRequest(date, 1, 10L, 20L)
            );

            List<MatchPairingDto> created = Arrays.asList(
                    new MatchPairingDto(1L, date, 1, 10L, "選手A", 20L, "選手B", 1L, null)
            );

            when(matchPairingService.createBatch(eq(date), eq(matchNumber), anyList(), anyLong()))
                    .thenReturn(created);

            // When & Then
            mockMvc.perform(post("/api/match-pairings/batch")
                            .header("X-User-Role", "ADMIN")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("PLAYER権限では403エラー")
        void shouldReturn403ForPlayerRole() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            List<MatchPairingCreateRequest> requests = Arrays.asList(
                    new MatchPairingCreateRequest(date, 1, 10L, 20L)
            );

            // When & Then
            mockMvc.perform(post("/api/match-pairings/batch")
                            .header("X-User-Role", "PLAYER")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isForbidden());

            verify(matchPairingService, never()).createBatch(any(), anyInt(), anyList(), anyLong());
        }

        @Test
        @DisplayName("空のリクエストリストの場合は空配列を返す")
        void shouldReturnEmptyArrayForEmptyRequest() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 1;
            List<MatchPairingCreateRequest> requests = Collections.emptyList();

            when(matchPairingService.createBatch(eq(date), eq(matchNumber), anyList(), anyLong()))
                    .thenReturn(Collections.emptyList());

            // When & Then
            mockMvc.perform(post("/api/match-pairings/batch")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requests)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("DELETE /api/match-pairings/{id}")
    class DeleteByIdTests {

        @Test
        @DisplayName("SUPER_ADMIN権限で対戦ペアリングを削除できる")
        void shouldDeletePairingAsSuperAdmin() throws Exception {
            // Given
            Long id = 1L;
            doNothing().when(matchPairingService).delete(id);

            // When & Then
            mockMvc.perform(delete("/api/match-pairings/{id}", id)
                            .header("X-User-Role", "SUPER_ADMIN"))
                    .andExpect(status().isOk());

            verify(matchPairingService).delete(id);
        }

        @Test
        @DisplayName("ADMIN権限で対戦ペアリングを削除できる")
        void shouldDeletePairingAsAdmin() throws Exception {
            // Given
            Long id = 1L;
            doNothing().when(matchPairingService).delete(id);

            // When & Then
            mockMvc.perform(delete("/api/match-pairings/{id}", id)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isOk());

            verify(matchPairingService).delete(id);
        }

        @Test
        @DisplayName("PLAYER権限では403エラー")
        void shouldReturn403ForPlayerRole() throws Exception {
            // Given
            Long id = 1L;

            // When & Then
            mockMvc.perform(delete("/api/match-pairings/{id}", id)
                            .header("X-User-Role", "PLAYER"))
                    .andExpect(status().isForbidden());

            verify(matchPairingService, never()).delete(anyLong());
        }
    }

    @Nested
    @DisplayName("DELETE /api/match-pairings/date-and-match")
    class DeleteByDateAndMatchNumberTests {

        @Test
        @DisplayName("SUPER_ADMIN権限で対戦ペアリングを削除できる")
        void shouldDeletePairingAsSuperAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 3;
            doNothing().when(matchPairingService).deleteByDateAndMatchNumber(date, matchNumber);

            // When & Then
            mockMvc.perform(delete("/api/match-pairings/date-and-match")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "3"))
                    .andExpect(status().isOk());

            verify(matchPairingService).deleteByDateAndMatchNumber(date, matchNumber);
        }

        @Test
        @DisplayName("ADMIN権限で対戦ペアリングを削除できる")
        void shouldDeletePairingAsAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            Integer matchNumber = 3;
            doNothing().when(matchPairingService).deleteByDateAndMatchNumber(date, matchNumber);

            // When & Then
            mockMvc.perform(delete("/api/match-pairings/date-and-match")
                            .header("X-User-Role", "ADMIN")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "3"))
                    .andExpect(status().isOk());

            verify(matchPairingService).deleteByDateAndMatchNumber(date, matchNumber);
        }

        @Test
        @DisplayName("PLAYER権限では403エラー")
        void shouldReturn403ForPlayerRole() throws Exception {
            // When & Then
            mockMvc.perform(delete("/api/match-pairings/date-and-match")
                            .header("X-User-Role", "PLAYER")
                            .param("date", "2024-01-15")
                            .param("matchNumber", "3"))
                    .andExpect(status().isForbidden());

            verify(matchPairingService, never()).deleteByDateAndMatchNumber(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("POST /api/match-pairings/auto-match")
    class AutoMatchTests {

        @Test
        @DisplayName("SUPER_ADMIN権限で自動マッチングを実行できる")
        void shouldAutoMatchAsSuperAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            List<Long> playerIds = Arrays.asList(1L, 2L, 3L, 4L);
            AutoMatchingRequest request = new AutoMatchingRequest(date, playerIds);

            List<MatchPairingDto> pairings = Arrays.asList(
                    new MatchPairingDto(1L, date, 1, 1L, "選手A", 2L, "選手B", 1L, null),
                    new MatchPairingDto(2L, date, 2, 3L, "選手C", 4L, "選手D", 1L, null)
            );

            AutoMatchingResult result = new AutoMatchingResult(pairings, Collections.emptyList());

            when(matchPairingService.autoMatch(any(AutoMatchingRequest.class)))
                    .thenReturn(result);

            // When & Then
            mockMvc.perform(post("/api/match-pairings/auto-match")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pairings", hasSize(2)))
                    .andExpect(jsonPath("$.waitingPlayers", hasSize(0)))
                    .andExpect(jsonPath("$.pairings[0].matchNumber").value(1))
                    .andExpect(jsonPath("$.pairings[1].matchNumber").value(2));

            verify(matchPairingService).autoMatch(any(AutoMatchingRequest.class));
        }

        @Test
        @DisplayName("ADMIN権限で自動マッチングを実行できる")
        void shouldAutoMatchAsAdmin() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            List<Long> playerIds = Arrays.asList(1L, 2L);
            AutoMatchingRequest request = new AutoMatchingRequest(date, playerIds);

            List<MatchPairingDto> pairings = Arrays.asList(
                    new MatchPairingDto(1L, date, 1, 1L, "選手A", 2L, "選手B", 1L, null)
            );

            AutoMatchingResult result = new AutoMatchingResult(pairings, Collections.emptyList());

            when(matchPairingService.autoMatch(any(AutoMatchingRequest.class)))
                    .thenReturn(result);

            // When & Then
            mockMvc.perform(post("/api/match-pairings/auto-match")
                            .header("X-User-Role", "ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pairings", hasSize(1)));
        }

        @Test
        @DisplayName("奇数人数の場合は待機選手が含まれる")
        void shouldIncludeWaitingPlayersForOddNumber() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            List<Long> playerIds = Arrays.asList(1L, 2L, 3L);
            AutoMatchingRequest request = new AutoMatchingRequest(date, playerIds);

            List<MatchPairingDto> pairings = Arrays.asList(
                    new MatchPairingDto(1L, date, 1, 1L, "選手A", 2L, "選手B", 1L, null)
            );

            PlayerDto waitingPlayer = new PlayerDto(
                    3L, "選手C", "A級", null, Player.Role.PLAYER, null, null, null
            );

            AutoMatchingResult result = new AutoMatchingResult(
                    pairings, Arrays.asList(waitingPlayer)
            );

            when(matchPairingService.autoMatch(any(AutoMatchingRequest.class)))
                    .thenReturn(result);

            // When & Then
            mockMvc.perform(post("/api/match-pairings/auto-match")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pairings", hasSize(1)))
                    .andExpect(jsonPath("$.waitingPlayers", hasSize(1)))
                    .andExpect(jsonPath("$.waitingPlayers[0].id").value(3))
                    .andExpect(jsonPath("$.waitingPlayers[0].name").value("選手C"));
        }

        @Test
        @DisplayName("PLAYER権限では403エラー")
        void shouldReturn403ForPlayerRole() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            List<Long> playerIds = Arrays.asList(1L, 2L);
            AutoMatchingRequest request = new AutoMatchingRequest(date, playerIds);

            // When & Then
            mockMvc.perform(post("/api/match-pairings/auto-match")
                            .header("X-User-Role", "PLAYER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());

            verify(matchPairingService, never()).autoMatch(any());
        }

        @Test
        @DisplayName("存在しない選手IDが含まれる場合は404エラー")
        void shouldReturn404ForNonexistentPlayer() throws Exception {
            // Given
            LocalDate date = LocalDate.of(2024, 1, 15);
            List<Long> playerIds = Arrays.asList(1L, 999L);
            AutoMatchingRequest request = new AutoMatchingRequest(date, playerIds);

            when(matchPairingService.autoMatch(any(AutoMatchingRequest.class)))
                    .thenThrow(new ResourceNotFoundException("Player not found"));

            // When & Then
            mockMvc.perform(post("/api/match-pairings/auto-match")
                            .header("X-User-Role", "SUPER_ADMIN")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }
}
