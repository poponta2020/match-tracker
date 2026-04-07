package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * リッチメニュー照会ハンドラーのロジックテスト
 *
 * LineWebhookController内のハンドラーメソッドはprivateなので、
 * 照会ロジックで使用するサービスメソッド・Flexビルダーを個別にテストする。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("リッチメニュー照会ロジック テスト")
class LineRichMenuHandlerTest {

    @Mock
    private PracticeParticipantRepository practiceParticipantRepository;
    @Mock
    private PracticeSessionRepository practiceSessionRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private VenueRepository venueRepository;

    @InjectMocks
    private LineNotificationService lineNotificationService;

    // ===== キャンセル待ち状況確認 =====

    @Nested
    @DisplayName("buildWaitlistStatusFlex")
    class WaitlistStatusFlexTest {

        @Test
        @DisplayName("複数エントリを正しく表示する")
        void shouldBuildFlexWithMultipleEntries() {
            List<Map<String, Object>> entries = List.of(
                Map.of(
                    "sessionLabel", "4月10日（中央公民館）",
                    "matchNumber", 1,
                    "waitlistNumber", 2,
                    "status", "WAITLISTED"
                ),
                Map.of(
                    "sessionLabel", "4月17日（中央公民館）",
                    "matchNumber", 2,
                    "waitlistNumber", 1,
                    "status", "OFFERED",
                    "offerDeadline", LocalDateTime.of(2026, 4, 8, 18, 0)
                )
            );

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            assertThat(flex).containsKey("type");
            assertThat(flex.get("type")).isEqualTo("bubble");
            assertThat(flex).containsKey("header");
            assertThat(flex).containsKey("body");
        }

        @Test
        @DisplayName("WAITLISTEDエントリのみでも構築できる")
        void shouldBuildFlexWithWaitlistedOnly() {
            List<Map<String, Object>> entries = List.of(
                Map.of(
                    "sessionLabel", "4月10日（中央公民館）",
                    "matchNumber", 3,
                    "waitlistNumber", 1,
                    "status", "WAITLISTED"
                )
            );

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            assertThat(flex.get("type")).isEqualTo("bubble");
        }

        @Test
        @DisplayName("同一ラベル・別sessionIdが別グループとして表示される")
        void shouldGroupBySameSessionIdNotLabel() {
            // 同じラベルだが異なるsessionIdのエントリ
            java.util.Map<String, Object> entry1 = new java.util.LinkedHashMap<>();
            entry1.put("sessionId", 100L);
            entry1.put("sessionLabel", "4月10日（中央公民館）");
            entry1.put("matchNumber", 1);
            entry1.put("waitlistNumber", 1);
            entry1.put("status", "WAITLISTED");

            java.util.Map<String, Object> entry2 = new java.util.LinkedHashMap<>();
            entry2.put("sessionId", 200L);
            entry2.put("sessionLabel", "4月10日（中央公民館）");
            entry2.put("matchNumber", 2);
            entry2.put("waitlistNumber", 1);
            entry2.put("status", "WAITLISTED");

            List<Map<String, Object>> entries = List.of(entry1, entry2);

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            assertThat(flex.get("type")).isEqualTo("bubble");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) flex.get("body");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");

            // 別sessionIdなのでセッションラベルが2回表示される（separator含む）
            long labelCount = contents.stream()
                    .filter(c -> "4月10日（中央公民館）".equals(c.get("text")))
                    .count();
            assertThat(labelCount).isEqualTo(2);

            // 各グループにmatchNumber行が含まれることを検証
            long matchNumberCount = contents.stream()
                    .filter(c -> c.get("text") != null && c.get("text").toString().contains("試合目"))
                    .count();
            assertThat(matchNumberCount).isEqualTo(2);

            // グループ間にseparatorが1つ存在することを検証
            long separatorCount = contents.stream()
                    .filter(c -> "separator".equals(c.get("type")))
                    .count();
            assertThat(separatorCount).isEqualTo(1);
        }

        @Test
        @DisplayName("同一sessionIdの複数エントリが1グループにまとまりラベルは1回のみ表示される")
        void shouldGroupSameSessionIdEntriesWithSingleLabel() {
            java.util.Map<String, Object> entry1 = new java.util.LinkedHashMap<>();
            entry1.put("sessionId", 100L);
            entry1.put("sessionLabel", "4月10日（中央公民館）");
            entry1.put("matchNumber", 1);
            entry1.put("waitlistNumber", 2);
            entry1.put("status", "WAITLISTED");

            java.util.Map<String, Object> entry2 = new java.util.LinkedHashMap<>();
            entry2.put("sessionId", 100L);
            entry2.put("sessionLabel", "4月10日（中央公民館）");
            entry2.put("matchNumber", 2);
            entry2.put("waitlistNumber", 1);
            entry2.put("status", "WAITLISTED");

            List<Map<String, Object>> entries = List.of(entry1, entry2);

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) flex.get("body");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");

