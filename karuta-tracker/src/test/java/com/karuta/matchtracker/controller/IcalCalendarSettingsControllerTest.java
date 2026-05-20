package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.FeedInfoDto;
import com.karuta.matchtracker.dto.GuestFeedDto;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.IcalCalendarFeedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IcalCalendarSettingsController バリデーションテスト
 *
 * RoleCheckInterceptor は @WebMvcTest 配下でも WebConfig 経由で適用されるため、
 * X-User-Id / X-User-Role ヘッダーで認証コンテキストを与える。
 */
@WebMvcTest(IcalCalendarSettingsController.class)
@DisplayName("IcalCalendarSettingsController バリデーションテスト")
class IcalCalendarSettingsControllerTest {

    private static final long USER_ID = 100L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IcalCalendarFeedService icalCalendarFeedService;

    // RoleCheckInterceptor が依存する Bean。@WebMvcTest が依存解決のために要求する場合がある
    @MockitoBean
    private PlayerRepository playerRepository;

    @Test
    @DisplayName("PATCH /display-names: 空ボディは 200 + 現状の feedInfo を返し、updateDisplayNames は呼ばれない")
    void updateDisplayNames_emptyBody_returnsCurrentInfo() throws Exception {
        FeedInfoDto info = new FeedInfoDto(List.of(), new GuestFeedDto("http://localhost/ical/calendar/dummy/guest.ics"));
        when(icalCalendarFeedService.getFeedInfo(USER_ID)).thenReturn(info);

        mockMvc.perform(patch("/api/calendar/feed/display-names")
                        .header("X-User-Id", String.valueOf(USER_ID))
                        .header("X-User-Role", "PLAYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Collections.emptyMap())))
                .andExpect(status().isOk());

        verify(icalCalendarFeedService, never()).updateDisplayNames(anyLong(), anyMap());
    }

    @Test
    @DisplayName("PATCH /display-names: displayNames が null の場合も 200 で feedInfo を返す")
    void updateDisplayNames_nullDisplayNames_returnsCurrentInfo() throws Exception {
        FeedInfoDto info = new FeedInfoDto(List.of(), new GuestFeedDto("http://localhost/ical/calendar/dummy/guest.ics"));
        when(icalCalendarFeedService.getFeedInfo(USER_ID)).thenReturn(info);

        // {"displayNames": null}
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("displayNames", null);

        mockMvc.perform(patch("/api/calendar/feed/display-names")
                        .header("X-User-Id", String.valueOf(USER_ID))
                        .header("X-User-Role", "PLAYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(icalCalendarFeedService, never()).updateDisplayNames(anyLong(), anyMap());
    }

    @Test
    @DisplayName("PATCH /display-names: 表示名 50文字超は 400 Bad Request、Service は呼ばれない")
    void updateDisplayNames_over50Chars_returns400() throws Exception {
        String tooLong = "あ".repeat(51);
        Map<String, Object> body = Map.of("displayNames", Map.of("1", tooLong));

        mockMvc.perform(patch("/api/calendar/feed/display-names")
                        .header("X-User-Id", String.valueOf(USER_ID))
                        .header("X-User-Role", "PLAYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(icalCalendarFeedService, never()).updateDisplayNames(anyLong(), anyMap());
    }

    @Test
    @DisplayName("PATCH /display-names: organizationId が非数値の場合 400 Bad Request、Service は呼ばれない")
    void updateDisplayNames_nonNumericOrgId_returns400() throws Exception {
        Map<String, Object> body = Map.of("displayNames", Map.of("not-a-number", "わすら"));

        mockMvc.perform(patch("/api/calendar/feed/display-names")
                        .header("X-User-Id", String.valueOf(USER_ID))
                        .header("X-User-Role", "PLAYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(icalCalendarFeedService, never()).updateDisplayNames(anyLong(), anyMap());
    }

    @Test
    @DisplayName("PATCH /display-names: 50文字以下の正常な表示名は 200 で Service が呼ばれる")
    void updateDisplayNames_validDisplayName_returns200() throws Exception {
        String name = "あ".repeat(50);
        Map<String, Object> body = Map.of("displayNames", Map.of("1", name));
        FeedInfoDto info = new FeedInfoDto(List.of(), new GuestFeedDto("http://localhost/ical/calendar/dummy/guest.ics"));
        when(icalCalendarFeedService.updateDisplayNames(anyLong(), anyMap())).thenReturn(info);

        mockMvc.perform(patch("/api/calendar/feed/display-names")
                        .header("X-User-Id", String.valueOf(USER_ID))
                        .header("X-User-Role", "PLAYER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(icalCalendarFeedService).updateDisplayNames(anyLong(), anyMap());
    }
}
