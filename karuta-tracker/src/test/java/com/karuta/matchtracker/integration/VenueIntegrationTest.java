package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.VenueCreateRequest;
import com.karuta.matchtracker.dto.VenueUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Venue統合テスト
 *
 * 実際のMySQLコンテナを使用してエンドツーエンドのテストを行います。
 */
@DisplayName("Venue 統合テスト")
class VenueIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    // ===== CRUD一連操作テスト =====

    @Test
    @DisplayName("会場のCRUD一連操作ができる")
    void testFullVenueLifecycle_CreateReadUpdateDelete() throws Exception {
        // 1. 会場を作成
        VenueCreateRequest createRequest = VenueCreateRequest.builder()
                .name("東京会場")
                .defaultMatchCount(5)
                .schedules(List.of(
                        VenueCreateRequest.MatchScheduleRequest.builder()
                                .matchNumber(1)
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(10, 30))
                                .build(),
                        VenueCreateRequest.MatchScheduleRequest.builder()
                                .matchNumber(2)
                                .startTime(LocalTime.of(10, 45))
                                .endTime(LocalTime.of(12, 15))
                                .build()
                ))
                .build();

        String createResponse = mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("東京会場"))
                .andExpect(jsonPath("$.defaultMatchCount").value(5))
                .andExpect(jsonPath("$.schedules", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long venueId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. IDで会場を取得
        mockMvc.perform(get("/api/venues/" + venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(venueId))
                .andExpect(jsonPath("$.name").value("東京会場"))
                .andExpect(jsonPath("$.schedules", hasSize(2)));

        // 3. 会場を更新
        VenueUpdateRequest updateRequest = VenueUpdateRequest.builder()
                .name("東京新会場")
                .defaultMatchCount(7)
                .schedules(List.of(
                        VenueUpdateRequest.MatchScheduleRequest.builder()
                                .matchNumber(1)
                                .startTime(LocalTime.of(10, 0))
                                .endTime(LocalTime.of(11, 30))
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/venues/" + venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("東京新会場"))
                .andExpect(jsonPath("$.defaultMatchCount").value(7))
                .andExpect(jsonPath("$.schedules", hasSize(1)));

        // 4. 会場を削除
        mockMvc.perform(delete("/api/venues/" + venueId))
                .andExpect(status().isNoContent());

        // 5. 削除後は取得できない
        mockMvc.perform(get("/api/venues/" + venueId))
                .andExpect(status().isNotFound());
    }

    // ===== 複数会場テスト =====

    @Test
    @DisplayName("複数の会場を登録して一覧取得できる")
    void testCreateMultipleVenues_ListReturnsAll() throws Exception {
        // 会場を3つ作成
        for (int i = 1; i <= 3; i++) {
            VenueCreateRequest request = VenueCreateRequest.builder()
                    .name("会場" + i)
                    .defaultMatchCount(i + 4)
                    .schedules(List.of(
                            VenueCreateRequest.MatchScheduleRequest.builder()
                                    .matchNumber(1)
                                    .startTime(LocalTime.of(9, 0))
                                    .endTime(LocalTime.of(10, 30))
                                    .build()
                    ))
                    .build();

            mockMvc.perform(post("/api/venues")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // 一覧取得
        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].schedules", hasSize(1)))
                .andExpect(jsonPath("$[1].schedules", hasSize(1)))
                .andExpect(jsonPath("$[2].schedules", hasSize(1)));
    }

    // ===== スケジュール更新テスト =====

    @Test
    @DisplayName("会場更新時にスケジュールが完全に置換される")
    void testUpdateVenueSchedules_SchedulesReplaced() throws Exception {
        // 会場を作成（スケジュール2つ）
        VenueCreateRequest createRequest = VenueCreateRequest.builder()
                .name("テスト会場")
                .defaultMatchCount(5)
                .schedules(List.of(
                        VenueCreateRequest.MatchScheduleRequest.builder()
                                .matchNumber(1)
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(10, 30))
                                .build(),
                        VenueCreateRequest.MatchScheduleRequest.builder()
                                .matchNumber(2)
                                .startTime(LocalTime.of(10, 45))
                                .endTime(LocalTime.of(12, 15))
                                .build()
                ))
                .build();

        String createResponse = mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedules", hasSize(2)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long venueId = objectMapper.readTree(createResponse).get("id").asLong();

        // 更新（スケジュール3つに変更）
        VenueUpdateRequest updateRequest = VenueUpdateRequest.builder()
                .name("テスト会場")
                .defaultMatchCount(6)
                .schedules(List.of(
                        VenueUpdateRequest.MatchScheduleRequest.builder()
                                .matchNumber(1)
                                .startTime(LocalTime.of(10, 0))
                                .endTime(LocalTime.of(11, 30))
                                .build(),
                        VenueUpdateRequest.MatchScheduleRequest.builder()
                                .matchNumber(2)
                                .startTime(LocalTime.of(11, 45))
                                .endTime(LocalTime.of(13, 15))
                                .build(),
                        VenueUpdateRequest.MatchScheduleRequest.builder()
                                .matchNumber(3)
                                .startTime(LocalTime.of(13, 30))
                                .endTime(LocalTime.of(15, 0))
                                .build()
                ))
                .build();

        mockMvc.perform(put("/api/venues/" + venueId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules", hasSize(3)));

        // 再度取得して確認
        mockMvc.perform(get("/api/venues/" + venueId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedules", hasSize(3)))
                .andExpect(jsonPath("$.schedules[0].matchNumber").value(1))
                .andExpect(jsonPath("$.schedules[1].matchNumber").value(2))
                .andExpect(jsonPath("$.schedules[2].matchNumber").value(3));
    }

    // ===== 重複エラーテスト =====

    @Test
    @DisplayName("重複した会場名で作成すると409エラーになる")
    void testCreateDuplicateVenue_Returns409() throws Exception {
        // 最初の会場を作成
        VenueCreateRequest request = VenueCreateRequest.builder()
                .name("重複テスト会場")
                .defaultMatchCount(5)
                .schedules(List.of(
                        VenueCreateRequest.MatchScheduleRequest.builder()
                                .matchNumber(1)
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(10, 30))
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // 同じ名前で再度作成を試みる
        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    // ===== 境界値テスト =====

    @Test
    @DisplayName("最大スケジュール数で会場を作成できる")
    void testVenueWithMaxSchedules_Success() throws Exception {
        // 20個のスケジュールを作成
        List<VenueCreateRequest.MatchScheduleRequest> schedules = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            schedules.add(VenueCreateRequest.MatchScheduleRequest.builder()
                    .matchNumber(i)
                    .startTime(LocalTime.of(8 + (i / 2), (i % 2) * 30))
                    .endTime(LocalTime.of(9 + (i / 2), (i % 2) * 30))
                    .build());
        }

        VenueCreateRequest request = VenueCreateRequest.builder()
                .name("大会場")
                .defaultMatchCount(20)
                .schedules(schedules)
                .build();

        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedules", hasSize(20)));
    }
}
