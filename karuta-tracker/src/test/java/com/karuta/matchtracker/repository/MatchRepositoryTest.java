package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MatchRepositoryの結合テスト
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("MatchRepository 結合テスト")
class MatchRepositoryTest {

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private PlayerRepository playerRepository;

    private Player player1;
    private Player player2;
    private Player player3;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        // 選手データの準備
        player1 = playerRepository.save(Player.builder()
                .name("選手1")
                .password("pass1")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .build());

        player2 = playerRepository.save(Player.builder()
                .name("選手2")
                .password("pass2")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .role(Player.Role.PLAYER)
                .build());

        player3 = playerRepository.save(Player.builder()
                .name("選手3")
                .password("pass3")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .build());

        // 対戦結果データの準備
        Match match1 = Match.builder()
                .matchDate(today)
                .matchNumber(1)
                .player1Id(player1.getId())
                .player2Id(player2.getId())
                .winnerId(player1.getId())
                .scoreDifference(15)
                .notes("テスト試合1")
                .createdBy(player1.getId())
                .updatedBy(player1.getId())
                .build();

        Match match2 = Match.builder()
                .matchDate(today)
                .matchNumber(2)
                .player1Id(player1.getId())
                .player2Id(player3.getId())
                .winnerId(player3.getId())
                .scoreDifference(10)
                .notes("テスト試合2")
                .createdBy(player1.getId())
                .updatedBy(player1.getId())
                .build();

        Match match3 = Match.builder()
                .matchDate(today.minusDays(7))
                .matchNumber(1)
                .player1Id(player2.getId())
                .player2Id(player3.getId())
                .winnerId(player2.getId())
                .scoreDifference(20)
                .notes("1週間前の試合")
                .createdBy(player2.getId())
                .updatedBy(player2.getId())
                .build();

        matchRepository.saveAll(List.of(match1, match2, match3));
    }

    @Test
    @DisplayName("日付別の対戦結果を試合番号順で取得できる")
    void testFindByMatchDateOrderByMatchNumber() {
        // When
        List<Match> matches = matchRepository.findByMatchDateOrderByMatchNumber(today);

        // Then
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).getMatchNumber()).isEqualTo(1);
        assertThat(matches.get(1).getMatchNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("選手の対戦結果を全て取得できる")
    void testFindByPlayerId() {
        // When
        List<Match> matches = matchRepository.findByPlayerId(player1.getId());

        // Then
        assertThat(matches).hasSize(2);
        assertThat(matches)
                .allMatch(m -> m.getPlayer1Id().equals(player1.getId()) ||
                              m.getPlayer2Id().equals(player1.getId()));
    }

    @Test
    @DisplayName("選手の期間内の対戦結果を取得できる")
    void testFindByPlayerIdAndDateRange() {
        // When
        List<Match> matches = matchRepository.findByPlayerIdAndDateRange(
                player1.getId(),
                today.minusDays(1),
                today
        );

        // Then
        assertThat(matches).hasSize(2);
        assertThat(matches)
                .allMatch(m -> m.getMatchDate().equals(today));
    }

    @Test
    @DisplayName("2人の選手間の対戦履歴を取得できる")
    void testFindByTwoPlayers() {
        // When
        List<Match> matches = matchRepository.findByTwoPlayers(player1.getId(), player2.getId());

        // Then
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getNotes()).isEqualTo("テスト試合1");
    }

    @Test
    @DisplayName("選手の総対戦数を取得できる")
    void testCountByPlayerId() {
        // When
        long count = matchRepository.countByPlayerId(player1.getId());

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("選手の勝利数を取得できる")
    void testCountWinsByPlayerId() {
        // When
        long winCount1 = matchRepository.countWinsByPlayerId(player1.getId());
        long winCount3 = matchRepository.countWinsByPlayerId(player3.getId());

        // Then
        assertThat(winCount1).isEqualTo(1);
        assertThat(winCount3).isEqualTo(1);
    }

    @Test
    @DisplayName("日付と試合番号で対戦結果を検索できる")
    void testFindByMatchDateAndMatchNumber() {
        // When
        List<Match> matches = matchRepository.findByMatchDateAndMatchNumber(today, 1);

        // Then
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getMatchNumber()).isEqualTo(1);
        assertThat(matches.get(0).getNotes()).isEqualTo("テスト試合1");
    }

    @Test
    @DisplayName("特定の日付に対戦結果が存在するか確認できる")
    void testExistsByMatchDate() {
        // When & Then
        assertThat(matchRepository.existsByMatchDate(today)).isTrue();
        assertThat(matchRepository.existsByMatchDate(today.minusDays(1))).isFalse();
    }

    @Test
    @DisplayName("player1_id < player2_id が自動的に保証される")
    void testPlayer1LessThanPlayer2Constraint() {
        // Given - player2.getId() < player1.getId() となるようにデータを作成
        Match match = Match.builder()
                .matchDate(today)
                .matchNumber(3)
                .player1Id(player2.getId())  // 大きいID
                .player2Id(player1.getId())  // 小さいID
                .winnerId(player1.getId())
                .scoreDifference(5)
                .createdBy(player1.getId())
                .updatedBy(player1.getId())
                .build();

        // When
        Match saved = matchRepository.save(match);

        // Then - 保存時に自動的に入れ替わる
        assertThat(saved.getPlayer1Id()).isLessThan(saved.getPlayer2Id());
    }

    @Test
    @DisplayName("対戦結果を保存するとタイムスタンプが自動設定される")
    void testTimestampAutoSet() {
        // Given
        Match match = Match.builder()
                .matchDate(today)
                .matchNumber(4)
                .player1Id(player1.getId())
                .player2Id(player2.getId())
                .winnerId(player1.getId())
                .scoreDifference(12)
                .createdBy(player1.getId())
                .updatedBy(player1.getId())
                .build();

        // When
        Match saved = matchRepository.save(match);

        // Then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
