package com.karuta.matchtracker.integration;

import com.karuta.matchtracker.config.TestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdjacentRoomServiceのトランザクション境界を通る統合テスト（実DB検証）
 *
 * room_availability_cache テーブルを一時的にリネームして実際のDataAccessExceptionを発生させ、
 * PracticeSessionService(@Transactional readOnly=true) →
 *   AdjacentRoomService(@Transactional propagation=NOT_SUPPORTED)
 * のAOP境界を通る呼び出しで、外側トランザクションに影響しないことを検証する。
 *
 * NOT_SUPPORTEDを外すとinner DataAccessExceptionがouter transactionをrollback-onlyに
 * マークするため500となり、このテストが失敗する。
 *
 * ※ テーブルリネームはトランザクション外で即コミットされる必要があるため、
 *   BaseIntegrationTestの@Transactionalを継承せず独自にセットアップする。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("隣室空き状況 統合テスト（トランザクション境界検証）")
class AdjacentRoomIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE matches, player_profiles, practice_participants, practice_sessions, " +
            "match_pairings, venue_match_schedules, venues, player_organizations, players, organizations RESTART IDENTITY CASCADE"
        );
        jdbcTemplate.execute(
            "INSERT INTO organizations (id, code, name, color, deadline_type, created_at, updated_at) " +
            "VALUES (1, 'wasura', 'わすらもち会', '#16a34a', 'SAME_DAY', NOW(), NOW()) " +
            "ON CONFLICT (id) DO NOTHING"
        );
    }

    @AfterEach
    void tearDown() {
        // テーブルがリネームされていた場合は復元する
        try {
            jdbcTemplate.execute("ALTER TABLE room_availability_cache_bak RENAME TO room_availability_cache");
        } catch (Exception ignored) {
            // リネームされていなかった場合は無視
        }
        jdbcTemplate.execute(
            "TRUNCATE TABLE practice_sessions, venues, organizations RESTART IDENTITY CASCADE"
        );
    }

    @Test
    @DisplayName("実DBでキャッシュテーブルが存在しない場合、APIは200で応答しステータスは「不明」")
    void getSessionDetail_whenCacheTableMissing_returnsOkWithUnknownStatus() throws Exception {
        // Venue（かでる和室 id=3）を準備
        jdbcTemplate.execute(
                "INSERT INTO venues (id, name, default_match_count, capacity, created_at, updated_at) " +
                "VALUES (3, 'すずらん', 7, 14, NOW(), NOW()) ON CONFLICT (id) DO NOTHING");

        // 練習セッションを直接DBに投入
        jdbcTemplate.execute(
                "INSERT INTO practice_sessions " +
                "(session_date, total_matches, venue_id, organization_id, created_by, updated_by, created_at, updated_at) " +
                "VALUES ('2026-05-01', 7, 3, 1, 1, 1, NOW(), NOW())");
        Long sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM practice_sessions WHERE session_date = '2026-05-01'", Long.class);

        // room_availability_cache テーブルをリネームして実DBエラーを発生させる
        jdbcTemplate.execute("ALTER TABLE room_availability_cache RENAME TO room_availability_cache_bak");

        // 練習日詳細APIを呼び出し
        // @Transactional(propagation=NOT_SUPPORTED) が有効なら:
        //   inner query は outer transaction の外で実行 → DataAccessException はキャッチされ "不明" で応答
        // NOT_SUPPORTED を外すと:
        //   inner DataAccessException が PostgreSQL の outer transaction を abort 状態にし 500 エラーになる
        mockMvc.perform(get("/api/practice-sessions/" + sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId))
                .andExpect(jsonPath("$.adjacentRoomStatus").exists())
                .andExpect(jsonPath("$.adjacentRoomStatus.status").value("不明"))
                .andExpect(jsonPath("$.adjacentRoomStatus.available").value(false));
    }
}
