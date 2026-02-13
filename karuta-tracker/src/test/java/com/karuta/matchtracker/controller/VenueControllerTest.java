package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.VenueCreateRequest;
import com.karuta.matchtracker.dto.VenueDto;
import com.karuta.matchtracker.dto.VenueMatchScheduleDto;
import com.karuta.matchtracker.dto.VenueUpdateRequest;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.service.VenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VenueControllerの単体テスト
 */
@WebMvcTest(VenueController.class)
@DisplayName("VenueController 単体テスト")
class VenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VenueService venueService;

    private VenueDto testVenueDto;
    private VenueCreateRequest createRequest;
    private VenueUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        VenueMatchScheduleDto scheduleDto = VenueMatchScheduleDto.builder()
                .id(1L)
                .matchNumber(1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        testVenueDto = VenueDto.builder()
                .id(1L)
                .name("東京会場")
                .defaultMatchCount(5)
                .schedules(List.of(scheduleDto))
                .build();

        VenueCreateRequest.MatchScheduleRequest scheduleRequest = VenueCreateRequest.MatchScheduleRequest.builder()
                .matchNumber(1)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        createRequest = VenueCreateRequest.builder()
                .name("大阪会場")
                .defaultMatchCount(6)
                .schedules(List.of(scheduleRequest))
                .build();

        VenueUpdateRequest.MatchScheduleRequest updateScheduleRequest = VenueUpdateRequest.MatchScheduleRequest.builder()
                .matchNumber(1)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 30))
                .build();

        updateRequest = VenueUpdateRequest.builder()
                .name("東京新会場")
                .defaultMatchCount(7)
                .schedules(List.of(updateScheduleRequest))
                .build();
    }

    // ===== GET /api/venues テスト =====

    @Test
    @DisplayName("GET /api/venues - 全会場を取得できる")
    void testGetAllVenues_Returns200WithList() throws Exception {
        // Given
        when(venueService.getAllVenues()).thenReturn(List.of(testVenueDto));

        // When & Then
        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("東京会場"))
                .andExpect(jsonPath("$[0].defaultMatchCount").value(5))
                .andExpect(jsonPath("$[0].schedules").isArray())
                .andExpect(jsonPath("$[0].schedules.length()").value(1));

        verify(venueService).getAllVenues();
    }

    @Test
    @DisplayName("GET /api/venues - 会場がない場合は空リストを返す")
    void testGetAllVenues_EmptyList_ReturnsEmptyArray() throws Exception {
        // Given
        when(venueService.getAllVenues()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(venueService).getAllVenues();
    }

    // ===== GET /api/venues/{id} テスト =====

    @Test
    @DisplayName("GET /api/venues/{id} - 会場をIDで取得できる")
    void testGetVenueById_ExistingId_Returns200() throws Exception {
        // Given
        when(venueService.getVenueById(1L)).thenReturn(testVenueDto);

        // When & Then
        mockMvc.perform(get("/api/venues/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("東京会場"))
                .andExpect(jsonPath("$.defaultMatchCount").value(5))
                .andExpect(jsonPath("$.schedules").isArray());

        verify(venueService).getVenueById(1L);
    }

    @Test
    @DisplayName("GET /api/venues/{id} - 存在しないIDは404を返す")
    void testGetVenueById_NonExistingId_Returns404() throws Exception {
        // Given
        when(venueService.getVenueById(999L))
                .thenThrow(new ResourceNotFoundException("Venue", 999L));

        // When & Then
        mockMvc.perform(get("/api/venues/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(venueService).getVenueById(999L);
    }

    // ===== POST /api/venues テスト =====

    @Test
    @DisplayName("POST /api/venues - 会場を作成できる")
    void testCreateVenue_ValidRequest_Returns201() throws Exception {
        // Given
        VenueDto createdVenue = VenueDto.builder()
                .id(2L)
                .name("大阪会場")
                .defaultMatchCount(6)
                .schedules(Collections.emptyList())
                .build();

        when(venueService.createVenue(any(VenueCreateRequest.class))).thenReturn(createdVenue);

        // When & Then
        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("大阪会場"))
                .andExpect(jsonPath("$.defaultMatchCount").value(6));

        verify(venueService).createVenue(any(VenueCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/venues - 重複名は409を返す")
    void testCreateVenue_DuplicateName_Returns409() throws Exception {
        // Given
        when(venueService.createVenue(any(VenueCreateRequest.class)))
                .thenThrow(new DuplicateResourceException("会場名「大阪会場」は既に登録されています"));

        // When & Then
        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409));

        verify(venueService).createVenue(any(VenueCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/venues - 空の名前は400を返す")
    void testCreateVenue_EmptyName_Returns400() throws Exception {
        // Given
        VenueCreateRequest invalidRequest = VenueCreateRequest.builder()
                .name("")  // 空の名前
                .defaultMatchCount(5)
                .schedules(List.of(VenueCreateRequest.MatchScheduleRequest.builder()
                        .matchNumber(1)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(10, 30))
                        .build()))
                .build();

        // When & Then
        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"));

        verify(venueService, never()).createVenue(any(VenueCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/venues - 範囲外のmatchCountは400を返す")
    void testCreateVenue_InvalidMatchCount_Returns400() throws Exception {
        // Given
        VenueCreateRequest invalidRequest = VenueCreateRequest.builder()
                .name("テスト会場")
                .defaultMatchCount(25)  // 最大値超過（20が上限）
                .schedules(List.of(VenueCreateRequest.MatchScheduleRequest.builder()
                        .matchNumber(1)
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(10, 30))
                        .build()))
                .build();

        // When & Then
        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(venueService, never()).createVenue(any(VenueCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/venues - スケジュールが空は400を返す")
    void testCreateVenue_EmptySchedules_Returns400() throws Exception {
        // Given
        VenueCreateRequest invalidRequest = VenueCreateRequest.builder()
                .name("テスト会場")
                .defaultMatchCount(5)
                .schedules(Collections.emptyList())  // 空のスケジュール
                .build();

        // When & Then
        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verify(venueService, never()).createVenue(any(VenueCreateRequest.class));
    }

    // ===== PUT /api/venues/{id} テスト =====

    @Test
    @DisplayName("PUT /api/venues/{id} - 会場を更新できる")
    void testUpdateVenue_ValidRequest_Returns200() throws Exception {
        // Given
        VenueDto updatedVenue = VenueDto.builder()
                .id(1L)
                .name("東京新会場")
                .defaultMatchCount(7)
                .schedules(Collections.emptyList())
                .build();

        when(venueService.updateVenue(eq(1L), any(VenueUpdateRequest.class))).thenReturn(updatedVenue);

        // When & Then
        mockMvc.perform(put("/api/venues/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("東京新会場"))
                .andExpect(jsonPath("$.defaultMatchCount").value(7));

        verify(venueService).updateVenue(eq(1L), any(VenueUpdateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/venues/{id} - 存在しないIDは404を返す")
    void testUpdateVenue_NonExistingId_Returns404() throws Exception {
        // Given
        when(venueService.updateVenue(eq(999L), any(VenueUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("Venue", 999L));

        // When & Then
        mockMvc.perform(put("/api/venues/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(venueService).updateVenue(eq(999L), any(VenueUpdateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/venues/{id} - 重複名は409を返す")
    void testUpdateVenue_DuplicateName_Returns409() throws Exception {
        // Given
        when(venueService.updateVenue(eq(1L), any(VenueUpdateRequest.class)))
                .thenThrow(new DuplicateResourceException("会場名「東京新会場」は既に登録されています"));

        // When & Then
        mockMvc.perform(put("/api/venues/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409));

        verify(venueService).updateVenue(eq(1L), any(VenueUpdateRequest.class));
    }

    // ===== DELETE /api/venues/{id} テスト =====

    @Test
    @DisplayName("DELETE /api/venues/{id} - 会場を削除できる")
    void testDeleteVenue_ExistingId_Returns204() throws Exception {
        // Given
        doNothing().when(venueService).deleteVenue(1L);

        // When & Then
        mockMvc.perform(delete("/api/venues/1"))
                .andExpect(status().isNoContent());

        verify(venueService).deleteVenue(1L);
    }

    @Test
    @DisplayName("DELETE /api/venues/{id} - 存在しないIDは404を返す")
    void testDeleteVenue_NonExistingId_Returns404() throws Exception {
        // Given
        doThrow(new ResourceNotFoundException("Venue", 999L))
                .when(venueService).deleteVenue(999L);

        // When & Then
        mockMvc.perform(delete("/api/venues/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404));

        verify(venueService).deleteVenue(999L);
    }
}
