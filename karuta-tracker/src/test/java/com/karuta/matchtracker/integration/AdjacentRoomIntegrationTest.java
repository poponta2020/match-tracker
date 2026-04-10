package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.repository.RoomAvailabilityCacheRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdjacentRoomServiceのトランザクション境界を通る統合テスト
 *
 * PracticeSessionService(@Transactional readOnly=true) から
 * AdjacentRoomService(@Transactional propagation=NOT_SUPPORTED) を呼び出す際、
 * DB障害時でも外側トランザクションに影響せず正常応答できることを検証する。
 */
@DisplayName("隣室空き状況 統合テスト（トランザクション境界検証）")
class AdjacentRoomIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RoomAvailabilityCacheRepository roomAvailabilityCacheRepository;

    @Test
    @DisplayName("DB障害時でも練習日詳細APIは200で応答し、隣室ステータスは「不明」になる")
    void getSessionDetail_whenCacheDbFails_returnsOkWithUnknownStatus() throws Exception {
        // RoomAvailabilityCacheRepository が DataAccessException をスローするように設定
        when(roomAvailabilityCacheRepository.findByRoomNameAndTargetDateAndTimeSlot(
                anyString(), any(LocalDate.class), anyString()))
                .thenThrow(new DataAccessResourceFailureException(
                        "relation \"room_availability_cache\" does not exist"));

        // Venue（かでる和室 id=3）を準備
        jdbcTemplate.execute(
                "INSERT INTO venues (id, name, default_match_count, capacity, created_at, updated_at) " +
                "VALUES (3, 'すずらん', 7, 14, NOW(), NOW()) ON CONFLICT (id) DO NOTHING");

        // 練習セッションを直接DBに投入（APIのPOSTだとenrichment内でmockが呼ばれるため）
        jdbcTemplate.execute(
                "INSERT INTO practice_sessions " +
                "(session_date, total_matches, venue_id, organization_id, created_by, updated_by, created_at, updated_at) " +
                "VALUES ('2026-05-01', 7, 3, 1, 1, 1, NOW(), NOW())");
        Long sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM practice_sessions WHERE session_date = '2026-05-01'", Long.class);

        // 練習日詳細APIを呼び出し
        // PracticeSessionService(@Transactional readOnly=true) →
        //   AdjacentRoomService.getAdjacentRoomAvailability(@Transactional NOT_SUPPORTED)
        // のAOP境界を通る呼び出しで、DB障害が外側トランザクションに影響しないことを検証
        mockMvc.perform(get("/api/practice-sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.adjacentRoomStatus").exists())
                .andExpect(jsonPath("$.adjacentRoomStatus.status").value("不明"))
                .andExpect(jsonPath("$.adjacentRoomStatus.available").value(false));
    }
}
