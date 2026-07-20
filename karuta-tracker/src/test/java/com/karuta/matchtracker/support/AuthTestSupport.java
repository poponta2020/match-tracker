package com.karuta.matchtracker.support;

import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.AuthTokenService;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

/**
 * テストで認証済みリクエストを組み立てるためのヘルパー（auth-tokenization）
 *
 * <p>本番の認証はサーバ発行トークンだけを根拠にするため、テストからは
 * {@code X-User-Role} / {@code X-User-Id} ヘッダーで主体を指定できなくなった。
 * 代わりに「主体を符号化した合成トークン」を {@code Authorization: Bearer} に載せ、
 * モックした {@link AuthTokenService} がそれを解決する。
 *
 * <p>これにより、テストが指定したいのは<b>誰として叩くか</b>だけ、という関心は
 * 従来と変わらないまま、本番コードのヘッダー参照を完全に廃止できる。
 *
 * <p>実トークンの発行・検証そのものは {@code AuthTokenServiceTest} と
 * {@code AuthPasswordIntegrationTest}（実 DB・実サービス）が検証する。
 */
public final class AuthTestSupport {

    /** 合成トークンの接頭辞。{@code test-token:<playerId>:<ROLE>:<adminOrgId|->} 形式 */
    private static final String TOKEN_PREFIX = "test-token:";

    private static final String NO_ADMIN_ORG = "-";

    private AuthTestSupport() {
    }

    /**
     * 指定の選手・ロールとして認証されるための Authorization ヘッダー値を組み立てる
     *
     * @param playerId 主体の選手ID
     * @param role     主体のロール
     * @return {@code "Bearer <合成トークン>"}
     */
    public static String bearer(long playerId, Role role) {
        return bearer(playerId, role, null);
    }

    /**
     * ADMIN の団体スコープ付きで Authorization ヘッダー値を組み立てる
     *
     * @param playerId       主体の選手ID
     * @param role           主体のロール
     * @param adminOrganizationId ADMIN の管轄団体ID（不要なら null）
     * @return {@code "Bearer <合成トークン>"}
     */
    public static String bearer(long playerId, Role role, Long adminOrganizationId) {
        return "Bearer " + TOKEN_PREFIX + playerId + ":" + role.name() + ":"
                + (adminOrganizationId == null ? NO_ADMIN_ORG : adminOrganizationId);
    }

    /**
     * 合成トークンから主体を復元する。モックした {@link AuthTokenService#verify} の実装として使う。
     *
     * @param rawToken 生トークン
     * @return 合成トークンなら対応する選手。それ以外（未認証・不正トークン）は empty
     */
    public static Optional<Player> resolve(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }

        String[] parts = rawToken.substring(TOKEN_PREFIX.length()).split(":");
        if (parts.length != 3) {
            return Optional.empty();
        }

        return Optional.of(Player.builder()
                .id(Long.parseLong(parts[0]))
                .role(Role.valueOf(parts[1]))
                .adminOrganizationId(NO_ADMIN_ORG.equals(parts[2]) ? null : Long.parseLong(parts[2]))
                .build());
    }

    /**
     * モックした {@link AuthTokenService} に、合成トークンを解決する挙動を仕込む。
     *
     * <p>合成トークン以外（トークン無し・でたらめな文字列）は empty を返すため、
     * 「未認証なら 401」の検証はそのまま成立する。
     *
     * @param authTokenService モック化された AuthTokenService
     */
    public static void stubVerify(AuthTokenService authTokenService) {
        // nullable: 未認証リクエストではトークンが null のまま verify に渡るため、
        // null も含めてマッチさせる（anyString() だと null がマッチせず素の mock が null を返す）
        when(authTokenService.verify(nullable(String.class)))
                .thenAnswer(invocation -> resolve(invocation.getArgument(0)));
    }
}
