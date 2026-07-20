package com.karuta.matchtracker.integration;

import com.karuta.matchtracker.service.AuthTokenService;
import com.karuta.matchtracker.support.AuthTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 認証済みリクエストを投げる統合テストの基底クラス（auth-tokenization）
 *
 * <p>{@link AuthTokenService} をモックし、{@link AuthTestSupport} の合成トークンで
 * 「誰として叩くか」を指定できるようにする。トークンの発行・検証・失効そのものは
 * {@link AuthPasswordIntegrationTest}（実サービス・実 DB）が検証するため、
 * ここでモックしても認証機構の検証が失われることはない。
 *
 * <p>トークンを付けない（あるいは不正なトークンの）リクエストは従来どおり
 * empty に解決されるため、「未認証なら 401」の検証もこの基底クラス上で成立する。
 */
public abstract class BaseAuthenticatedIntegrationTest extends BaseIntegrationTest {

    @MockitoBean
    protected AuthTokenService authTokenService;

    @BeforeEach
    void stubAuthTokenService() {
        AuthTestSupport.stubVerify(authTokenService);
    }

    /**
     * 指定団体に所属する実在の選手を作成し、その選手IDを返す。
     *
     * <p>所属団体でスコープされるエンドポイント（練習日の年月検索など）では、
     * 「認証は通るが DB に居ない合成主体」だと所属が空になり結果が 0 件になる。
     * そうした検証では、この helper で作った実在の選手として
     * {@code AuthTestSupport.bearer(id, role)} を組み立てること。
     *
     * @param name           選手名（テスト内で一意にすること）
     * @param organizationId 所属させる団体ID
     * @return 作成した選手のID
     */
    protected Long createMemberPlayer(String name, Long organizationId) {
        jdbcTemplate.update(
                "INSERT INTO players (name, password, gender, dominant_hand, role, ical_feed_token, "
                        + "require_password_change, created_at, updated_at) "
                        + "VALUES (?, ?, '男性', '右', 'PLAYER', ?, false, NOW(), NOW())",
                name, "$2a$10$notusedinthistest", "feed-" + name);
        Long playerId = jdbcTemplate.queryForObject(
                "SELECT id FROM players WHERE name = ?", Long.class, name);
        jdbcTemplate.update(
                "INSERT INTO player_organizations (player_id, organization_id, created_at) "
                        + "VALUES (?, ?, NOW())",
                playerId, organizationId);
        return playerId;
    }
}
