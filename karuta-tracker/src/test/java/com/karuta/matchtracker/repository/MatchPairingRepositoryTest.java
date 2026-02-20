package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.MatchPairing;
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
 * MatchPairingRepositoryの結合テスト
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("MatchPairingRepository 結合テスト")
class MatchPairingRepositoryTest {

    @Autowired
    private MatchPairingRepository matchPairingRepository;

    @Autowired
    private PlayerRepository playerRepository;

    private Player player1;
    private Player player2;
    private Player player3;
    private Player player4;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        // テスト用選手を作成
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

        player4 = playerRepository.save(Player.builder()
                .name("選手4")
                .password("pass4")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.両)
                .role(Player.Role.PLAYER)
                .build());
    }

    // ===== findBySessionDateOrderByMatchNumber テスト =====

    @Test
    @DisplayName("日付指定で対戦組み合わせを試合番号順に取得できる")
    void testFindBySessionDateOrderByMatchNumber_ReturnsOrdered() {
        // Given
        MatchPairing pairing1 = matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(2)  // 2番目に登録
                .player1Id(player1.getId())
                .player2Id(player2.getId())
                .createdBy(1L)
                .build());

        MatchPairing pairing2 = matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(1)  // 1番目に登録
                .player1Id(player3.getId())
                .player2Id(player4.getId())
                .createdBy(1L)
                .build());

        // When
        List<MatchPairing> result = matchPairingRepository.findBySessionDateOrderByMatchNumber(today);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getMatchNumber()).isEqualTo(1);  // 試合番号順
        assertThat(result.get(1).getMatchNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("データがない日付では空リストを返す")
    void testFindBySessionDate_NoMatches_ReturnsEmpty() {
        // Given - データなし

        // When
        List<MatchPairing> result = matchPairingRepository.findBySessionDateOrderByMatchNumber(today);

        // Then
        assertThat(result).isEmpty();
    }

    // ===== findBySessionDateAndMatchNumber テスト =====

    @Test
    @DisplayName("日付と試合番号で対戦組み合わせを取得できる")
    void testFindBySessionDateAndMatchNumber_ReturnsMatching() {
        // Given
        matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(1)
                .player1Id(player1.getId())
                .player2Id(player2.getId())
                .createdBy(1L)
                .build());

        matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(1)
                .player1Id(player3.getId())
                .player2Id(player4.getId())
                .createdBy(1L)
                .build());

        matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(2)
                .player1Id(player1.getId())
                .player2Id(player3.getId())
                .createdBy(1L)
                .build());

        // When
        List<MatchPairing> result = matchPairingRepository.findBySessionDateAndMatchNumber(today, 1);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(p -> p.getMatchNumber().equals(1));
    }

    // ===== deleteBySessionDateAndMatchNumber テスト =====

    @Test
    @DisplayName("日付と試合番号で対戦組み合わせを削除できる")
    void testDeleteBySessionDateAndMatchNumber_DeletesMatching() {
        // Given
        matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(1)
                .player1Id(player1.getId())
                .player2Id(player2.getId())
                .createdBy(1L)
                .build());

        matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(2)
                .player1Id(player3.getId())
                .player2Id(player4.getId())
                .createdBy(1L)
                .build());

        // When
        matchPairingRepository.deleteBySessionDateAndMatchNumber(today, 1);

        // Then
        List<MatchPairing> remaining = matchPairingRepository.findBySessionDateOrderByMatchNumber(today);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getMatchNumber()).isEqualTo(2);
    }

    // ===== existsBySessionDateAndMatchNumber テスト =====

    @Test
    @DisplayName("存在する日付と試合番号でtrueを返す")
    void testExistsBySessionDateAndMatchNumber_Existing_ReturnsTrue() {
        // Given
        matchPairingRepository.save(MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(1)
                .player1Id(player1.getId())
                .player2Id(player2.getId())
                .createdBy(1L)
                .build());

        // When
        boolean exists = matchPairingRepository.existsBySessionDateAndMatchNumber(today, 1);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("存在しない日付と試合番号でfalseを返す")
    void testExistsBySessionDateAndMatchNumber_NonExisting_ReturnsFalse() {
        // Given - データなし

        // When
        boolean exists = matchPairingRepository.existsBySessionDateAndMatchNumber(today, 1);

        // Then
        assertThat(exists).isFalse();
    }

    // ===== 複数試合番号テスト =====

    @Test
    @DisplayName("同日の異なる試合番号を正しく区別できる")
    void testMultiplePairings_SameDate_DifferentMatchNumbers() {
        // Given - 同日に3つの試合番号
        for (int matchNum = 1; matchNum <= 3; matchNum++) {
            matchPairingRepository.save(MatchPairing.builder()
                    .sessionDate(today)
                    .matchNumber(matchNum)
                    .player1Id(player1.getId())
                    .player2Id(player2.getId())
                    .createdBy(1L)
                    .build());
        }

        // When
        List<MatchPairing> allPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(today);
        boolean existsMatch1 = matchPairingRepository.existsBySessionDateAndMatchNumber(today, 1);
        boolean existsMatch2 = matchPairingRepository.existsBySessionDateAndMatchNumber(today, 2);
        boolean existsMatch3 = matchPairingRepository.existsBySessionDateAndMatchNumber(today, 3);
        boolean existsMatch4 = matchPairingRepository.existsBySessionDateAndMatchNumber(today, 4);

        // Then
        assertThat(allPairings).hasSize(3);
        assertThat(existsMatch1).isTrue();
        assertThat(existsMatch2).isTrue();
        assertThat(existsMatch3).isTrue();
        assertThat(existsMatch4).isFalse();
    }

    // ===== 保存テスト =====

    @Test
    @DisplayName("対戦組み合わせを保存するとタイムスタンプが自動設定される")
    void testSave_TimestampsAutoSet() {
        // Given
        MatchPairing pairing = MatchPairing.builder()
                .sessionDate(today)
                .matchNumber(1)
                .player1Id(player1.getId())
                .player2Id(player2.getId())
                .createdBy(1L)
                .build();

        // When
        MatchPairing saved = matchPairingRepository.save(pairing);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
