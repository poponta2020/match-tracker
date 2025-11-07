package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PlayerProfileCreateRequest;
import com.karuta.matchtracker.dto.PlayerProfileDto;
import com.karuta.matchtracker.entity.PlayerProfile;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.service.PlayerProfileService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PlayerProfileControllerのテスト
 */
@WebMvcTest(PlayerProfileController.class)
@DisplayName("PlayerProfileController 単体テスト")
class PlayerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlayerProfileService playerProfileService;

    private PlayerProfileDto testProfileDto;
    private PlayerProfileCreateRequest createRequest;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        testProfileDto = PlayerProfileDto.builder()
                .id(1L)
                .playerId(1L)
                .playerName("山田太郎")
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(LocalDate.of(2024, 1, 1))
                .validTo(null)
                .build();

        createRequest = PlayerProfileCreateRequest.builder()
                .playerId(1L)
                .grade(PlayerProfile.Grade.B)
                .dan(PlayerProfile.Dan.二)
                .validFrom(today)
                .build();
    }

    @Test
    @DisplayName("GET /api/player-profiles/current/{playerId} - 現在のプロフィールを取得できる")
    void testGetCurrentProfile() throws Exception {
        // Given
        when(playerProfileService.findCurrentProfile(1L)).thenReturn(Optional.of(testProfileDto));

        // When & Then
        mockMvc.perform(get("/api/player-profiles/current/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.playerId").value(1))
                .andExpect(jsonPath("$.playerName").value("山田太郎"))
                .andExpect(jsonPath("$.grade").value("C"))
                .andExpect(jsonPath("$.dan").value("初"));

        verify(playerProfileService).findCurrentProfile(1L);
    }

    @Test
    @DisplayName("GET /api/player-profiles/current/{playerId} - プロフィールがない場合は404を返す")
    void testGetCurrentProfileNotFound() throws Exception {
        // Given
        when(playerProfileService.findCurrentProfile(1L)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/player-profiles/current/1"))
                .andExpect(status().isNotFound());

        verify(playerProfileService).findCurrentProfile(1L);
    }

    @Test
    @DisplayName("GET /api/player-profiles/at-date/{playerId} - 特定日のプロフィールを取得できる")
    void testGetProfileAtDate() throws Exception {
        // Given
        LocalDate targetDate = LocalDate.of(2024, 6, 1);
        when(playerProfileService.findProfileAtDate(1L, targetDate))
                .thenReturn(Optional.of(testProfileDto));

        // When & Then
        mockMvc.perform(get("/api/player-profiles/at-date/1")
                        .param("date", targetDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.playerId").value(1));

        verify(playerProfileService).findProfileAtDate(1L, targetDate);
    }

    @Test
    @DisplayName("GET /api/player-profiles/history/{playerId} - プロフィール履歴を取得できる")
    void testGetProfileHistory() throws Exception {
        // Given
        PlayerProfileDto oldProfile = PlayerProfileDto.builder()
                .id(2L)
                .playerId(1L)
                .playerName("山田太郎")
                .grade(PlayerProfile.Grade.D)
                .dan(PlayerProfile.Dan.無)
                .validFrom(LocalDate.of(2023, 1, 1))
                .validTo(LocalDate.of(2023, 12, 31))
                .build();
        when(playerProfileService.findProfileHistory(1L))
                .thenReturn(List.of(testProfileDto, oldProfile));

        // When & Then
        mockMvc.perform(get("/api/player-profiles/history/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].grade").value("C"))
                .andExpect(jsonPath("$[1].grade").value("D"));

        verify(playerProfileService).findProfileHistory(1L);
    }

    @Test
    @DisplayName("GET /api/player-profiles/history/{playerId} - 存在しない選手は404を返す")
    void testGetProfileHistoryPlayerNotFound() throws Exception {
        // Given
        when(playerProfileService.findProfileHistory(999L))
                .thenThrow(new ResourceNotFoundException("Player", 999L));

        // When & Then
        mockMvc.perform(get("/api/player-profiles/history/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(playerProfileService).findProfileHistory(999L);
    }

    @Test
    @DisplayName("POST /api/player-profiles - プロフィールを登録できる")
    void testCreateProfile() throws Exception {
        // Given
        PlayerProfileDto createdProfile = PlayerProfileDto.builder()
                .id(2L)
                .playerId(1L)
                .playerName("山田太郎")
                .grade(PlayerProfile.Grade.B)
                .dan(PlayerProfile.Dan.二)
                .validFrom(today)
                .validTo(null)
                .build();
        when(playerProfileService.createProfile(any(PlayerProfileCreateRequest.class)))
                .thenReturn(createdProfile);

        // When & Then
        mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.grade").value("B"))
                .andExpect(jsonPath("$.dan").value("二"));

        verify(playerProfileService).createProfile(any(PlayerProfileCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/player-profiles - バリデーションエラーは400を返す")
    void testCreateProfileValidationError() throws Exception {
        // Given - 必須フィールドが欠けているリクエスト
        PlayerProfileCreateRequest invalidRequest = PlayerProfileCreateRequest.builder()
                .playerId(1L)
                // gradeとdanが欠けている
                .validFrom(today)
                .build();

        // When & Then
        mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.status").value(400));

        verify(playerProfileService, never()).createProfile(any(PlayerProfileCreateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/player-profiles/{profileId}/valid-to - 有効期限を設定できる")
    void testSetValidTo() throws Exception {
        // Given
        LocalDate validTo = LocalDate.of(2024, 12, 31);
        PlayerProfileDto updatedProfile = PlayerProfileDto.builder()
                .id(1L)
                .playerId(1L)
                .playerName("山田太郎")
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(LocalDate.of(2024, 1, 1))
                .validTo(validTo)
                .build();
        when(playerProfileService.setValidTo(1L, validTo)).thenReturn(updatedProfile);

        // When & Then
        mockMvc.perform(put("/api/player-profiles/1/valid-to")
                        .param("validTo", validTo.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1));

        verify(playerProfileService).setValidTo(1L, validTo);
    }

    @Test
    @DisplayName("PUT /api/player-profiles/{profileId}/valid-to - 不正な日付は400を返す")
    void testSetValidToInvalidDate() throws Exception {
        // Given
        LocalDate invalidValidTo = LocalDate.of(2023, 12, 31);
        when(playerProfileService.setValidTo(1L, invalidValidTo))
                .thenThrow(new IllegalArgumentException("valid_to must be after or equal to valid_from"));

        // When & Then
        mockMvc.perform(put("/api/player-profiles/1/valid-to")
                        .param("validTo", invalidValidTo.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(playerProfileService).setValidTo(1L, invalidValidTo);
    }

    @Test
    @DisplayName("DELETE /api/player-profiles/{profileId} - プロフィールを削除できる")
    void testDeleteProfile() throws Exception {
        // Given
        doNothing().when(playerProfileService).deleteProfile(1L);

        // When & Then
        mockMvc.perform(delete("/api/player-profiles/1"))
                .andExpect(status().isNoContent());

        verify(playerProfileService).deleteProfile(1L);
    }
}
