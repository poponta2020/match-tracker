package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlayerRepositoryの結合テスト
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("PlayerRepository 結合テスト")
class PlayerRepositoryTest {

    @Autowired
    private PlayerRepository playerRepository;

    private Player activePlayer1;
    private Player activePlayer2;
    private Player deletedPlayer;

    @BeforeEach
    void setUp() {
        // テストデータの準備
        activePlayer1 = Player.builder()
                .name("山田太郎")
                .password("password123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .deletedAt(null)
                .build();

        activePlayer2 = Player.builder()
                .name("佐藤花子")
                .password("password456")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .role(Player.Role.ADMIN)
                .deletedAt(null)
                .build();

        deletedPlayer = Player.builder()
                .name("削除済み選手")
                .password("password789")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .deletedAt(LocalDateTime.now())
                .build();

        playerRepository.saveAll(List.of(activePlayer1, activePlayer2, deletedPlayer));
    }

    @Test
    @DisplayName("名前で選手を検索できる")
    void testFindByName() {
        // When
        Optional<Player> found = playerRepository.findByName("山田太郎");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("山田太郎");
    }

    @Test
    @DisplayName("存在しない名前で検索すると空のOptionalが返る")
    void testFindByNameNotFound() {
        // When
        Optional<Player> found = playerRepository.findByName("存在しない選手");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("部分一致で選手を検索できる")
    void testFindByNameContaining() {
        // When
        List<Player> found = playerRepository.findByNameContaining("山田");

        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("山田太郎");
    }

    @Test
    @DisplayName("アクティブな選手のみ取得できる（削除済みを除外）")
    void testFindAllActive() {
        // When
        List<Player> activePlayers = playerRepository.findAllActive();

        // Then
        assertThat(activePlayers).hasSize(2);
        assertThat(activePlayers)
                .extracting(Player::getName)
                .containsExactlyInAnyOrder("山田太郎", "佐藤花子");
    }

    @Test
    @DisplayName("アクティブな選手を名前順で取得できる")
    void testFindAllActiveOrderByName() {
        // When
        List<Player> activePlayers = playerRepository.findAllActiveOrderByName();

        // Then
        assertThat(activePlayers).hasSize(2);
        assertThat(activePlayers.get(0).getName()).isEqualTo("佐藤花子");
        assertThat(activePlayers.get(1).getName()).isEqualTo("山田太郎");
    }

    @Test
    @DisplayName("アクティブな選手数を取得できる")
    void testCountActive() {
        // When
        long count = playerRepository.countActive();

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("ロールでアクティブな選手を検索できる")
    void testFindByRoleAndActive() {
        // When
        List<Player> admins = playerRepository.findByRoleAndActive(Player.Role.ADMIN);

        // Then
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getName()).isEqualTo("佐藤花子");
        assertThat(admins.get(0).getRole()).isEqualTo(Player.Role.ADMIN);
    }

    @Test
    @DisplayName("名前とアクティブ状態で選手を検索できる（認証用）")
    void testFindByNameAndActive() {
        // When - アクティブな選手
        Optional<Player> active = playerRepository.findByNameAndActive("山田太郎");

        // Then
        assertThat(active).isPresent();
        assertThat(active.get().getName()).isEqualTo("山田太郎");

        // When - 削除済みの選手
        Optional<Player> deleted = playerRepository.findByNameAndActive("削除済み選手");

        // Then
        assertThat(deleted).isEmpty();
    }

    @Test
    @DisplayName("選手を保存できる")
    void testSavePlayer() {
        // Given
        Player newPlayer = Player.builder()
                .name("新規選手")
                .password("newpass")
                .gender(Player.Gender.その他)
                .dominantHand(Player.DominantHand.両)
                .role(Player.Role.PLAYER)
                .build();

        // When
        Player saved = playerRepository.save(newPlayer);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("新規選手");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("選手を論理削除できる")
    void testLogicalDelete() {
        // Given
        Player player = playerRepository.findByName("山田太郎").orElseThrow();

        // When - 論理削除（deletedAtを設定）
        player.setDeletedAt(LocalDateTime.now());
        playerRepository.save(player);

        // Then - アクティブな選手には含まれない
        List<Player> activePlayers = playerRepository.findAllActive();
        assertThat(activePlayers).hasSize(1);
        assertThat(activePlayers.get(0).getName()).isEqualTo("佐藤花子");

        // But - IDで検索すると取得できる
        Optional<Player> found = playerRepository.findById(player.getId());
        assertThat(found).isPresent();
        assertThat(found.get().isDeleted()).isTrue();
    }
}