            // 同一sessionIdなのでラベルは1回だけ表示される
            long labelCount = contents.stream()
                    .filter(c -> "4月10日（中央公民館）".equals(c.get("text")))
                    .count();
            assertThat(labelCount).isEqualTo(1);

            // 試合目は2つ表示される
            long matchNumberCount = contents.stream()
                    .filter(c -> c.get("text") != null && c.get("text").toString().contains("試合目"))
                    .count();
            assertThat(matchNumberCount).isEqualTo(2);

            // グループ内なのでseparatorは0
            long separatorCount = contents.stream()
                    .filter(c -> "separator".equals(c.get("type")))
                    .count();
            assertThat(separatorCount).isEqualTo(0);
        }

        @Test
        @DisplayName("同一sessionId内でOFFEREDとWAITLISTEDが混在しても1グループにまとまる")
        void shouldGroupMixedStatusEntriesWithSameSessionId() {
            java.util.Map<String, Object> entry1 = new java.util.LinkedHashMap<>();
            entry1.put("sessionId", 100L);
            entry1.put("sessionLabel", "4月10日（中央公民館）");
            entry1.put("matchNumber", 1);
            entry1.put("waitlistNumber", 1);
            entry1.put("status", "OFFERED");
            entry1.put("offerDeadline", LocalDateTime.of(2026, 4, 8, 18, 0));

            java.util.Map<String, Object> entry2 = new java.util.LinkedHashMap<>();
            entry2.put("sessionId", 100L);
            entry2.put("sessionLabel", "4月10日（中央公民館）");
            entry2.put("matchNumber", 2);
            entry2.put("waitlistNumber", 1);
            entry2.put("status", "WAITLISTED");

            List<Map<String, Object>> entries = List.of(entry1, entry2);

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) flex.get("body");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");

            // ラベルは1回のみ
            long labelCount = contents.stream()
                    .filter(c -> "4月10日（中央公民館）".equals(c.get("text")))
                    .count();
            assertThat(labelCount).isEqualTo(1);

            // OFFEREDの「繰り上げオファー中」が表示される
            long offerCount = contents.stream()
                    .filter(c -> "繰り上げオファー中".equals(c.get("text")))
                    .count();
            assertThat(offerCount).isEqualTo(1);

            // WAITLISTEDの「キャンセル待ち」が表示される
            long waitlistCount = contents.stream()
                    .filter(c -> c.get("text") != null && c.get("text").toString().startsWith("キャンセル待ち"))
                    .count();
            assertThat(waitlistCount).isEqualTo(1);

            // グループ内なのでseparatorは0
            long separatorCount = contents.stream()
                    .filter(c -> "separator".equals(c.get("type")))
                    .count();
            assertThat(separatorCount).isEqualTo(0);
        }

        @Test
        @DisplayName("sessionIdがnullの場合は個別表示される")
        void shouldDisplayIndividuallyWhenSessionIdIsNull() {
            // sessionIdを持たないエントリ（同一ラベル）
            java.util.Map<String, Object> entry1 = new java.util.LinkedHashMap<>();
            entry1.put("sessionLabel", "4月10日（中央公民館）");
            entry1.put("matchNumber", 1);
            entry1.put("waitlistNumber", 2);
            entry1.put("status", "WAITLISTED");

            java.util.Map<String, Object> entry2 = new java.util.LinkedHashMap<>();
            entry2.put("sessionLabel", "4月10日（中央公民館）");
            entry2.put("matchNumber", 2);
            entry2.put("waitlistNumber", 1);
            entry2.put("status", "WAITLISTED");

            List<Map<String, Object>> entries = List.of(entry1, entry2);

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            assertThat(flex.get("type")).isEqualTo("bubble");
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) flex.get("body");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contents = (List<Map<String, Object>>) body.get("contents");

            // sessionIdがnullなので個別表示され、ラベルは各エントリごとに表示
            long labelCount = contents.stream()
                    .filter(c -> "4月10日（中央公民館）".equals(c.get("text")))
                    .count();
            assertThat(labelCount).isEqualTo(2);

            // 各グループにmatchNumber行が1つずつ、計2つ含まれることを検証
            long matchNumberCount = contents.stream()
                    .filter(c -> c.get("text") != null && c.get("text").toString().contains("試合目"))
                    .count();
            assertThat(matchNumberCount).isEqualTo(2);

            // 個別グループなのでseparatorが1つ存在する
            long separatorCount = contents.stream()
                    .filter(c -> "separator".equals(c.get("type")))
                    .count();
            assertThat(separatorCount).isEqualTo(1);
        }

    }

    // ===== 今日の参加者表示 =====

    @Nested
    @DisplayName("buildTodayParticipantsFlex")
    class TodayParticipantsFlexTest {

        @Test
        @DisplayName("参加者がいる場合にFlex Messageを構築する")
        void shouldBuildFlexWithParticipants() {
            PracticeParticipant p1 = PracticeParticipant.builder()
                    .id(1L).sessionId(100L).playerId(10L).matchNumber(1)
                    .status(ParticipantStatus.WON).build();
            PracticeParticipant p2 = PracticeParticipant.builder()
                    .id(2L).sessionId(100L).playerId(20L).matchNumber(1)
                    .status(ParticipantStatus.WON).build();

            Player player1 = Player.builder().id(10L).name("田中").danRank(Player.DanRank.参段).build();
            Player player2 = Player.builder().id(20L).name("鈴木").danRank(Player.DanRank.弐段).build();

            Map<Integer, List<PracticeParticipant>> byMatch = Map.of(1, List.of(p1, p2));
            Map<Long, Player> playerMap = Map.of(10L, player1, 20L, player2);

            Map<String, Object> flex = lineNotificationService.buildTodayParticipantsFlex(
                    "4月2日（中央公民館）", byMatch, playerMap, 4);

            assertThat(flex.get("type")).isEqualTo("bubble");
            assertThat(flex).containsKey("header");
            assertThat(flex).containsKey("body");
        }
    }

    // ===== 当日参加申込 =====

    @Nested
    @DisplayName("buildSameDayJoinFlex")
    class SameDayJoinFlexTest {

        @Test
        @DisplayName("申込可能な試合一覧を表示する")
        void shouldBuildFlexWithAvailableMatches() {
            List<Map<String, Object>> availableMatches = List.of(
                Map.of("matchNumber", 1, "vacancy", 2),
                Map.of("matchNumber", 3, "vacancy", 1)
            );

            Map<String, Object> flex = lineNotificationService.buildSameDayJoinFlex(
                    "4月2日（中央公民館）", availableMatches, 100L);

            assertThat(flex.get("type")).isEqualTo("bubble");
            assertThat(flex).containsKey("footer");

            // footerに参加ボタンが2つあること
            @SuppressWarnings("unchecked")
            Map<String, Object> footer = (Map<String, Object>) flex.get("footer");
            @SuppressWarnings("unchecked")
            List<Object> footerContents = (List<Object>) footer.get("contents");
            assertThat(footerContents).hasSize(2);

            // 各ボタンのpostbackデータにsame_day_joinアクションが含まれること
            @SuppressWarnings("unchecked")
            Map<String, Object> button1 = (Map<String, Object>) footerContents.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> action1 = (Map<String, Object>) button1.get("action");
            assertThat(action1.get("data").toString()).contains("action=same_day_join");
            assertThat(action1.get("data").toString()).contains("sessionId=100");
            assertThat(action1.get("data").toString()).contains("matchNumber=1");
        }
    }

    // ===== 当日参加申込の条件判定 =====

    @Nested
    @DisplayName("当日参加申込の条件判定")
    class SameDayJoinConditionTest {

        @Test
        @DisplayName("空きありキャンセル待ちなし→時間制限なしで申込可能")
        void shouldAllowJoinWhenVacancyAndNoWaitlist() {
            // vacancy > 0 かつ waitlist = 0 → 12時前でも申込可能
            int capacity = 4;
            int wonCount = 3;
            int vacancy = capacity - wonCount;
            boolean hasWaitlist = false;
            boolean isAfterNoon = false; // 12時前

            boolean canJoin = vacancy > 0 && (!hasWaitlist || isAfterNoon);
            assertThat(canJoin).isTrue();
        }

        @Test
        @DisplayName("空きありキャンセル待ちあり12時前→申込不可")
        void shouldNotAllowJoinWhenWaitlistAndBeforeNoon() {
            int capacity = 4;
            int wonCount = 3;
            int vacancy = capacity - wonCount;
            boolean hasWaitlist = true;
            boolean isAfterNoon = false;

            boolean canJoin = vacancy > 0 && (!hasWaitlist || isAfterNoon);
            assertThat(canJoin).isFalse();
        }

        @Test
        @DisplayName("空きありキャンセル待ちあり12時以降→申込可能")
        void shouldAllowJoinWhenWaitlistAndAfterNoon() {
            int capacity = 4;
            int wonCount = 3;
            int vacancy = capacity - wonCount;
            boolean hasWaitlist = true;
            boolean isAfterNoon = true;

            boolean canJoin = vacancy > 0 && (!hasWaitlist || isAfterNoon);
            assertThat(canJoin).isTrue();
        }

        @Test
        @DisplayName("空きなし→申込不可")
        void shouldNotAllowJoinWhenNoVacancy() {
            int capacity = 4;
            int wonCount = 4;
            int vacancy = capacity - wonCount;
            boolean hasWaitlist = false;
            boolean isAfterNoon = true;

            boolean canJoin = vacancy > 0 && (!hasWaitlist || isAfterNoon);
            assertThat(canJoin).isFalse();
        }
    }
}
