package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Match APIの統合テスト
 *
 * 試合結果は選手と練習日に依存するため、これらのデータも準備してテストする
 */
@DisplayName("Match統合テスト")
class MatchIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private Long player1Id;
    private Long player2Id;
    private Long player3Id;
    private LocalDate sessionDate;

    @BeforeEach
    void setUpTestData() throws Exception {
        sessionDate = LocalDate.now();

        // 選手を3人登録
        player1Id = createPlayer("選手1");
        player2Id = createPlayer("選手2");
        player3Id = createPlayer("選手3");

        // 練習日を登録
        PracticeSessionCreateRequest sessionRequest = PracticeSessionCreateRequest.builder()
                .sessionDate(sessionDate)
                .totalMatches(10)
                .build();

        mockMvc.perform(post("/api/practice-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionRequest)))
                .andExpect(status().isCreated());
    }

    private Long createPlayer(String name) throws Exception {
        PlayerCreateRequest request = PlayerCreateRequest.builder()
                .name(name)
                .password("password123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build();

        String response = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    @DisplayName("試合結果の登録から削除までの一連の操作ができる")
    void testFullMatchLifecycle() throws Exception {
        // 1. 試合結果を登録
        MatchCreateRequest createRequest = MatchCreateRequest.builder()
                .matchDate(sessionDate)
                .player1Id(player1Id)
                .player2Id(player2Id)
                .winnerId(player1Id)
                .matchNumber(1)
                .build();

        String createResponse = mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.player1Name").value("選手1"))
                .andExpect(jsonPath("$.player2Name").value("選手2"))
                .andExpect(jsonPath("$.winnerName").value("選手1"))
                .andExpect(jsonPath("$.matchNumber").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long matchId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. 日付で試合を取得
        mockMvc.perform(get("/api/matches")
                        .param("date", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(matchId));

        // 3. 試合が存在するか確認
        mockMvc.perform(get("/api/matches/exists")
                        .param("date", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        // 4. 選手1の試合を取得
        mockMvc.perform(get("/api/matches/player/" + player1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 5. 試合を削除
        mockMvc.perform(delete("/api/matches/" + matchId))
                .andExpect(status().isNoContent());

        // 6. 削除後は試合が存在しない
        mockMvc.perform(get("/api/matches/exists")
                        .param("date", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("複数の試合結果を登録して統計を取得できる")
    void testMultipleMatchesAndStatistics() throws Exception {
        // 選手1 vs 選手2 を3試合（選手1が2勝）
        createMatch(sessionDate, player1Id, player2Id, player1Id, 1);
        createMatch(sessionDate, player1Id, player2Id, player2Id, 2);
        createMatch(sessionDate, player1Id, player2Id, player1Id, 3);

        // 選手1 vs 選手3 を2試合（選手3が2勝）
        createMatch(sessionDate, player1Id, player3Id, player3Id, 4);
        createMatch(sessionDate, player1Id, player3Id, player3Id, 5);

        // 日付で試合を取得
        mockMvc.perform(get("/api/matches")
                        .param("date", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));

        // 選手1の試合を取得
        mockMvc.perform(get("/api/matches/player/" + player1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));

        // 選手1 vs 選手2の対戦成績
        mockMvc.perform(get("/api/matches/between")
                        .param("player1Id", player1Id.toString())
                        .param("player2Id", player2Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // 選手1の統計を取得
        mockMvc.perform(get("/api/matches/player/" + player1Id + "/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(5))
                .andExpect(jsonPath("$.wins").value(2))
                .andExpect(jsonPath("$.losses").value(3))
                .andExpect(jsonPath("$.winRate").value(closeTo(0.4, 0.01)));

        // 選手3の統計を取得
        mockMvc.perform(get("/api/matches/player/" + player3Id + "/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMatches").value(2))
                .andExpect(jsonPath("$.wins").value(2))
                .andExpect(jsonPath("$.losses").value(0))
                .andExpect(jsonPath("$.winRate").value(1.0));
    }

    private void createMatch(LocalDate date, Long p1Id, Long p2Id, Long winnerId, int matchNumber) throws Exception {
        MatchCreateRequest request = MatchCreateRequest.builder()
                .matchDate(date)
                .player1Id(p1Id)
                .player2Id(p2Id)
                .winnerId(winnerId)
                .matchNumber(matchNumber)
                .build();

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("期間内の試合を取得できる")
    void testGetMatchesInPeriod() throws Exception {
        LocalDate yesterday = sessionDate.minusDays(1);
        LocalDate tomorrow = sessionDate.plusDays(1);

        // 昨日の練習日を登録
        PracticeSessionCreateRequest yesterdaySession = PracticeSessionCreateRequest.builder()
                .sessionDate(yesterday)
                .totalMatches(5)
                .build();
        mockMvc.perform(post("/api/practice-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(yesterdaySession)))
                .andExpect(status().isCreated());

        // 明日の練習日を登録
        PracticeSessionCreateRequest tomorrowSession = PracticeSessionCreateRequest.builder()
                .sessionDate(tomorrow)
                .totalMatches(5)
                .build();
        mockMvc.perform(post("/api/practice-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tomorrowSession)))
                .andExpect(status().isCreated());

        // 各日に試合を登録
        createMatch(yesterday, player1Id, player2Id, player1Id, 1);
        createMatch(sessionDate, player1Id, player2Id, player2Id, 1);
        createMatch(tomorrow, player1Id, player2Id, player1Id, 1);

        // 期間内の試合を取得
        mockMvc.perform(get("/api/matches/player/" + player1Id + "/period")
                        .param("startDate", yesterday.toString())
                        .param("endDate", tomorrow.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // 今日だけの試合を取得
        mockMvc.perform(get("/api/matches/player/" + player1Id + "/period")
                        .param("startDate", sessionDate.toString())
                        .param("endDate", sessionDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("不正な試合結果は登録できない")
    void testInvalidMatch() throws Exception {
        // 同じ選手同士の試合
        MatchCreateRequest samePlayerRequest = MatchCreateRequest.builder()
                .matchDate(sessionDate)
                .player1Id(player1Id)
                .player2Id(player1Id)
                .winnerId(player1Id)
                .matchNumber(1)
                .build();

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(samePlayerRequest)))
                .andExpect(status().isBadRequest());

        // 勝者が参加者以外
        MatchCreateRequest invalidWinnerRequest = MatchCreateRequest.builder()
                .matchDate(sessionDate)
                .player1Id(player1Id)
                .player2Id(player2Id)
                .winnerId(player3Id)
                .matchNumber(1)
                .build();

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidWinnerRequest)))
                .andExpect(status().isBadRequest());

        // 存在しない練習日
        LocalDate nonExistentDate = sessionDate.plusDays(100);
        MatchCreateRequest nonExistentSessionRequest = MatchCreateRequest.builder()
                .matchDate(nonExistentDate)
                .player1Id(player1Id)
                .player2Id(player2Id)
                .winnerId(player1Id)
                .matchNumber(1)
                .build();

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonExistentSessionRequest)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("存在しない選手の試合を取得しようとすると404を返す")
    void testNonExistentPlayer() throws Exception {
        mockMvc.perform(get("/api/matches/player/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("重複した試合番号は登録できない")
    void testDuplicateMatchNumber() throws Exception {
        // 最初の試合を登録
        MatchCreateRequest request1 = MatchCreateRequest.builder()
                .matchDate(sessionDate)
                .player1Id(player1Id)
                .player2Id(player2Id)
                .winnerId(player1Id)
                .matchNumber(1)
                .build();

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // 同じ日、同じ試合番号で登録しようとする
        MatchCreateRequest request2 = MatchCreateRequest.builder()
                .matchDate(sessionDate)
                .player1Id(player1Id)
                .player2Id(player3Id)
                .winnerId(player3Id)
                .matchNumber(1)
                .build();

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict());
    }
}
