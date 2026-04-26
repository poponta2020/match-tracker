package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.CreateVenueProxySessionRequest;
import com.karuta.matchtracker.dto.CreateVenueProxySessionResponse;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.proxy.VenueId;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyException;
import com.karuta.matchtracker.service.proxy.VenueReservationProxyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VenueReservationProxyController.class)
@DisplayName("VenueReservationProxyController 単体テスト")
class VenueReservationProxyControllerTest {

    private static final String TOKEN = "tok-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VenueReservationProxyService venueReservationProxyService;

    @MockitoBean
    private PlayerRepository playerRepository;

    @Test
    @DisplayName("POST /api/venue-reservation-proxy/session: ADMIN はセッション作成できる")
    void createSession_adminSuccess() throws Exception {
        CreateVenueProxySessionRequest request = request(VenueId.KADERU);
        CreateVenueProxySessionResponse response = CreateVenueProxySessionResponse.builder()
                .proxyToken(TOKEN)
                .viewUrl("/api/venue-reservation-proxy/view?token=" + TOKEN)
                .venue(VenueId.KADERU)
                .build();
        when(venueReservationProxyService.createSession(any())).thenReturn(response);

        mockMvc.perform(post("/api/venue-reservation-proxy/session")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.proxyToken").value(TOKEN))
                .andExpect(jsonPath("$.viewUrl").value("/api/venue-reservation-proxy/view?token=" + TOKEN))
                .andExpect(jsonPath("$.venue").value("KADERU"));

        verify(venueReservationProxyService).createSession(argThat(req ->
                req.getVenue() == VenueId.KADERU
                        && req.getPracticeSessionId().equals(123L)
                        && "はまなす".equals(req.getRoomName())
                        && req.getDate().equals(LocalDate.of(2026, 4, 12))
                        && req.getSlotIndex() == 2));
    }

    @Test
    @DisplayName("POST /api/venue-reservation-proxy/session: PLAYER は 403")
    void createSession_playerForbidden() throws Exception {
        mockMvc.perform(post("/api/venue-reservation-proxy/session")
                        .header("X-User-Role", "PLAYER")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(VenueId.KADERU))))
                .andExpect(status().isForbidden());

        verify(venueReservationProxyService, never()).createSession(any());
    }

    @Test
    @DisplayName("POST /api/venue-reservation-proxy/session: 必須項目欠落は 400")
    void createSession_validationError() throws Exception {
        CreateVenueProxySessionRequest request = new CreateVenueProxySessionRequest();

        mockMvc.perform(post("/api/venue-reservation-proxy/session")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(venueReservationProxyService, never()).createSession(any());
    }

    @Test
    @DisplayName("GET /api/venue-reservation-proxy/view: 認可ヘッダーなしの新規タブ遷移でも token 認証で 200 を返す")
    void view_returnsHtmlWithoutAuthHeaders() throws Exception {
        when(venueReservationProxyService.view(TOKEN))
                .thenReturn(ResponseEntity.ok()
                        .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                        .body("<html>rewritten</html>"));

        // X-User-Role / X-User-Id ヘッダー無しでも token があれば 200 を返すこと。
        // 新規タブの直接 GET 遷移では axios interceptor 由来の認可ヘッダーが届かないため、
        // token (UUID) を capability として検証する設計を担保する回帰テスト。
        mockMvc.perform(get("/api/venue-reservation-proxy/view")
                        .param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(content().string("<html>rewritten</html>"));

        verify(venueReservationProxyService).view(TOKEN);
    }

    @Test
    @DisplayName("ANY /api/venue-reservation-proxy/fetch/**: 認可ヘッダーなしの GET でも token 認証で service に委譲する")
    void fetch_getDelegatesWithoutAuthHeaders() throws Exception {
        byte[] body = "<html>proxied</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(venueReservationProxyService.fetch(eq(TOKEN), any(HttpServletRequest.class)))
                .thenReturn(ResponseEntity.ok()
                        .header("X-VRP-Completed", "true")
                        .contentType(MediaType.TEXT_HTML)
                        .body(body));

        // 会場 HTML 内の <link href> や <script src> はブラウザ自動 GET になり認可ヘッダーが付かない。
        // token があれば 200 を返すこと。
        mockMvc.perform(get("/api/venue-reservation-proxy/fetch/kaderu27/index.php")
                        .param("token", TOKEN)
                        .param("p", "apply"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-VRP-Completed", "true"))
                .andExpect(content().bytes(body));

        verify(venueReservationProxyService).fetch(eq(TOKEN), any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("ANY /api/venue-reservation-proxy/fetch/**: POST を service に委譲してレスポンスを透過する")
    void fetch_postDelegatesToService() throws Exception {
        byte[] body = "<html>proxied</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(venueReservationProxyService.fetch(eq(TOKEN), any(HttpServletRequest.class)))
                .thenReturn(ResponseEntity.ok()
                        .header("X-VRP-Completed", "true")
                        .contentType(MediaType.TEXT_HTML)
                        .body(body));

        mockMvc.perform(post("/api/venue-reservation-proxy/fetch/kaderu27/index.php")
                        .param("token", TOKEN)
                        .param("p", "apply")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("name=value"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-VRP-Completed", "true"))
                .andExpect(content().bytes(body));

        verify(venueReservationProxyService).fetch(eq(TOKEN), any(HttpServletRequest.class));
    }

    @Test
    @DisplayName("VenueReservationProxyException(VENUE_NOT_SUPPORTED): 400 + errorCode/message/venue")
    void venueProxyException_badRequest() throws Exception {
        when(venueReservationProxyService.createSession(any()))
                .thenThrow(new VenueReservationProxyException(
                        VenueReservationProxyException.VENUE_NOT_SUPPORTED,
                        VenueId.HIGASHI,
                        "Venue reservation proxy is not supported for venue: HIGASHI"));

        mockMvc.perform(post("/api/venue-reservation-proxy/session")
                        .header("X-User-Role", "ADMIN")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(VenueId.HIGASHI))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VENUE_NOT_SUPPORTED"))
                .andExpect(jsonPath("$.message").value("Venue reservation proxy is not supported for venue: HIGASHI"))
                .andExpect(jsonPath("$.venue").value("HIGASHI"));
    }

    @Test
    @DisplayName("VenueReservationProxyException(SCRIPT_ERROR): 500 + errorCode/message/venue")
    void venueProxyException_serverError() throws Exception {
        when(venueReservationProxyService.view(TOKEN))
                .thenThrow(new VenueReservationProxyException(
                        VenueReservationProxyException.SCRIPT_ERROR,
                        VenueId.KADERU,
                        "Unexpected proxy error"));

        mockMvc.perform(get("/api/venue-reservation-proxy/view")
                        .param("token", TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SCRIPT_ERROR"))
                .andExpect(jsonPath("$.message").value("Unexpected proxy error"))
                .andExpect(jsonPath("$.venue").value("KADERU"));
    }

    private static CreateVenueProxySessionRequest request(VenueId venue) {
        CreateVenueProxySessionRequest request = new CreateVenueProxySessionRequest();
        request.setVenue(venue);
        request.setPracticeSessionId(123L);
        request.setRoomName("はまなす");
        request.setDate(LocalDate.of(2026, 4, 12));
        request.setSlotIndex(2);
        return request;
    }
}
