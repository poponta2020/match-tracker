package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PracticeSession APIの統合テスト
 */
@DisplayName("PracticeSession統合テスト")
class PracticeSessionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("練習日の登録から削除までの一連の操作ができる")
    void testFullSessionLifecycle() throws Exception {
        LocalDate sessionDate = LocalDate.now();

        // 1. 練習日を登録
        PracticeSessionCreateRequest createRequest = PracticeSessionCreateRequest.builder()
                .sessionDate(sessionDate)
                .totalMatches(10)
                .organizationId(1L)
                .build();

        String createResponse = mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sessionDate").value(sessionDate.toString()))
                .andExpect(jsonPath("$.totalMatches").value(10))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long sessionId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. IDで練習日を取得
        mockMvc.perform(get("/api/practice-sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.sessionDate").value(sessionDate.toString()));

        // 3. 日付で練習日を取得
        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId));

        // 4. 練習日が存在するか確認
        mockMvc.perform(get("/api/practice-sessions/exists")
                        .param("date", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        // 5. 年月で練習日リストに含まれている
        mockMvc.perform(get("/api/practice-sessions/year-month")
                        .param("year", String.valueOf(sessionDate.getYear()))
                        .param("month", String.valueOf(sessionDate.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(sessionId));

        // 6. 総試合数を更新
        mockMvc.perform(put("/api/practice-sessions/" + sessionId + "/total-matches")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .param("totalMatches", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(15));

        // 7. 練習日を削除
        mockMvc.perform(delete("/api/practice-sessions/" + sessionId)
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1"))
                .andExpect(status().isNoContent());

        // 8. 削除後は存在しない
        mockMvc.perform(get("/api/practice-sessions/exists")
                        .param("date", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("同じ日付の練習日を重複登録できない")
    void testDuplicateSessionDate() throws Exception {
        LocalDate sessionDate = LocalDate.now();

        PracticeSessionCreateRequest request = PracticeSessionCreateRequest.builder()
                .sessionDate(sessionDate)
                .totalMatches(10)
                .organizationId(1L)
                .build();

        // 1回目は成功
        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // 2回目は失敗
        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("複数の練習日を登録して年月検索ができる")
    void testMultipleSessionsAndYearMonthQuery() throws Exception {
        LocalDate today = LocalDate.now();

        // 3日分の練習日を登録
        for (int i = 0; i < 3; i++) {
            LocalDate date = today.minusDays(i);
            PracticeSessionCreateRequest request = PracticeSessionCreateRequest.builder()
                    .sessionDate(date)
                    .totalMatches(10 + i)
                    .organizationId(1L)
                    .build();

            mockMvc.perform(post("/api/practice-sessions")
                            .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // 年月で練習日を取得
        mockMvc.perform(get("/api/practice-sessions/year-month")
                        .param("year", String.valueOf(today.getYear()))
                        .param("month", String.valueOf(today.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("年月別で練習日を取得できる")
    void testGetSessionsByYearMonth() throws Exception {
        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        int currentMonth = today.getMonthValue();

        // 今月の練習日を2つ登録
        PracticeSessionCreateRequest request1 = PracticeSessionCreateRequest.builder()
                .sessionDate(today)
                .totalMatches(10)
                .organizationId(1L)
                .build();
        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        PracticeSessionCreateRequest request2 = PracticeSessionCreateRequest.builder()
                .sessionDate(today.minusDays(7))
                .totalMatches(8)
                .organizationId(1L)
                .build();
        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // 先月の練習日を1つ登録
        LocalDate lastMonth = today.minusMonths(1);
        PracticeSessionCreateRequest request3 = PracticeSessionCreateRequest.builder()
                .sessionDate(lastMonth)
                .totalMatches(12)
                .organizationId(1L)
                .build();
        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request3)))
                .andExpect(status().isCreated());

        // 今月の練習日を取得
        mockMvc.perform(get("/api/practice-sessions/year-month")
                        .param("year", String.valueOf(currentYear))
                        .param("month", String.valueOf(currentMonth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 先月の練習日を取得
        mockMvc.perform(get("/api/practice-sessions/year-month")
                        .param("year", String.valueOf(lastMonth.getYear()))
                        .param("month", String.valueOf(lastMonth.getMonthValue())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("次の参加予定練習を取得できる")
    void testGetNextParticipation() throws Exception {
        LocalDate today = LocalDate.now();

        // 未来の練習日を登録
        PracticeSessionCreateRequest futureRequest = PracticeSessionCreateRequest.builder()
                .sessionDate(today.plusDays(7))
                .totalMatches(15)
                .organizationId(1L)
                .build();
        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(futureRequest)))
                .andExpect(status().isCreated());

        // 次の参加予定を取得（playerId=1）
        mockMvc.perform(get("/api/practice-sessions/next-participation")
                        .param("playerId", "1"))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    @DisplayName("負の総試合数は設定できない")
    void testNegativeTotalMatches() throws Exception {
        LocalDate sessionDate = LocalDate.now();

        // まず練習日を登録
        PracticeSessionCreateRequest createRequest = PracticeSessionCreateRequest.builder()
                .sessionDate(sessionDate)
                .totalMatches(10)
                .organizationId(1L)
                .build();

        String createResponse = mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long sessionId = objectMapper.readTree(createResponse).get("id").asLong();

        // 負の値で更新しようとする
        mockMvc.perform(put("/api/practice-sessions/" + sessionId + "/total-matches")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .param("totalMatches", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("存在しない練習日は404を返す")
    void testNonExistentSession() throws Exception {
        mockMvc.perform(get("/api/practice-sessions/999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/practice-sessions/date")
                        .param("date", LocalDate.now().plusYears(10).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("バリデーションエラーは400を返す")
    void testValidationError() throws Exception {
        // totalMatchesが負
        PracticeSessionCreateRequest invalidRequest = PracticeSessionCreateRequest.builder()
                .sessionDate(LocalDate.now())
                .totalMatches(-5)
                .organizationId(1L)
                .build();

        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
