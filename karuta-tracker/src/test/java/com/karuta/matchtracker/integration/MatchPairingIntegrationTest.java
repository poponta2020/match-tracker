package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.AutoMatchingRequest;
import com.karuta.matchtracker.dto.AutoMatchingResult;
import com.karuta.matchtracker.dto.MatchPairingCreateRequest;
import com.karuta.matchtracker.dto.MatchPairingDto;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("MatchPairing 統合テスト")
class MatchPairingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchPairingRepository matchPairingRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private PracticeSessionRepository practiceSessionRepository;

    @Autowired
    private PracticeParticipantRepository practiceParticipantRepository;

    @Test
    @DisplayName("対戦ペアリングのCRUDライフサイクルが正常に動作する")
    void shouldCompletePairingCrudLifecycle() throws Exception {
        // Given: テスト用選手を作成
        Player player1 = createAndSavePlayer("統合選手A", "A級");
        Player player2 = createAndSavePlayer("統合選手B", "B級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 10);
        Integer matchNumber = 1;

        MatchPairingCreateRequest createRequest = new MatchPairingCreateRequest(
                sessionDate, matchNumber, player1.getId(), player2.getId()
        );

        // When & Then: 対戦ペアリングを作成
        String createResponse = mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionDate").value("2024-02-10"))
                .andExpect(jsonPath("$.matchNumber").value(1))
                .andExpect(jsonPath("$.player1Id").value(player1.getId()))
                .andExpect(jsonPath("$.player2Id").value(player2.getId()))
                .andReturn().getResponse().getContentAsString();

        MatchPairingDto created = objectMapper.readValue(createResponse, MatchPairingDto.class);
        Long pairingId = created.getId();

        // Then: データベースに保存されていることを確認
        assertThat(matchPairingRepository.findById(pairingId)).isPresent();

        // When & Then: 日付で対戦ペアリングを取得
        mockMvc.perform(get("/api/match-pairings/date")
                        .param("date", "2024-02-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pairingId))
                .andExpect(jsonPath("$[0].matchNumber").value(1));

        // When & Then: 日付と試合番号で対戦ペアリングを取得
        mockMvc.perform(get("/api/match-pairings/date-and-match")
                        .param("date", "2024-02-10")
                        .param("matchNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(pairingId));

        // When & Then: 存在確認
        mockMvc.perform(get("/api/match-pairings/exists")
                        .param("date", "2024-02-10")
                        .param("matchNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));

        // When & Then: 日付と試合番号で削除
        mockMvc.perform(delete("/api/match-pairings/date-and-match")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .param("date", "2024-02-10")
                        .param("matchNumber", "1"))
                .andExpect(status().isNoContent());

        // Then: データベースから削除されていることを確認
        assertThat(matchPairingRepository.findById(pairingId)).isEmpty();

        // Then: 存在確認でfalseが返ることを確認
        mockMvc.perform(get("/api/match-pairings/exists")
                        .param("date", "2024-02-10")
                        .param("matchNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    @DisplayName("同じ日付の複数の対戦ペアリングを作成し、一覧取得できる")
    void shouldCreateAndListMultiplePairings() throws Exception {
        // Given: テスト用選手を作成
        Player player1 = createAndSavePlayer("選手1", "A級");
        Player player2 = createAndSavePlayer("選手2", "A級");
        Player player3 = createAndSavePlayer("選手3", "B級");
        Player player4 = createAndSavePlayer("選手4", "B級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 11);

        // When: 2つの対戦ペアリングを作成
        MatchPairingCreateRequest request1 = new MatchPairingCreateRequest(
                sessionDate, 1, player1.getId(), player2.getId()
        );
        mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        MatchPairingCreateRequest request2 = new MatchPairingCreateRequest(
                sessionDate, 2, player3.getId(), player4.getId()
        );
        mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // Then: 一覧取得で2件取得でき、試合番号順になっている
        mockMvc.perform(get("/api/match-pairings/date")
                        .param("date", "2024-02-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].matchNumber").value(1))
                .andExpect(jsonPath("$[0].player1Id").value(player1.getId()))
                .andExpect(jsonPath("$[0].player2Id").value(player2.getId()))
                .andExpect(jsonPath("$[1].matchNumber").value(2))
                .andExpect(jsonPath("$[1].player1Id").value(player3.getId()))
                .andExpect(jsonPath("$[1].player2Id").value(player4.getId()));
    }

    @Test
    @DisplayName("一括作成で複数の対戦ペアリングを同時に作成できる")
    void shouldBatchCreateMultiplePairings() throws Exception {
        // Given: テスト用選手を作成
        Player player1 = createAndSavePlayer("一括選手1", "A級");
        Player player2 = createAndSavePlayer("一括選手2", "A級");
        Player player3 = createAndSavePlayer("一括選手3", "B級");
        Player player4 = createAndSavePlayer("一括選手4", "B級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 12);

        List<MatchPairingCreateRequest> pairingRequests = Arrays.asList(
                new MatchPairingCreateRequest(sessionDate, 1, player1.getId(), player2.getId()),
                new MatchPairingCreateRequest(sessionDate, 1, player3.getId(), player4.getId())
        );

        com.karuta.matchtracker.dto.MatchPairingBatchRequest batchRequest =
                com.karuta.matchtracker.dto.MatchPairingBatchRequest.builder()
                        .pairings(pairingRequests)
                        .waitingPlayerIds(List.of())
                        .build();

        // When: 一括作成（matchNumber=1の組み合わせを2ペア作成）
        mockMvc.perform(post("/api/match-pairings/batch")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .param("date", "2024-02-12")
                        .param("matchNumber", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)));

        // Then: データベースに保存されている
        List<com.karuta.matchtracker.entity.MatchPairing> savedPairings =
                matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
        assertThat(savedPairings).hasSize(2);
        assertThat(savedPairings.get(0).getMatchNumber()).isEqualTo(1);
        assertThat(savedPairings.get(1).getMatchNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("同じ選手同士の対戦ペアリング作成は失敗する")
    void shouldFailToCreatePairingWithSamePlayer() throws Exception {
        // Given: テスト用選手を作成
        Player player = createAndSavePlayer("同一選手", "A級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 13);
        MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                sessionDate, 1, player.getId(), player.getId()
        );

        // When & Then: 同じ選手同士の対戦は400エラー
        mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // Then: データベースに保存されていない
        List<com.karuta.matchtracker.entity.MatchPairing> pairings =
                matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
        assertThat(pairings).isEmpty();
    }

    @Test
    @DisplayName("同一試合番号に複数の対戦ペアリングを作成できる")
    void shouldAllowMultiplePairingsForSameMatchNumber() throws Exception {
        // Given: テスト用選手を作成
        Player player1 = createAndSavePlayer("重複選手A", "A級");
        Player player2 = createAndSavePlayer("重複選手B", "A級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 14);
        MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                sessionDate, 1, player1.getId(), player2.getId()
        );

        // When: 1回目の作成は成功
        mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // When: 同じ日付と試合番号で2回目の作成も成功する（同一試合番号に複数ペアが存在可能）
        mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Then: データベースに2件保存されている
        List<com.karuta.matchtracker.entity.MatchPairing> pairings =
                matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
        assertThat(pairings).hasSize(2);
    }

    @Test
    @DisplayName("自動マッチングが偶数人数で正しく動作する")
    void shouldAutoMatchWithEvenNumberOfPlayers() throws Exception {
        // Given: テスト用選手を作成
        Player player1 = createAndSavePlayer("自動A", "A級");
        Player player2 = createAndSavePlayer("自動B", "A級");
        Player player3 = createAndSavePlayer("自動C", "B級");
        Player player4 = createAndSavePlayer("自動D", "B級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 15);
        List<Long> playerIds = Arrays.asList(
                player1.getId(), player2.getId(), player3.getId(), player4.getId()
        );
        createSessionWithWonParticipants(sessionDate, playerIds, 1);
        AutoMatchingRequest request = AutoMatchingRequest.builder().sessionDate(sessionDate).matchNumber(1).build();

        // When: 自動マッチング実行（提案のみ、DBには保存しない）
        String response = mockMvc.perform(post("/api/match-pairings/auto-match")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairings", hasSize(2)))
                .andExpect(jsonPath("$.waitingPlayers", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        // Then: すべての選手がいずれかのペアリング提案に含まれている
        AutoMatchingResult result = objectMapper.readValue(response, AutoMatchingResult.class);
        List<Long> pairedPlayerIds = result.getPairings().stream()
                .flatMap(p -> Arrays.asList(p.getPlayer1Id(), p.getPlayer2Id()).stream())
                .toList();
        assertThat(pairedPlayerIds).containsExactlyInAnyOrder(
                player1.getId(), player2.getId(), player3.getId(), player4.getId()
        );
    }

    @Test
    @DisplayName("自動マッチングが奇数人数で正しく動作する")
    void shouldAutoMatchWithOddNumberOfPlayers() throws Exception {
        // Given: テスト用選手を作成(奇数)
        Player player1 = createAndSavePlayer("奇数A", "A級");
        Player player2 = createAndSavePlayer("奇数B", "A級");
        Player player3 = createAndSavePlayer("奇数C", "B級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 16);
        List<Long> playerIds = Arrays.asList(
                player1.getId(), player2.getId(), player3.getId()
        );
        createSessionWithWonParticipants(sessionDate, playerIds, 1);
        AutoMatchingRequest request = AutoMatchingRequest.builder().sessionDate(sessionDate).matchNumber(1).build();

        // When: 自動マッチング実行（提案のみ、DBには保存しない）
        String response = mockMvc.perform(post("/api/match-pairings/auto-match")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairings", hasSize(1)))
                .andExpect(jsonPath("$.waitingPlayers", hasSize(1)))
                .andReturn().getResponse().getContentAsString();

        // Then: 1人が待機選手になっている
        AutoMatchingResult result = objectMapper.readValue(response, AutoMatchingResult.class);
        assertThat(result.getWaitingPlayers()).hasSize(1);
    }

    @Test
    @DisplayName("存在しない選手IDでも対戦ペアリングを作成できる（選手存在チェックは未実装）")
    void shouldCreatePairingWithNonexistentPlayerIds() throws Exception {
        // Given
        LocalDate sessionDate = LocalDate.of(2024, 2, 17);
        MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                sessionDate, 1, 9999L, 9998L
        );

        // When & Then: 選手存在チェックは未実装のため、存在しないIDでも作成される
        mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PLAYER権限では対戦ペアリング作成・削除ができない")
    void shouldNotAllowPlayerRoleToCreateOrDelete() throws Exception {
        // Given: テスト用選手を作成
        Player player1 = createAndSavePlayer("権限テストA", "A級");
        Player player2 = createAndSavePlayer("権限テストB", "A級");

        LocalDate sessionDate = LocalDate.of(2024, 2, 18);
        MatchPairingCreateRequest request = new MatchPairingCreateRequest(
                sessionDate, 1, player1.getId(), player2.getId()
        );

        // When & Then: PLAYER権限では作成できない
        mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "PLAYER").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        // Given: SUPER_ADMINで対戦ペアリングを作成
        String createResponse = mockMvc.perform(post("/api/match-pairings")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        MatchPairingDto created = objectMapper.readValue(createResponse, MatchPairingDto.class);

        // When & Then: PLAYER権限では削除できない
        mockMvc.perform(delete("/api/match-pairings/{id}", created.getId())
                        .header("X-User-Role", "PLAYER").header("X-User-Id", "1"))
                .andExpect(status().isForbidden());

        // Then: データベースには残っている
        assertThat(matchPairingRepository.findById(created.getId())).isPresent();
    }

    // ヘルパーメソッド

    private void createSessionWithWonParticipants(LocalDate sessionDate, List<Long> playerIds, Integer matchNumber) {
        PracticeSession session = PracticeSession.builder()
                .sessionDate(sessionDate)
                .totalMatches(7)
                .organizationId(1L)
                .createdBy(1L)
                .updatedBy(1L)
                .build();
        PracticeSession savedSession = practiceSessionRepository.save(session);

        List<PracticeParticipant> participants = playerIds.stream()
                .map(playerId -> PracticeParticipant.builder()
                        .sessionId(savedSession.getId())
                        .playerId(playerId)
                        .matchNumber(matchNumber)
                        .status(ParticipantStatus.WON)
                        .build())
                .toList();
        practiceParticipantRepository.saveAll(participants);
    }

    private Player createAndSavePlayer(String name, String rank) {
        Player player = new Player();
        player.setName(name);
        player.setPassword("test-password");
        player.setGender(Player.Gender.その他);
        player.setDominantHand(Player.DominantHand.右);
        // rankの値に基づいてkyuRankまたはdanRankを設定
        if (rank.endsWith("級")) {
            player.setKyuRank(Player.KyuRank.valueOf(rank));
        } else if (!rank.equals("無段") && rank.contains("段")) {
            player.setDanRank(Player.DanRank.valueOf(rank));
        }
        player.setRole(Player.Role.PLAYER);
        return playerRepository.save(player);
    }
}
