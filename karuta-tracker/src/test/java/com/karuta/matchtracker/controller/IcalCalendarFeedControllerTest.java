package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.IcalCalendarFeedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IcalCalendarFeedController の単体テスト
 */
@WebMvcTest(IcalCalendarFeedController.class)
@DisplayName("IcalCalendarFeedController 単体テスト")
class IcalCalendarFeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IcalCalendarFeedService icalCalendarFeedService;

    // RoleCheckInterceptor が依存する PlayerRepository を MockitoBean として用意する
    // (このコントローラは /ical/... なのでインターセプターは適用されないが、Bean 解決のため必要)
    @MockitoBean
    private PlayerRepository playerRepository;

    @Test
    @DisplayName("GET /ical/calendar/{token}/org/{orgId}.ics - 有効なトークンで200とtext/calendarが返る")
    void getOrgFeed_validToken_returns200WithIcsContentType() throws Exception {
        // Given
        String token = "valid-token-1234";
        Long orgId = 200L;
        String icsBody = "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//karuta-match-tracker//iCal Feed//JP\r\n"
                + "END:VCALENDAR\r\n";
        when(icalCalendarFeedService.generateIcsForOrgFeed(token, orgId)).thenReturn(icsBody);

        // When & Then
        mockMvc.perform(get("/ical/calendar/" + token + "/org/" + orgId + ".ics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN:VCALENDAR")));

        verify(icalCalendarFeedService).generateIcsForOrgFeed(token, orgId);
    }

    @Test
    @DisplayName("GET /ical/calendar/{token}/org/{orgId}.ics - 無効なトークンで404が返る")
    void getOrgFeed_invalidToken_returns404() throws Exception {
        // Given
        String token = "invalid-token";
        Long orgId = 200L;
        when(icalCalendarFeedService.generateIcsForOrgFeed(token, orgId))
                .thenThrow(new ResourceNotFoundException("Player", "icalFeedToken", token));

        // When & Then
        mockMvc.perform(get("/ical/calendar/" + token + "/org/" + orgId + ".ics"))
                .andExpect(status().isNotFound());

        verify(icalCalendarFeedService).generateIcsForOrgFeed(token, orgId);
    }

    @Test
    @DisplayName("GET /ical/calendar/{token}/org/{orgId}.ics - 所属外のorgIdで404が返る")
    void getOrgFeed_unaffiliatedOrgId_returns404() throws Exception {
        // Given
        String token = "valid-token-1234";
        Long unaffiliatedOrgId = 9999L;
        when(icalCalendarFeedService.generateIcsForOrgFeed(token, unaffiliatedOrgId))
                .thenThrow(new ResourceNotFoundException(
                        "PlayerOrganization not found with playerId: 1, organizationId: 9999"));

        // When & Then
        mockMvc.perform(get("/ical/calendar/" + token + "/org/" + unaffiliatedOrgId + ".ics"))
                .andExpect(status().isNotFound());

        verify(icalCalendarFeedService).generateIcsForOrgFeed(token, unaffiliatedOrgId);
    }

    @Test
    @DisplayName("GET /ical/calendar/{token}/guest.ics - 有効なトークンで200とtext/calendarが返る")
    void getGuestFeed_validToken_returns200WithIcsContentType() throws Exception {
        // Given
        String token = "valid-token-1234";
        String icsBody = "BEGIN:VCALENDAR\r\n"
                + "VERSION:2.0\r\n"
                + "PRODID:-//karuta-match-tracker//iCal Feed//JP\r\n"
                + "X-WR-CALNAME:ゲスト参加\r\n"
                + "END:VCALENDAR\r\n";
        when(icalCalendarFeedService.generateIcsForGuestFeed(token)).thenReturn(icsBody);

        // When & Then
        mockMvc.perform(get("/ical/calendar/" + token + "/guest.ics"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("BEGIN:VCALENDAR")));

        verify(icalCalendarFeedService).generateIcsForGuestFeed(token);
    }

    @Test
    @DisplayName("GET /ical/calendar/{token}/guest.ics - 無効なトークンで404が返る")
    void getGuestFeed_invalidToken_returns404() throws Exception {
        // Given
        String token = "invalid-token";
        when(icalCalendarFeedService.generateIcsForGuestFeed(token))
                .thenThrow(new ResourceNotFoundException("Player", "icalFeedToken", token));

        // When & Then
        mockMvc.perform(get("/ical/calendar/" + token + "/guest.ics"))
                .andExpect(status().isNotFound());

        verify(icalCalendarFeedService).generateIcsForGuestFeed(token);
    }
}
