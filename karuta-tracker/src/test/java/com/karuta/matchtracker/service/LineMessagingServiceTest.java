package com.karuta.matchtracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * LineMessagingService#getGroupMemberCount の単体テスト（AC-4/AC-5 の枠会計に使うグループ人数取得）。
 * syncRestTemplate を MockRestServiceServer で差し替え、パース・失敗時 -1 を検証する。
 */
@DisplayName("LineMessagingService グループ人数取得テスト")
class LineMessagingServiceTest {

    private LineMessagingService service;
    private MockRestServiceServer server;

    private static final String URL = "https://api.line.me/v2/bot/group/G123/members/count";

    @BeforeEach
    void setUp() {
        service = new LineMessagingService(new RestTemplateBuilder());
        RestTemplate syncRestTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "syncRestTemplate");
        server = MockRestServiceServer.createServer(syncRestTemplate);
    }

    @Test
    @DisplayName("{\"count\": N} を数値としてパースする")
    void parsesCount() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"count\": 68}", MediaType.APPLICATION_JSON));

        assertThat(service.getGroupMemberCount("token", "G123")).isEqualTo(68);
        server.verify();
    }

    @Test
    @DisplayName("count フィールドが無ければ -1")
    void missingCountFieldReturnsMinusOne() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(service.getGroupMemberCount("token", "G123")).isEqualTo(-1);
    }

    @Test
    @DisplayName("APIエラー時は -1")
    void serverErrorReturnsMinusOne() {
        server.expect(requestTo(URL))
                .andRespond(withServerError());

        assertThat(service.getGroupMemberCount("token", "G123")).isEqualTo(-1);
    }
}
