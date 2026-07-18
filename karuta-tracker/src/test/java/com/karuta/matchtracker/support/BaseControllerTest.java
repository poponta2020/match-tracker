package com.karuta.matchtracker.support;

import com.karuta.matchtracker.service.AuthTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code @WebMvcTest} スライスの基底クラス（auth-tokenization）
 *
 * <p>{@code @WebMvcTest} は {@code HandlerInterceptor} 実装を Bean として読み込むため、
 * コントローラ単体テストでも認証インターセプタが動く。認証がトークンベースになった以降は
 * {@link AuthTokenService} が必要になるので、ここでモックを1つ用意して
 * 合成トークンを解決させる。
 *
 * <p>テストは {@code .header("Authorization", AuthTestSupport.bearer(1L, Role.SUPER_ADMIN))}
 * のように「誰として叩くか」だけを指定すればよい。
 */
public abstract class BaseControllerTest {

    @MockitoBean
    protected AuthTokenService authTokenService;

    @BeforeEach
    void stubAuthTokenService() {
        AuthTestSupport.stubVerify(authTokenService);
    }
}
