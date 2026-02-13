package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.service.PlayerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PlayerControllerのテスト
 */
@WebMvcTest(PlayerController.class)
@DisplayName("PlayerController 単体テスト")
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PlayerService playerService;

    private PlayerDto testPlayerDto;
    private PlayerCreateRequest createRequest;
    private PlayerUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        testPlayerDto = PlayerDto.builder()
                .id(1L)
                .name("山田太郎")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .build();

        createRequest = PlayerCreateRequest.builder()
                .name("佐藤花子")
                .password("password123")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .build();

        updateRequest = PlayerUpdateRequest.builder()
                .name("山田太郎（更新）")
                .build();
    }

    @Test
    @DisplayName("GET /api/players - 全選手を取得できる")
    void testGetAllPlayers() throws Exception {
        // Given
        PlayerDto player2 = PlayerDto.builder()
                .id(2L)
                .name("佐藤花子")
                .build();
        when(playerService.findAllActivePlayers()).thenReturn(List.of(testPlayerDto, player2));

        // When & Then
        mockMvc.perform(get("/api/players"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("山田太郎"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("佐藤花子"));

        verify(playerService).findAllActivePlayers();
    }

    @Test
    @DisplayName("GET /api/players/{id} - IDで選手を取得できる")
    void testGetPlayerById() throws Exception {
        // Given
        when(playerService.findById(1L)).thenReturn(testPlayerDto);

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("山田太郎"))
                .andExpect(jsonPath("$.gender").value("男性"))
                .andExpect(jsonPath("$.dominantHand").value("右"))
                .andExpect(jsonPath("$.role").value("PLAYER"));

        verify(playerService).findById(1L);
    }

    @Test
    @DisplayName("GET /api/players/{id} - 存在しないIDは404を返す")
    void testGetPlayerByIdNotFound() throws Exception {
        // Given
        when(playerService.findById(999L))
                .thenThrow(new ResourceNotFoundException("Player", 999L));

        // When & Then
        mockMvc.perform(get("/api/players/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Player not found with id: 999"))
                .andExpect(jsonPath("$.status").value(404));

        verify(playerService).findById(999L);
    }

    @Test
    @DisplayName("GET /api/players/search - 名前で検索できる")
    void testSearchPlayers() throws Exception {
        // Given
        when(playerService.searchByName("山田")).thenReturn(List.of(testPlayerDto));

        // When & Then
        mockMvc.perform(get("/api/players/search")
                        .param("name", "山田"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("山田太郎"));

        verify(playerService).searchByName("山田");
    }

    @Test
    @DisplayName("GET /api/players/role/{role} - ロール別で選手を取得できる")
    void testGetPlayersByRole() throws Exception {
        // Given
        when(playerService.findByRole(Player.Role.PLAYER)).thenReturn(List.of(testPlayerDto));

        // When & Then
        mockMvc.perform(get("/api/players/role/PLAYER"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("PLAYER"));

        verify(playerService).findByRole(Player.Role.PLAYER);
    }

    @Test
    @DisplayName("GET /api/players/count - アクティブな選手数を取得できる")
    void testCountActivePlayers() throws Exception {
        // Given
        when(playerService.countActivePlayers()).thenReturn(5L);

        // When & Then
        mockMvc.perform(get("/api/players/count"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("5"));

        verify(playerService).countActivePlayers();
    }

    @Test
    @DisplayName("POST /api/players - 選手を登録できる")
    void testCreatePlayer() throws Exception {
        // Given
        PlayerDto createdPlayer = PlayerDto.builder()
                .id(2L)
                .name("佐藤花子")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .role(Player.Role.PLAYER)
                .build();
        when(playerService.createPlayer(any(PlayerCreateRequest.class))).thenReturn(createdPlayer);

        // When & Then
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("佐藤花子"))
                .andExpect(jsonPath("$.gender").value("女性"));

        verify(playerService).createPlayer(any(PlayerCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/players - バリデーションエラーは400を返す")
    void testCreatePlayerValidationError() throws Exception {
        // Given - 名前が空のリクエスト
        PlayerCreateRequest invalidRequest = PlayerCreateRequest.builder()
                .name("") // 空の名前
                .password("pass123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build();

        // When & Then
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.status").value(400));

        verify(playerService, never()).createPlayer(any(PlayerCreateRequest.class));
    }

    @Test
    @DisplayName("POST /api/players - 重複した名前は409を返す")
    void testCreatePlayerDuplicateName() throws Exception {
        // Given
        when(playerService.createPlayer(any(PlayerCreateRequest.class)))
                .thenThrow(new DuplicateResourceException("Player", "name", "佐藤花子"));

        // When & Then
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409));

        verify(playerService).createPlayer(any(PlayerCreateRequest.class));
    }

    @Test
    @DisplayName("PUT /api/players/{id} - 選手情報を更新できる")
    void testUpdatePlayer() throws Exception {
        // Given
        PlayerDto updatedPlayer = PlayerDto.builder()
                .id(1L)
                .name("山田太郎（更新）")
                .build();
        when(playerService.updatePlayer(eq(1L), any(PlayerUpdateRequest.class)))
                .thenReturn(updatedPlayer);

        // When & Then
        mockMvc.perform(put("/api/players/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("山田太郎（更新）"));

        verify(playerService).updatePlayer(eq(1L), any(PlayerUpdateRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/players/{id} - 選手を削除できる")
    void testDeletePlayer() throws Exception {
        // Given
        doNothing().when(playerService).deletePlayer(1L);

        // When & Then
        mockMvc.perform(delete("/api/players/1"))
                .andExpect(status().isNoContent());

        verify(playerService).deletePlayer(1L);
    }

    @Test
    @DisplayName("PUT /api/players/{id}/role - ロールを変更できる")
    void testUpdatePlayerRole() throws Exception {
        // Given
        PlayerDto updatedPlayer = PlayerDto.builder()
                .id(1L)
                .name("山田太郎")
                .role(Player.Role.ADMIN)
                .build();
        when(playerService.updateRole(1L, Player.Role.ADMIN)).thenReturn(updatedPlayer);

        // When & Then
        mockMvc.perform(put("/api/players/1/role")
                        .param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        verify(playerService).updateRole(1L, Player.Role.ADMIN);
    }

    @Test
    @DisplayName("POST /api/players/login - 正しい認証情報でログインできる")
    void testLoginSuccess() throws Exception {
        // Given
        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("山田太郎", "password123");

        com.karuta.matchtracker.dto.LoginResponse response =
                com.karuta.matchtracker.dto.LoginResponse.builder()
                        .id(1L)
                        .name("山田太郎")
                        .role(Player.Role.PLAYER)
                        .currentRank("A級")
                        .build();

        when(playerService.login(any(com.karuta.matchtracker.dto.LoginRequest.class)))
                .thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/players/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("山田太郎"))
                .andExpect(jsonPath("$.role").value("PLAYER"))
                .andExpect(jsonPath("$.currentRank").value("A級"));

        verify(playerService).login(any(com.karuta.matchtracker.dto.LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/players/login - 存在しない選手名でログインすると404エラー")
    void testLoginNonexistentUser() throws Exception {
        // Given
        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("存在しない選手", "password");

        when(playerService.login(any(com.karuta.matchtracker.dto.LoginRequest.class)))
                .thenThrow(new com.karuta.matchtracker.exception.ResourceNotFoundException(
                        "選手名またはパスワードが正しくありません"));

        // When & Then
        mockMvc.perform(post("/api/players/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(playerService).login(any(com.karuta.matchtracker.dto.LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/players/login - 誤ったパスワードでログインすると404エラー")
    void testLoginWrongPassword() throws Exception {
        // Given
        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("山田太郎", "wrongPassword");

        when(playerService.login(any(com.karuta.matchtracker.dto.LoginRequest.class)))
                .thenThrow(new com.karuta.matchtracker.exception.ResourceNotFoundException(
                        "選手名またはパスワードが正しくありません"));

        // When & Then
        mockMvc.perform(post("/api/players/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        verify(playerService).login(any(com.karuta.matchtracker.dto.LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/players/login - 空の選手名でログインするとバリデーションエラー")
    void testLoginEmptyName() throws Exception {
        // Given
        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("", "password");

        // When & Then
        mockMvc.perform(post("/api/players/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(playerService, never()).login(any());
    }

    @Test
    @DisplayName("POST /api/players/login - 空のパスワードでログインするとバリデーションエラー")
    void testLoginEmptyPassword() throws Exception {
        // Given
        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("山田太郎", "");

        // When & Then
        mockMvc.perform(post("/api/players/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(playerService, never()).login(any());
    }
}
