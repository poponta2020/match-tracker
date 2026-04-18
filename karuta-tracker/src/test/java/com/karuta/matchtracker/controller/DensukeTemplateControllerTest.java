package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.DensukeTemplateDto;
import com.karuta.matchtracker.dto.DensukeTemplateUpdateRequest;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.DensukeTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DensukeTemplateController.class)
@DisplayName("DensukeTemplateController 単体テスト")
class DensukeTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DensukeTemplateService densukeTemplateService;

    @MockitoBean
    private PlayerRepository playerRepository;

    @Test
    @DisplayName("GET /api/densuke-templates/{orgId}: 200 でテンプレート DTO を返す")
    void getTemplate_returns200() throws Exception {
        DensukeTemplateDto dto = DensukeTemplateDto.builder()
                .organizationId(1L)
                .titleTemplate("{year}年{month}月 練習出欠")
                .description("説明")
                .contactEmail("test@example.com")
                .build();
        when(densukeTemplateService.getTemplate(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/densuke-templates/1")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(1))
                .andExpect(jsonPath("$.titleTemplate").value("{year}年{month}月 練習出欠"))
                .andExpect(jsonPath("$.description").value("説明"))
                .andExpect(jsonPath("$.contactEmail").value("test@example.com"));
    }

    @Test
    @DisplayName("PUT /api/densuke-templates/{orgId}: 正常更新で 200")
    void updateTemplate_returns200() throws Exception {
        DensukeTemplateUpdateRequest request = new DensukeTemplateUpdateRequest();
        request.setTitleTemplate("{year}/{month} 練習");
        request.setDescription("新説明");
        request.setContactEmail("new@example.com");

        DensukeTemplateDto updated = DensukeTemplateDto.builder()
                .organizationId(1L)
                .titleTemplate("{year}/{month} 練習")
                .description("新説明")
                .contactEmail("new@example.com")
                .build();
        when(densukeTemplateService.updateTemplate(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/densuke-templates/1")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titleTemplate").value("{year}/{month} 練習"));
    }

    @Test
    @DisplayName("PUT /api/densuke-templates/{orgId}: titleTemplate が空なら 400")
    void updateTemplate_returns400_whenTitleMissing() throws Exception {
        DensukeTemplateUpdateRequest request = new DensukeTemplateUpdateRequest();
        request.setTitleTemplate("");

        mockMvc.perform(put("/api/densuke-templates/1")
                        .header("X-User-Role", "SUPER_ADMIN")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
