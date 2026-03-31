package com.karuta.matchtracker.integration;

import com.karuta.matchtracker.config.TestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 統合テストの基底クラス
 *
 * - TestContainersConfigを使用してPostgreSQLコンテナを起動
 * - Spring Boot全体をロード
 * - MockMvcを使用してHTTPリクエストをシミュレート
 * - 各テストでデータベースをクリーンアップ
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * 各テストの前にデータベースをクリーンアップ
     * PostgreSQLではTRUNCATE ... CASCADEで外部キー制約を考慮しつつ削除
     */
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE matches, player_profiles, practice_participants, practice_sessions, " +
            "match_pairings, venue_match_schedules, venues, player_organizations, players, organizations RESTART IDENTITY CASCADE"
        );
        // テスト用の団体データを挿入
        jdbcTemplate.execute(
            "INSERT INTO organizations (id, code, name, color, deadline_type, created_at, updated_at) " +
            "VALUES (1, 'wasura', 'わすらもち会', '#16a34a', 'SAME_DAY', NOW(), NOW()) " +
            "ON CONFLICT (id) DO NOTHING"
        );
        jdbcTemplate.execute(
            "INSERT INTO organizations (id, code, name, color, deadline_type, created_at, updated_at) " +
            "VALUES (2, 'hokudai', '北海道大学かるた会', '#ef4444', 'MONTHLY', NOW(), NOW()) " +
            "ON CONFLICT (id) DO NOTHING"
        );
    }
}
