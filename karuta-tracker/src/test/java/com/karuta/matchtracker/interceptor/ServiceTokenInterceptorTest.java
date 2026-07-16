package com.karuta.matchtracker.interceptor;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ServiceTokenInterceptor の単体テスト（AC-2: トークン無し=401・不正=403・正当=通過・env未設定=全拒否）。
 */
@DisplayName("ServiceTokenInterceptor")
class ServiceTokenInterceptorTest {

    private static final String TOKEN = "s3cr3t-worker-token-value";

    private MockHttpServletRequest req() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRequestURI("/api/line-chat-worker/tasks");
        return r;
    }

    @Test
    @DisplayName("トークンヘッダ無し → 401 で拒否")
    void missingTokenReturns401() {
        ServiceTokenInterceptor interceptor = new ServiceTokenInterceptor(TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req(), res, new Object());

        assertThat(ok).isFalse();
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("不正なトークン → 403 で拒否")
    void wrongTokenReturns403() {
        ServiceTokenInterceptor interceptor = new ServiceTokenInterceptor(TOKEN);
        MockHttpServletRequest req = req();
        req.addHeader("X-Service-Token", "wrong-token");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, new Object());

        assertThat(ok).isFalse();
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("正当なトークン → 通過")
    void correctTokenPasses() {
        ServiceTokenInterceptor interceptor = new ServiceTokenInterceptor(TOKEN);
        MockHttpServletRequest req = req();
        req.addHeader("X-Service-Token", TOKEN);
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, new Object());

        assertThat(ok).isTrue();
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("env 未設定（空） → 正しいヘッダでも全拒否（401）")
    void unsetEnvRejectsAll() {
        ServiceTokenInterceptor interceptor = new ServiceTokenInterceptor("");
        MockHttpServletRequest req = req();
        req.addHeader("X-Service-Token", "anything");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, res, new Object());

        assertThat(ok).isFalse();
        assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
