package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.config.VenueReservationProxyConfig;
import com.karuta.matchtracker.dto.CreateVenueProxySessionRequest;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.proxy.venue.VenueConfig;
import com.karuta.matchtracker.service.proxy.venue.VenueRewriteStrategy;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("VenueReservationProxyService 単体テスト")
class VenueReservationProxyServiceTest {

    private static final String BASE_URL = "https://k2.p-kashikan.jp";
    private static final String TOKEN = "tok-123";
    private static final Long PRACTICE_SESSION_ID = 123L;
    private static final Long ORGANIZATION_ID = 99L;
    /** AdjacentRoomConfig 上で「すずらん」(隣室=「はまなす」) に対応する練習会場ID */
    private static final Long KADERU_VENUE_ID = 3L;
    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 4, 12);

    @Mock
    private VenueReservationSessionStore sessionStore;
    @Mock
    private VenueReservationCompletionDetector completionDetector;
    @Mock
    private VenueReservationClient client;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;

    private final VenueConfig venueConfig = new VenueConfig() {
        @Override public VenueId venue() { return VenueId.KADERU; }
        @Override public String baseUrl() { return BASE_URL; }
        @Override public String displayName() { return "かでる2・7"; }
        @Override public Duration sessionTimeout() { return Duration.ofMinutes(15); }
    };

    private final VenueRewriteStrategy rewriteStrategy = new VenueRewriteStrategy() {
        @Override public VenueId venue() { return VenueId.KADERU; }
        @Override public String rewriteHtml(String html, String proxyToken) { return html; }
        @Override public String injectScript() { return ""; }
    };

    @Nested
    @DisplayName("createSession")
    class CreateSession {

        @Test
        @DisplayName("enabled=true の venue は client.prepareReservationTray まで実行して viewUrl を返す")
        void createsSessionAndReturnsToken() {
            VenueReservationHtmlRewriter rewriter = new VenueReservationHtmlRewriter();
            VenueReservationProxyService service = newService(enabledConfig(), rewriter);
            stubPracticeSessionLookup(practiceSessionFixture());
            ProxySession session = session();
            when(sessionStore.createSession(
                    eq(VenueId.KADERU),
                    eq(PRACTICE_SESSION_ID),
                    eq("はまなす"),
                    eq(SESSION_DATE),
                    eq(2),
                    any())).thenReturn(session);
            doAnswer(invocation -> {
                session.setCachedTrayHtml("<html>tray</html>");
                return null;
            }).when(client).prepareReservationTray(session);

            var response = service.createSession(request(VenueId.KADERU), "SUPER_ADMIN", null);

            assertThat(response.getProxyToken()).isEqualTo(TOKEN);
            assertThat(response.getViewUrl())
                    .isEqualTo("/api/venue-reservation-proxy/view?token=" + TOKEN);
            assertThat(response.getVenue()).isEqualTo(VenueId.KADERU);
            verify(client).prepareReservationTray(session);
        }

        @Test
        @DisplayName("enabled=false の venue は VENUE_NOT_SUPPORTED で拒否する")
        void disabledVenueRejected() {
            VenueReservationProxyService service = newService(disabledHigashiConfig(), new VenueReservationHtmlRewriter());

            assertThatThrownBy(() -> service.createSession(request(VenueId.HIGASHI), "SUPER_ADMIN", null))
                    .isInstanceOfSatisfying(VenueReservationProxyException.class, ex -> {
                        assertThat(ex.getErrorCode()).isEqualTo(VenueReservationProxyException.VENUE_NOT_SUPPORTED);
                        assertThat(ex.getVenue()).isEqualTo(VenueId.HIGASHI);
                    });
            verify(sessionStore, never()).createSession(any(), any(), any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("prepareReservationTray が失敗したら作成済みセッションを破棄して例外を返す")
        void prepareFailureRemovesSession() {
            VenueReservationProxyService service = newService(enabledConfig(), new VenueReservationHtmlRewriter());
            stubPracticeSessionLookup(practiceSessionFixture());
            ProxySession session = session();
            when(sessionStore.createSession(any(), any(), any(), any(), anyInt(), any())).thenReturn(session);
            VenueReservationProxyException failure = new VenueReservationProxyException(
                    VenueReservationProxyException.LOGIN_FAILED,
                    VenueId.KADERU,
                    "login failed");
            doThrow(failure).when(client).prepareReservationTray(session);

            assertThatThrownBy(() -> service.createSession(request(VenueId.KADERU), "SUPER_ADMIN", null))
                    .isSameAs(failure);
            verify(sessionStore).remove(TOKEN);
        }

        @Test
        @DisplayName("practice_sessions が見つからない場合は ResourceNotFoundException")
        void practiceSessionMissing() {
            VenueReservationProxyService service = newService(enabledConfig(), new VenueReservationHtmlRewriter());
            when(practiceSessionRepository.findById(PRACTICE_SESSION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createSession(request(VenueId.KADERU), "SUPER_ADMIN", null))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(sessionStore, never()).createSession(any(), any(), any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("ADMIN が他団体の practice_sessions を指定した場合は ForbiddenException")
        void adminOtherOrgRejected() {
            VenueReservationProxyService service = newService(enabledConfig(), new VenueReservationHtmlRewriter());
            stubPracticeSessionLookup(practiceSessionFixture());

            assertThatThrownBy(() -> service.createSession(
                    request(VenueId.KADERU), "ADMIN", ORGANIZATION_ID + 1L))
                    .isInstanceOf(ForbiddenException.class);
            verify(sessionStore, never()).createSession(any(), any(), any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("リクエスト日付が practice_sessions の日付と異なる場合は INVALID_REQUEST")
        void dateMismatchRejected() {
            VenueReservationProxyService service = newService(enabledConfig(), new VenueReservationHtmlRewriter());
            stubPracticeSessionLookup(practiceSessionFixture());
            CreateVenueProxySessionRequest req = request(VenueId.KADERU);
            req.setDate(SESSION_DATE.plusDays(1));

            assertThatThrownBy(() -> service.createSession(req, "SUPER_ADMIN", null))
                    .isInstanceOfSatisfying(VenueReservationProxyException.class, ex ->
                            assertThat(ex.getErrorCode())
                                    .isEqualTo(VenueReservationProxyException.INVALID_REQUEST));
            verify(sessionStore, never()).createSession(any(), any(), any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("リクエスト部屋名が隣室名と異なる場合は INVALID_REQUEST")
        void roomNameMismatchRejected() {
            VenueReservationProxyService service = newService(enabledConfig(), new VenueReservationHtmlRewriter());
            stubPracticeSessionLookup(practiceSessionFixture());
            CreateVenueProxySessionRequest req = request(VenueId.KADERU);
            req.setRoomName("えぞまつ");

            assertThatThrownBy(() -> service.createSession(req, "SUPER_ADMIN", null))
                    .isInstanceOfSatisfying(VenueReservationProxyException.class, ex ->
                            assertThat(ex.getErrorCode())
                                    .isEqualTo(VenueReservationProxyException.INVALID_REQUEST));
            verify(sessionStore, never()).createSession(any(), any(), any(), any(), anyInt(), any());
        }

        @Test
        @DisplayName("KADERU 対象外の venueId が紐づく practice_sessions は INVALID_REQUEST")
        void venueIdNotKaderuRejected() {
            VenueReservationProxyService service = newService(enabledConfig(), new VenueReservationHtmlRewriter());
            PracticeSession nonKaderu = practiceSessionFixture();
            nonKaderu.setVenueId(6L); // 東区民センター(さくら) — KADERU 対象外
            stubPracticeSessionLookup(nonKaderu);

            assertThatThrownBy(() -> service.createSession(request(VenueId.KADERU), "SUPER_ADMIN", null))
                    .isInstanceOfSatisfying(VenueReservationProxyException.class, ex ->
                            assertThat(ex.getErrorCode())
                                    .isEqualTo(VenueReservationProxyException.INVALID_REQUEST));
            verify(sessionStore, never()).createSession(any(), any(), any(), any(), anyInt(), any());
        }

        private void stubPracticeSessionLookup(PracticeSession entity) {
            when(practiceSessionRepository.findById(PRACTICE_SESSION_ID)).thenReturn(Optional.of(entity));
        }

        private PracticeSession practiceSessionFixture() {
            return PracticeSession.builder()
                    .id(PRACTICE_SESSION_ID)
                    .organizationId(ORGANIZATION_ID)
                    .venueId(KADERU_VENUE_ID)
                    .sessionDate(SESSION_DATE)
                    .build();
        }
    }

    @Nested
    @DisplayName("view")
    class View {

        @Test
        @DisplayName("cachedTrayHtml を会場別 strategy で書き換えて text/html で返す")
        void rewritesCachedHtml() {
            VenueReservationHtmlRewriter rewriter = spy(new VenueReservationHtmlRewriter());
            VenueReservationProxyService service = newService(enabledConfig(), rewriter);
            ProxySession session = session();
            session.setCachedTrayHtml("<html>tray</html>");
            when(sessionStore.get(TOKEN)).thenReturn(Optional.of(session));
            doReturn("<html>rewritten</html>")
                    .when(rewriter).rewrite(
                            eq("<html>tray</html>"),
                            any(String.class),
                            eq(session),
                            eq(venueConfig),
                            eq(rewriteStrategy));

            ResponseEntity<String> response = service.view(TOKEN);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType())
                    .isEqualTo(new MediaType("text", "html", StandardCharsets.UTF_8));
            assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(response.getBody()).isEqualTo("<html>rewritten</html>");
            verify(sessionStore).touch(TOKEN);
        }
    }

    @Nested
    @DisplayName("fetch")
    class Fetch {

        @Test
        @DisplayName("会場サイトへ中継し、HTMLを書き換え、危険ヘッダ除去と完了ヘッダ注入を行う")
        void proxiesAndRewritesHtmlResponse() throws Exception {
            VenueReservationHtmlRewriter rewriter = spy(new VenueReservationHtmlRewriter());
            VenueReservationProxyService service = newService(enabledConfig(), rewriter);
            ProxySession session = session();
            when(sessionStore.get(TOKEN)).thenReturn(Optional.of(session));
            doReturn("<html>rewritten</html>")
                    .when(rewriter).rewrite(
                            eq("<html>done</html>"),
                            any(String.class),
                            eq(session),
                            eq(venueConfig),
                            eq(rewriteStrategy));

            CloseableHttpResponse upstreamResponse = htmlResponse("<html>done</html>");
            upstreamResponse.addHeader(HttpHeaders.SET_COOKIE, "PHPSESSID=secret");
            upstreamResponse.addHeader("X-Frame-Options", "SAMEORIGIN");
            upstreamResponse.addHeader(HttpHeaders.LOCATION, BASE_URL + "/complete");
            when(client.fetch(eq(session), any())).thenReturn(upstreamResponse);
            when(completionDetector.detectAndMarkComplete(
                    eq(session),
                    eq(BASE_URL + "/kaderu27/index.php?p=apply&x=1"),
                    eq(BASE_URL + "/complete"),
                    eq("<html>done</html>"))).thenReturn(true);

            MockHttpServletRequest request = new MockHttpServletRequest(
                    "POST",
                    "/api/venue-reservation-proxy/fetch/kaderu27/index.php");
            request.setQueryString("p=apply&token=" + TOKEN + "&x=1");
            request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            request.setContent("name=value".getBytes(StandardCharsets.UTF_8));
            request.addHeader("Host", "app.example.local");
            request.addHeader("X-Test", "yes");

            ResponseEntity<byte[]> response = service.fetch(TOKEN, request);

            ArgumentCaptor<HttpUriRequest> requestCaptor = ArgumentCaptor.forClass(HttpUriRequest.class);
            verify(client).fetch(eq(session), requestCaptor.capture());
            HttpUriRequest proxied = requestCaptor.getValue();
            assertThat(proxied.getMethod()).isEqualTo("POST");
            assertThat(proxied.getURI().toString())
                    .isEqualTo(BASE_URL + "/kaderu27/index.php?p=apply&x=1");
            assertThat(proxied.getFirstHeader("Host")).isNull();
            assertThat(proxied.getFirstHeader("X-Test").getValue()).isEqualTo("yes");
            assertThat(proxied).isInstanceOf(HttpEntityEnclosingRequest.class);
            String proxiedBody = EntityUtils.toString(
                    ((HttpEntityEnclosingRequest) proxied).getEntity(),
                    StandardCharsets.UTF_8);
            assertThat(proxiedBody).isEqualTo("name=value");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(new String(response.getBody(), StandardCharsets.UTF_8))
                    .isEqualTo("<html>rewritten</html>");
            assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();
            assertThat(response.getHeaders().getFirst("X-Frame-Options")).isNull();
            assertThat(response.getHeaders().getFirst("X-VRP-Completed")).isEqualTo("true");
            assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
            assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION))
                    .isEqualTo("/api/venue-reservation-proxy/fetch/complete?token=" + TOKEN);
            verify(sessionStore).touch(TOKEN);
        }

        @Test
        @DisplayName("非HTMLレスポンスは body を透過し、HTML rewriter を呼ばない")
        void nonHtmlPassThrough() {
            VenueReservationHtmlRewriter rewriter = spy(new VenueReservationHtmlRewriter());
            VenueReservationProxyService service = newService(enabledConfig(), rewriter);
            ProxySession session = session();
            when(sessionStore.get(TOKEN)).thenReturn(Optional.of(session));
            when(client.fetch(eq(session), any())).thenReturn(binaryResponse("img".getBytes(StandardCharsets.UTF_8)));
            when(completionDetector.detectAndMarkComplete(any(), any(), any(), any())).thenReturn(false);

            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET",
                    "/api/venue-reservation-proxy/fetch/assets/logo.png");
            request.setQueryString("token=" + TOKEN);

            ResponseEntity<byte[]> response = service.fetch(TOKEN, request);

            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(response.getBody()).isEqualTo("img".getBytes(StandardCharsets.UTF_8));
            verify(rewriter, never()).rewrite(any(), any(String.class), any(), any(), any());
            verify(rewriter, never()).rewrite(any(), any(ProxySession.class), any(), any());
        }
    }

    private VenueReservationProxyService newService(VenueReservationProxyConfig config,
                                                   VenueReservationHtmlRewriter rewriter) {
        when(client.venue()).thenReturn(VenueId.KADERU);
        return new VenueReservationProxyService(
                config,
                sessionStore,
                rewriter,
                completionDetector,
                practiceSessionRepository,
                List.of(client),
                List.of(venueConfig),
                List.of(rewriteStrategy));
    }

    private static CreateVenueProxySessionRequest request(VenueId venue) {
        CreateVenueProxySessionRequest request = new CreateVenueProxySessionRequest();
        request.setVenue(venue);
        request.setPracticeSessionId(PRACTICE_SESSION_ID);
        request.setRoomName("はまなす");
        request.setDate(SESSION_DATE);
        request.setSlotIndex(2);
        return request;
    }

    private static ProxySession session() {
        return ProxySession.builder()
                .token(TOKEN)
                .venue(VenueId.KADERU)
                .practiceSessionId(PRACTICE_SESSION_ID)
                .roomName("はまなす")
                .date(SESSION_DATE)
                .slotIndex(2)
                .build();
    }

    private static VenueReservationProxyConfig enabledConfig() {
        VenueReservationProxyConfig config = new VenueReservationProxyConfig();
        config.setEnabled(true);
        VenueReservationProxyConfig.VenueProperties kaderu = new VenueReservationProxyConfig.VenueProperties();
        kaderu.setEnabled(true);
        kaderu.setBaseUrl(BASE_URL);
        config.setVenues(Map.of("kaderu", kaderu));
        return config;
    }

    private static VenueReservationProxyConfig disabledHigashiConfig() {
        VenueReservationProxyConfig config = enabledConfig();
        VenueReservationProxyConfig.VenueProperties higashi = new VenueReservationProxyConfig.VenueProperties();
        higashi.setEnabled(false);
        config.setVenues(Map.of("kaderu", config.getVenues().get("kaderu"), "higashi", higashi));
        return config;
    }

    private static CloseableHttpResponse htmlResponse(String body) {
        BasicCloseableHttpResponse response = new BasicCloseableHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        response.setEntity(new StringEntity(
                body,
                ContentType.create("text/html", StandardCharsets.UTF_8)));
        response.addHeader(HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8");
        return response;
    }

    private static CloseableHttpResponse binaryResponse(byte[] body) {
        BasicCloseableHttpResponse response = new BasicCloseableHttpResponse(
                new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"));
        response.setEntity(new org.apache.http.entity.ByteArrayEntity(
                body,
                ContentType.create("image/png")));
        response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE);
        return response;
    }

    /** テスト用 {@link CloseableHttpResponse}。close() は no-op で leak チェックには十分。 */
    private static final class BasicCloseableHttpResponse extends BasicHttpResponse implements CloseableHttpResponse {
        BasicCloseableHttpResponse(StatusLine statusline) { super(statusline); }
        @Override public void close() { /* no-op */ }
    }
}
