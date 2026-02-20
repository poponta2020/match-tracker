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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 統合テストの基底クラス
 *
 * - Testcontainersを使用してPostgreSQLコンテナを起動
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
    protected static final PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("karuta_tracker_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * Testcontainersで起動したPostgreSQLの接続情報をSpringに設定
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    /**
     * 各テストの前にデータベースをクリーンアップ
     * PostgreSQLではTRUNCATE ... CASCADEで外部キー制約を考慮しつつ削除
     */
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE matches, player_profiles, practice_participants, practice_sessions, " +
            "match_pairings, venue_match_schedules, venues, players RESTART IDENTITY CASCADE"
        );
    }
}
