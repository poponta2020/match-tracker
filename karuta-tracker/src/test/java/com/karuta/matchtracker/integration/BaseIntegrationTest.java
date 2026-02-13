package com.karuta.matchtracker.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 統合テストの基底クラス
 *
 * - Testcontainersを使用してMySQLコンテナを起動
 * - Spring Boot全体をロード
 * - MockMvcを使用してHTTPリクエストをシミュレート
 * - 各テストでデータベースをクリーンアップ
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Container
    protected static final MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("karuta_tracker_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Testcontainersで起動したMySQLの接続情報をSpringに設定
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
    }

    /**
     * 各テストの前にデータベースをクリーンアップ
     */
    @BeforeEach
    void cleanDatabase() {
        // 外部キー制約を一時的に無効化
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        // 全テーブルのデータを削除
        jdbcTemplate.execute("TRUNCATE TABLE matches");
        jdbcTemplate.execute("TRUNCATE TABLE player_profiles");
        jdbcTemplate.execute("TRUNCATE TABLE practice_sessions");
        jdbcTemplate.execute("TRUNCATE TABLE match_pairings");
        jdbcTemplate.execute("TRUNCATE TABLE venue_match_schedules");
        jdbcTemplate.execute("TRUNCATE TABLE venues");
        jdbcTemplate.execute("TRUNCATE TABLE players");

        // 外部キー制約を再有効化
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}
