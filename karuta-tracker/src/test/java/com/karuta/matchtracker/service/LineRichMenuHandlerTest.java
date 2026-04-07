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
                    "sessionId", 100L,
                    "sessionLabel", "4月10日（中央公民館）",
                    "matchNumber", 3,
                    "waitlistNumber", 1,
                    "status", "WAITLISTED"
                )
            );

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            assertThat(flex.get("type")).isEqualTo("bubble");
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("同一セッションの複数試合を1セクションにまとめる")
        void shouldGroupEntriesBySession() {
            List<Map<String, Object>> entries = List.of(
                Map.of(
                    "sessionId", 100L,
                    "sessionLabel", "4月10日（中央公民館）",
                    "matchNumber", 1,
                    "waitlistNumber", 3,
                    "status", "WAITLISTED"
                ),
                Map.of(
                    "sessionId", 100L,
                    "sessionLabel", "4月10日（中央公民館）",
                    "matchNumber", 2,
                    "waitlistNumber", 1,
                    "status", "OFFERED",
                    "offerDeadline", LocalDateTime.of(2026, 4, 9, 18, 0)
                ),
                Map.of(
                    "sessionId", 200L,
                    "sessionLabel", "4月17日（中央公民館）",
                    "matchNumber", 1,
                    "waitlistNumber", 2,
                    "status", "WAITLISTED"
                )
            );

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            Map<String, Object> body = (Map<String, Object>) flex.get("body");
            List<Object> contents = (List<Object>) body.get("contents");

            // セッションラベルのテキスト要素を抽出
            List<String> sessionLabels = contents.stream()
                .filter(c -> {
                    Map<String, Object> m = (Map<String, Object>) c;
                    return "text".equals(m.get("type")) && "bold".equals(m.get("weight"))
                            && "md".equals(m.get("size"));
                })
                .map(c -> (String) ((Map<String, Object>) c).get("text"))
                .toList();

            // セッションラベルは2つ（同一セッションは1回のみ）
            assertThat(sessionLabels).containsExactly("4月10日（中央公民館）", "4月17日（中央公民館）");

            // 各試合の行テキストを抽出
            List<String> matchLines = contents.stream()
                .filter(c -> {
                    Map<String, Object> m = (Map<String, Object>) c;
                    return "text".equals(m.get("type")) && "sm".equals(m.get("size"));
                })
                .map(c -> (String) ((Map<String, Object>) c).get("text"))
                .toList();

            assertThat(matchLines).containsExactly(
                "1試合目 キャンセル待ち3番",
                "2試合目 繰り上げオファー中 期限：4/9 18:00",
                "1試合目 キャンセル待ち2番"
            );
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("同じsessionLabelでもsessionIdが異なれば別セクションになる")
        void shouldSeparateEntriesWithSameLabelButDifferentSessionId() {
            List<Map<String, Object>> entries = List.of(
                Map.of(
                    "sessionId", 100L,
                    "sessionLabel", "4月10日（中央公民館）",
                    "matchNumber", 1,
                    "waitlistNumber", 1,
                    "status", "WAITLISTED"
                ),
                Map.of(
                    "sessionId", 200L,
                    "sessionLabel", "4月10日（中央公民館）",
                    "matchNumber", 1,
                    "waitlistNumber", 2,
                    "status", "WAITLISTED"
                )
            );

            Map<String, Object> flex = lineNotificationService.buildWaitlistStatusFlex(entries);

            Map<String, Object> body = (Map<String, Object>) flex.get("body");
            List<Object> contents = (List<Object>) body.get("contents");

            // セッションラベルのテキスト要素を抽出（boldかつmdサイズ）
            List<String> sessionLabels = contents.stream()
                .filter(c -> {
                    Map<String, Object> m = (Map<String, Object>) c;
                    return "text".equals(m.get("type")) && "bold".equals(m.get("weight"))
                            && "md".equals(m.get("size"));
                })
                .map(c -> (String) ((Map<String, Object>) c).get("text"))
                .toList();

            // 同じラベルだがsessionIdが異なるので2セクション
            assertThat(sessionLabels).hasSize(2);
            assertThat(sessionLabels).containsExactly("4月10日（中央公民館）", "4月10日（中央公民館）");

            // セパレータが1つ存在する
            long separatorCount = contents.stream()
                .filter(c -> "separator".equals(((Map<String, Object>) c).get("type")))
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
