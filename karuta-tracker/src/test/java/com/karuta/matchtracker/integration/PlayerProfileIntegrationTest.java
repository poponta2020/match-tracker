package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerProfileCreateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PlayerProfile;
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
 * PlayerProfile APIの統合テスト
 *
 * 選手プロフィール履歴管理の複雑なシナリオをテスト
 */
@DisplayName("PlayerProfile統合テスト")
class PlayerProfileIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private Long playerId;

    @BeforeEach
    void setUpTestData() throws Exception {
        // テスト用の選手を登録
        PlayerCreateRequest playerRequest = PlayerCreateRequest.builder()
                .name("テスト選手")
                .password("password123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build();

        String response = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(playerRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        playerId = objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    @DisplayName("プロフィールの登録から削除までの一連の操作ができる")
    void testFullProfileLifecycle() throws Exception {
        LocalDate validFrom = LocalDate.of(2024, 1, 1);

        // 1. プロフィールを登録
        PlayerProfileCreateRequest createRequest = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(validFrom)
                .build();

        String createResponse = mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.playerId").value(playerId))
                .andExpect(jsonPath("$.playerName").value("テスト選手"))
                .andExpect(jsonPath("$.grade").value("C"))
                .andExpect(jsonPath("$.dan").value("初"))
                .andExpect(jsonPath("$.validFrom").value(validFrom.toString()))
                .andExpect(jsonPath("$.validTo").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long profileId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. 現在のプロフィールを取得
        mockMvc.perform(get("/api/player-profiles/current/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId))
                .andExpect(jsonPath("$.grade").value("C"));

        // 3. 特定日のプロフィールを取得
        LocalDate queryDate = LocalDate.of(2024, 6, 1);
        mockMvc.perform(get("/api/player-profiles/at-date/" + playerId)
                        .param("date", queryDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(profileId));

        // 4. プロフィール履歴を取得
        mockMvc.perform(get("/api/player-profiles/history/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(profileId));

        // 5. 有効期限を設定
        LocalDate validTo = LocalDate.of(2024, 12, 31);
        mockMvc.perform(put("/api/player-profiles/" + profileId + "/valid-to")
                        .param("validTo", validTo.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validTo").value(validTo.toString()));

        // 6. プロフィールを削除
        mockMvc.perform(delete("/api/player-profiles/" + profileId))
                .andExpect(status().isNoContent());

        // 7. 削除後は現在のプロフィールが存在しない
        mockMvc.perform(get("/api/player-profiles/current/" + playerId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("プロフィールの昇格履歴を管理できる")
    void testProfileUpgradeHistory() throws Exception {
        // D級無段から開始
        LocalDate date1 = LocalDate.of(2023, 1, 1);
        createProfile(playerId, PlayerProfile.Grade.D, PlayerProfile.Dan.無, date1);

        // C級初段に昇格
        LocalDate date2 = LocalDate.of(2023, 6, 1);
        createProfile(playerId, PlayerProfile.Grade.C, PlayerProfile.Dan.初, date2);

        // B級二段に昇格
        LocalDate date3 = LocalDate.of(2024, 1, 1);
        createProfile(playerId, PlayerProfile.Grade.B, PlayerProfile.Dan.二, date3);

        // 履歴を取得（新しい順）
        mockMvc.perform(get("/api/player-profiles/history/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].grade").value("B"))
                .andExpect(jsonPath("$[0].dan").value("二"))
                .andExpect(jsonPath("$[1].grade").value("C"))
                .andExpect(jsonPath("$[1].dan").value("初"))
                .andExpect(jsonPath("$[2].grade").value("D"))
                .andExpect(jsonPath("$[2].dan").value("無"));

        // 現在のプロフィールはB級二段
        mockMvc.perform(get("/api/player-profiles/current/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value("B"))
                .andExpect(jsonPath("$.dan").value("二"));

        // 2023年7月時点ではC級初段
        LocalDate historicalDate = LocalDate.of(2023, 7, 1);
        mockMvc.perform(get("/api/player-profiles/at-date/" + playerId)
                        .param("date", historicalDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade").value("C"))
                .andExpect(jsonPath("$.dan").value("初"));

        // 2022年時点ではプロフィールが存在しない
        LocalDate beforeDate = LocalDate.of(2022, 12, 31);
        mockMvc.perform(get("/api/player-profiles/at-date/" + playerId)
                        .param("date", beforeDate.toString()))
                .andExpect(status().isNotFound());
    }

    private void createProfile(Long playerId, PlayerProfile.Grade grade, PlayerProfile.Dan dan, LocalDate validFrom) throws Exception {
        PlayerProfileCreateRequest request = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .grade(grade)
                .dan(dan)
                .validFrom(validFrom)
                .build();

        mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("有効期限の自動設定が正しく動作する")
    void testAutoValidToSetting() throws Exception {
        // 最初のプロフィール
        LocalDate validFrom1 = LocalDate.of(2023, 1, 1);
        PlayerProfileCreateRequest request1 = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .grade(PlayerProfile.Grade.D)
                .dan(PlayerProfile.Dan.無)
                .validFrom(validFrom1)
                .build();

        String response1 = mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.validTo").isEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long profileId1 = objectMapper.readTree(response1).get("id").asLong();

        // 2つ目のプロフィールを登録すると、1つ目のvalid_toが自動設定される
        LocalDate validFrom2 = LocalDate.of(2024, 1, 1);
        PlayerProfileCreateRequest request2 = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(validFrom2)
                .build();

        mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        // 1つ目のプロフィールのvalid_toが設定されているか確認
        mockMvc.perform(get("/api/player-profiles/history/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].id").value(profileId1))
                .andExpect(jsonPath("$[1].validTo").value(validFrom2.minusDays(1).toString()));
    }

    @Test
    @DisplayName("不正な有効期限は設定できない")
    void testInvalidValidTo() throws Exception {
        // プロフィールを登録
        LocalDate validFrom = LocalDate.of(2024, 1, 1);
        PlayerProfileCreateRequest createRequest = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(validFrom)
                .build();

        String response = mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long profileId = objectMapper.readTree(response).get("id").asLong();

        // validFromより前の日付をvalidToに設定しようとする
        LocalDate invalidValidTo = validFrom.minusDays(1);
        mockMvc.perform(put("/api/player-profiles/" + profileId + "/valid-to")
                        .param("validTo", invalidValidTo.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("存在しない選手のプロフィールは404を返す")
    void testNonExistentPlayer() throws Exception {
        mockMvc.perform(get("/api/player-profiles/current/999"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/player-profiles/history/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("プロフィールが存在しない選手は404を返す")
    void testPlayerWithoutProfile() throws Exception {
        // 新しい選手を登録（プロフィールなし）
        PlayerCreateRequest playerRequest = PlayerCreateRequest.builder()
                .name("プロフィールなし選手")
                .password("password123")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .build();

        String response = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(playerRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long newPlayerId = objectMapper.readTree(response).get("id").asLong();

        // プロフィールが存在しない
        mockMvc.perform(get("/api/player-profiles/current/" + newPlayerId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("バリデーションエラーは400を返す")
    void testValidationError() throws Exception {
        // 必須フィールドが欠けている
        PlayerProfileCreateRequest invalidRequest = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .validFrom(LocalDate.now())
                // gradeとdanが欠けている
                .build();

        mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("複数選手のプロフィール管理")
    void testMultiplePlayersProfiles() throws Exception {
        // 2人目の選手を追加
        PlayerCreateRequest player2Request = PlayerCreateRequest.builder()
                .name("選手2")
                .password("password456")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .build();

        String player2Response = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(player2Request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long player2Id = objectMapper.readTree(player2Response).get("id").asLong();

        // 選手1のプロフィール
        createProfile(playerId, PlayerProfile.Grade.C, PlayerProfile.Dan.初, LocalDate.of(2024, 1, 1));

        // 選手2のプロフィール
        createProfile(player2Id, PlayerProfile.Grade.B, PlayerProfile.Dan.二, LocalDate.of(2024, 1, 1));

        // 選手1の現在のプロフィール
        mockMvc.perform(get("/api/player-profiles/current/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("テスト選手"))
                .andExpect(jsonPath("$.grade").value("C"));

        // 選手2の現在のプロフィール
        mockMvc.perform(get("/api/player-profiles/current/" + player2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value("選手2"))
                .andExpect(jsonPath("$.grade").value("B"));
    }

    @Test
    @DisplayName("同じ日付から始まるプロフィールを重複登録できない")
    void testDuplicateValidFrom() throws Exception {
        LocalDate validFrom = LocalDate.of(2024, 1, 1);

        // 1つ目のプロフィールを登録
        PlayerProfileCreateRequest request1 = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(validFrom)
                .build();

        mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // 同じvalidFromで2つ目を登録しようとする
        PlayerProfileCreateRequest request2 = PlayerProfileCreateRequest.builder()
                .playerId(playerId)
                .grade(PlayerProfile.Grade.B)
                .dan(PlayerProfile.Dan.二)
                .validFrom(validFrom)
                .build();

        mockMvc.perform(post("/api/player-profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict());
    }
}
