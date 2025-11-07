package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Player APIの統合テスト
 *
 * Controller → Service → Repository → Database の全ての層を通したテスト
 */
@DisplayName("Player統合テスト")
class PlayerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("選手の登録から削除までの一連の操作ができる")
    void testFullPlayerLifecycle() throws Exception {
        // 1. 選手を登録
        PlayerCreateRequest createRequest = PlayerCreateRequest.builder()
                .name("山田太郎")
                .password("password123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build();

        String createResponse = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("山田太郎"))
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long playerId = objectMapper.readTree(createResponse).get("id").asLong();

        // 2. IDで選手を取得
        mockMvc.perform(get("/api/players/" + playerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(playerId))
                .andExpect(jsonPath("$.name").value("山田太郎"));

        // 3. 全選手リストに含まれている
        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("山田太郎"));

        // 4. 選手情報を更新
        PlayerUpdateRequest updateRequest = PlayerUpdateRequest.builder()
                .name("山田次郎")
                .build();

        mockMvc.perform(put("/api/players/" + playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("山田次郎"));

        // 5. ロールを変更
        mockMvc.perform(put("/api/players/" + playerId + "/role")
                        .param("role", Player.Role.ADMIN.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // 6. 名前で検索
        mockMvc.perform(get("/api/players/search")
                        .param("name", "次郎"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("山田次郎"));

        // 7. ロール別で検索
        mockMvc.perform(get("/api/players/role/" + Player.Role.ADMIN.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        // 8. アクティブな選手数を確認
        mockMvc.perform(get("/api/players/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));

        // 9. 選手を削除（論理削除）
        mockMvc.perform(delete("/api/players/" + playerId))
                .andExpect(status().isNoContent());

        // 10. 削除後は全選手リストに含まれない
        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 11. アクティブな選手数が0になる
        mockMvc.perform(get("/api/players/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    @DisplayName("同じ名前の選手を重複登録できない")
    void testDuplicatePlayerName() throws Exception {
        // 1人目を登録
        PlayerCreateRequest request1 = PlayerCreateRequest.builder()
                .name("田中花子")
                .password("password123")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.右)
                .build();

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // 同じ名前で2人目を登録しようとする
        PlayerCreateRequest request2 = PlayerCreateRequest.builder()
                .name("田中花子")
                .password("password456")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .build();

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("田中花子")));
    }

    @Test
    @DisplayName("存在しない選手IDは404を返す")
    void testNonExistentPlayer() throws Exception {
        mockMvc.perform(get("/api/players/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("バリデーションエラーは400を返す")
    void testValidationError() throws Exception {
        // 名前が空
        PlayerCreateRequest invalidRequest = PlayerCreateRequest.builder()
                .name("")
                .password("password123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build();

        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"));
    }

    @Test
    @DisplayName("複数選手の登録と検索")
    void testMultiplePlayers() throws Exception {
        // 3人の選手を登録
        String[] names = {"選手A", "選手B", "選手C"};

        for (int i = 0; i < names.length; i++) {
            PlayerCreateRequest request = PlayerCreateRequest.builder()
                    .name(names[i])
                    .password("password" + i)
                    .gender(Player.Gender.男性)
                    .dominantHand(Player.DominantHand.右)
                    .build();

            mockMvc.perform(post("/api/players")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        // 全選手を取得
        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // PLAYERロールの選手のみ取得（デフォルト）
        mockMvc.perform(get("/api/players/role/" + Player.Role.PLAYER.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        // 名前で部分検索
        mockMvc.perform(get("/api/players/search")
                        .param("name", "選手"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    @DisplayName("削除した選手は更新できない")
    void testUpdateDeletedPlayer() throws Exception {
        // 選手を登録
        PlayerCreateRequest createRequest = PlayerCreateRequest.builder()
                .name("削除予定選手")
                .password("password123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build();

        String createResponse = mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long playerId = objectMapper.readTree(createResponse).get("id").asLong();

        // 選手を削除
        mockMvc.perform(delete("/api/players/" + playerId))
                .andExpect(status().isNoContent());

        // 削除した選手を更新しようとする
        PlayerUpdateRequest updateRequest = PlayerUpdateRequest.builder()
                .name("更新後の名前")
                .build();

        mockMvc.perform(put("/api/players/" + playerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }
}
