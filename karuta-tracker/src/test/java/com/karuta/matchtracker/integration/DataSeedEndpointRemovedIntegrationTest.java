package com.karuta.matchtracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 未認証で全データを破壊できたシードエンドポイントが存在しないことの回帰テスト（Issue #1103）
 *
 * 旧 DataSeedController は @RequireRole も @Profile も持たず、以下を未認証で実行できた。
 * - POST /api/seed/all : 全対戦記録・全練習日程を deleteAll し、全選手のパスワードを "pppppppp" に上書き
 * - POST /api/seed/venue-schedules : 全会場の試合時間割を deleteByVenueId で消去
 *
 * RoleCheckInterceptor はロールを X-User-Role ヘッダーの自己申告でしか判定しないため、
 * @RequireRole の付与では塞げない（ヘッダーを詐称すれば通る）。唯一の対策は
 * エンドポイントを存在させないことなので、コントローラごと削除した。
 *
 * ＜アサーションの方針＞
 * 主検証は「破壊的処理が実行されていないこと（データが無傷であること）」。
 * ステータスコードは補助的にしか見ない。旧 /api/seed/all は処理途中で例外が出ても
 * try/catch で 500 を返す実装だったため、「500 だから未実行」とは言えず、
 * ステータスでは "削除済み" と "実行された上で失敗" を区別できないため。
 * （なお現状、存在しないルートは NoResourceFoundException が
 *   GlobalExceptionHandler の Exception ハンドラに拾われて 500 になる。
 *   これは本 PR の対象外の既存の欠陥なので、4xx/5xx を問わず「成功しないこと」だけを見る）
 */
@DisplayName("シードエンドポイント削除の回帰テスト（Issue #1103）")
class DataSeedEndpointRemovedIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PracticeSessionRepository practiceSessionRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private VenueMatchScheduleRepository venueMatchScheduleRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private static final String KNOWN_PASSWORD = "password123";
    private static final String PLAYER_NAME = "被害者選手";

    private Long venueId;

    @BeforeEach
    void setUpTestData() throws Exception {
        PlayerCreateRequest playerRequest = PlayerCreateRequest.builder()
                .name(PLAYER_NAME)
                .password(KNOWN_PASSWORD)
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .build();

        mockMvc.perform(post("/api/players")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(playerRequest)))
                .andExpect(status().isCreated());

        PracticeSessionCreateRequest sessionRequest = PracticeSessionCreateRequest.builder()
                .sessionDate(LocalDate.now())
                .totalMatches(3)
                .organizationId(1L)
                .build();

        mockMvc.perform(post("/api/practice-sessions")
                        .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sessionRequest)))
                .andExpect(status().isCreated());

        // 旧 /api/seed/venue-schedules は全会場の時間割を消してから
        // 「中央区民センター」「クラーク会館」の分だけ作り直すため、
        // それ以外の名前の会場の時間割は消えたままになる。
        jdbcTemplate.update(
                "INSERT INTO venues (name, default_match_count, created_at, updated_at) "
                        + "VALUES (?, ?, NOW(), NOW())",
                "テスト会場", 1);
        venueId = jdbcTemplate.queryForObject(
                "SELECT id FROM venues WHERE name = ?", Long.class, "テスト会場");
        jdbcTemplate.update(
                "INSERT INTO venue_match_schedules (venue_id, match_number, start_time, end_time) "
                        + "VALUES (?, ?, ?, ?)",
                venueId, 1, java.sql.Time.valueOf("19:00:00"), java.sql.Time.valueOf("21:00:00"));
    }

    @Test
    @DisplayName("認証ヘッダー無しの POST /api/seed/all は成功せず、対戦記録・練習日程・パスワードが破壊されない")
    void seedAllIsNotReachableWithoutAuthentication() throws Exception {
        int statusCode = performAndGetStatus(post("/api/seed/all"));

        assertPracticeDataIntact();
        assertNotSuccessful(statusCode);
    }

    @Test
    @DisplayName("SUPER_ADMIN を詐称したヘッダー付きでも POST /api/seed/all は破壊的処理を実行しない")
    void seedAllIsNotReachableEvenWithSpoofedAdminHeaders() throws Exception {
        int statusCode = performAndGetStatus(post("/api/seed/all")
                .header("X-User-Role", "SUPER_ADMIN").header("X-User-Id", "1"));

        assertPracticeDataIntact();
        assertNotSuccessful(statusCode);
    }

    @Test
    @DisplayName("認証ヘッダー無しの POST /api/seed/venue-schedules は会場の試合時間割を破壊しない")
    void seedVenueSchedulesIsNotReachableWithoutAuthentication() throws Exception {
        int statusCode = performAndGetStatus(post("/api/seed/venue-schedules"));

        // jdbcTemplate ではなく JPA 経由で数える。
        // JPA の削除は永続化コンテキストに溜まってからフラッシュされるため、
        // 素の SQL で数えると「まだ消えていない」ように見えて検証が空振りする。
        int scheduleCount = venueMatchScheduleRepository
                .findByVenueIdOrderByMatchNumberAsc(venueId).size();
        assertThat(scheduleCount)
                .as("旧 seed/venue-schedules が実行されると、この会場の時間割は削除されたままになる")
                .isEqualTo(1);
        assertNotSuccessful(statusCode);
    }

    private int performAndGetStatus(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    /**
     * ルートが解決されないことの補助検証。
     * 存在しないルートの具体的なステータス（現状 500・本来 404）に依存させたくないため、
     * 「成功レスポンスではない」ことだけを見る。
     * 主検証（データが無傷であること）を先に評価させたいので、必ず後段で呼ぶ。
     */
    private void assertNotSuccessful(int statusCode) {
        assertThat(statusCode)
                .as("シードエンドポイントは存在しないため、成功レスポンスを返してはならない")
                .isGreaterThanOrEqualTo(400);
    }

    /**
     * 旧 /api/seed/all が実行されていれば練習日程は消え、パスワードは "pppppppp" に書き換わる。
     */
    private void assertPracticeDataIntact() {
        long sessionCount = practiceSessionRepository.count();
        assertThat(sessionCount)
                .as("旧 seed/all が実行されると practice_sessions は deleteAll される")
                .isEqualTo(1);

        String storedPassword = playerRepository.findByName(PLAYER_NAME)
                .map(com.karuta.matchtracker.entity.Player::getPassword)
                .orElse(null);
        // パスワードは BCrypt ハッシュで保存されるため平文比較はできない。
        // 「登録時のパスワードのままであること」を encoder 経由で確認する（auth-tokenization）
        assertThat(storedPassword).isNotNull();
        assertThat(passwordEncoder.matches(KNOWN_PASSWORD, storedPassword))
                .as("旧 seed/all が実行されると全選手のパスワードが pppppppp に上書きされる")
                .isTrue();
    }
}
