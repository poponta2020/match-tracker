package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.exception.DuplicateMatchException;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.service.PlayerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * GlobalExceptionHandlerの単体テスト
 *
 * PlayerControllerを使って各種例外のハンドリングをテストします。
 */
@WebMvcTest(PlayerController.class)
@DisplayName("GlobalExceptionHandler 単体テスト")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    // ===== ResourceNotFoundException (404) =====

    @Test
    @DisplayName("ResourceNotFoundExceptionは404 Not Foundを返す")
    void testHandleResourceNotFoundException_Returns404() throws Exception {
        // Given
        when(playerService.findById(999L))
                .thenThrow(new ResourceNotFoundException("Player", 999L));

        // When & Then
        mockMvc.perform(get("/api/players/999"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Player not found with id: 999"))
                .andExpect(jsonPath("$.path").value("/api/players/999"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("ResourceNotFoundExceptionは名前による検索でも404を返す")
    void testHandleResourceNotFoundException_ByName_Returns404() throws Exception {
        // Given
        when(playerService.findById(1L))
                .thenThrow(new ResourceNotFoundException("Player", "TestPlayer"));

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Player not found with name: TestPlayer"));
    }

    // ===== DuplicateResourceException (409) =====

    @Test
    @DisplayName("DuplicateResourceExceptionは409 Conflictを返す")
    void testHandleDuplicateResourceException_Returns409() throws Exception {
        // Given
        when(playerService.findById(1L))
                .thenThrow(new DuplicateResourceException("Player", "田中太郎"));

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Player already exists with name: 田中太郎"))
                .andExpect(jsonPath("$.path").value("/api/players/1"));
    }

    // ===== ForbiddenException (403) =====

    @Test
    @DisplayName("ForbiddenExceptionは403 Forbiddenを返す")
    void testHandleForbiddenException_Returns403() throws Exception {
        // Given
        when(playerService.findById(1L))
                .thenThrow(new ForbiddenException("この操作を実行する権限がありません"));

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("この操作を実行する権限がありません"))
                .andExpect(jsonPath("$.path").value("/api/players/1"));
    }

    @Test
    @DisplayName("認証が必要な場合は403 Forbiddenを返す")
    void testHandleForbiddenException_AuthRequired_Returns403() throws Exception {
        // Given
        when(playerService.findById(1L))
                .thenThrow(new ForbiddenException("認証が必要です"));

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("認証が必要です"));
    }

    // ===== IllegalArgumentException (400) =====

    @Test
    @DisplayName("IllegalArgumentExceptionは400 Bad Requestを返す")
    void testHandleIllegalArgumentException_Returns400() throws Exception {
        // Given
        when(playerService.findById(1L))
                .thenThrow(new IllegalArgumentException("不正な引数です"));

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("不正な引数です"))
                .andExpect(jsonPath("$.path").value("/api/players/1"));
    }

    // ===== IllegalStateException (400) =====

    @Test
    @DisplayName("IllegalStateExceptionは400 Bad Requestを返す")
    void testHandleIllegalStateException_Returns400() throws Exception {
        // Given
        when(playerService.findById(1L))
                .thenThrow(new IllegalStateException("不正な状態です"));

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("不正な状態です"))
                .andExpect(jsonPath("$.path").value("/api/players/1"));
    }

    // ===== MethodArgumentNotValidException (400) =====

    @Test
    @DisplayName("バリデーションエラーは400 Bad Requestを返しdetailsを含む")
    void testHandleValidationException_Returns400WithDetails() throws Exception {
        // Given - 不正なリクエストボディ（空の名前）
        String invalidRequest = """
            {
                "name": "",
                "password": "password123",
                "gender": "男性",
                "dominantHand": "右"
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details").isNotEmpty());
    }

    @Test
    @DisplayName("複数のバリデーションエラーは全てdetailsに含まれる")
    void testValidationException_MultipleErrors_AllIncluded() throws Exception {
        // Given - 複数のバリデーションエラーを含むリクエスト
        String invalidRequest = """
            {
                "name": "",
                "password": "",
                "gender": "男性",
                "dominantHand": "右"
            }
            """;

        // When & Then
        mockMvc.perform(post("/api/players")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("バリデーションエラー"))
                .andExpect(jsonPath("$.details").isArray());
    }

    // ===== Exception (500) =====

    @Test
    @DisplayName("予期しない例外は500 Internal Server Errorを返す")
    void testHandleGenericException_Returns500() throws Exception {
        // Given
        when(playerService.findById(1L))
                .thenThrow(new RuntimeException("予期しないエラー"));

        // When & Then
        mockMvc.perform(get("/api/players/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("内部サーバーエラーが発生しました"))
                .andExpect(jsonPath("$.path").value("/api/players/1"));
    }
}
