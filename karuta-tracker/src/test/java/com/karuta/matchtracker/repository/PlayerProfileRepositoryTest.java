package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PlayerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PlayerProfileRepositoryの結合テスト
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("PlayerProfileRepository 結合テスト")
class PlayerProfileRepositoryTest {

    @Autowired
    private PlayerProfileRepository playerProfileRepository;

    @Autowired
    private PlayerRepository playerRepository;

    private Player player;
    private PlayerProfile currentProfile;
    private PlayerProfile oldProfile;

    @BeforeEach
    void setUp() {
        // 選手データの準備
        player = playerRepository.save(Player.builder()
                .name("テスト選手")
                .password("password")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .build());

        // 古いプロフィール（D級無段 → C級初段に昇級した履歴）
        oldProfile = PlayerProfile.builder()
                .playerId(player.getId())
                .karutaClub("テストかるた会")
                .grade(PlayerProfile.Grade.D)
                .dan(PlayerProfile.Dan.無)
                .validFrom(LocalDate.of(2024, 1, 1))
                .validTo(LocalDate.of(2024, 6, 30))
                .build();

        // 現在のプロフィール
        currentProfile = PlayerProfile.builder()
                .playerId(player.getId())
                .karutaClub("テストかるた会")
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(LocalDate.of(2024, 7, 1))
                .validTo(null)  // 現在有効
                .build();

        playerProfileRepository.saveAll(List.of(oldProfile, currentProfile));
    }

    @Test
    @DisplayName("選手の現在有効なプロフィールを取得できる")
    void testFindCurrentByPlayerId() {
        // When
        Optional<PlayerProfile> found = playerProfileRepository.findCurrentByPlayerId(player.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getGrade()).isEqualTo(PlayerProfile.Grade.C);
        assertThat(found.get().getDan()).isEqualTo(PlayerProfile.Dan.初);
        assertThat(found.get().isCurrent()).isTrue();
    }

    @Test
    @DisplayName("選手の全プロフィール履歴を取得できる（新しい順）")
    void testFindAllByPlayerIdOrderByValidFromDesc() {
        // When
        List<PlayerProfile> profiles = playerProfileRepository
                .findAllByPlayerIdOrderByValidFromDesc(player.getId());

        // Then
        assertThat(profiles).hasSize(2);
        assertThat(profiles.get(0).getGrade()).isEqualTo(PlayerProfile.Grade.C);
        assertThat(profiles.get(1).getGrade()).isEqualTo(PlayerProfile.Grade.D);
    }

    @Test
    @DisplayName("特定の日付時点で有効だったプロフィールを取得できる")
    void testFindByPlayerIdAndDate() {
        // When - 2024年3月1日時点（D級無段の時）
        Optional<PlayerProfile> marchProfile = playerProfileRepository
                .findByPlayerIdAndDate(player.getId(), LocalDate.of(2024, 3, 1));

        // Then
        assertThat(marchProfile).isPresent();
        assertThat(marchProfile.get().getGrade()).isEqualTo(PlayerProfile.Grade.D);
        assertThat(marchProfile.get().getDan()).isEqualTo(PlayerProfile.Dan.無);

        // When - 2024年8月1日時点（C級初段の時）
        Optional<PlayerProfile> augustProfile = playerProfileRepository
                .findByPlayerIdAndDate(player.getId(), LocalDate.of(2024, 8, 1));

        // Then
        assertThat(augustProfile).isPresent();
        assertThat(augustProfile.get().getGrade()).isEqualTo(PlayerProfile.Grade.C);
        assertThat(augustProfile.get().getDan()).isEqualTo(PlayerProfile.Dan.初);
    }

    @Test
    @DisplayName("履歴がない日付では空のOptionalが返る")
    void testFindByPlayerIdAndDateBeforeHistory() {
        // When - プロフィール履歴よりも前の日付
        Optional<PlayerProfile> found = playerProfileRepository
                .findByPlayerIdAndDate(player.getId(), LocalDate.of(2023, 12, 31));

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("プロフィールを保存するとタイムスタンプが自動設定される")
    void testTimestampAutoSet() {
        // Given
        PlayerProfile newProfile = PlayerProfile.builder()
                .playerId(player.getId())
                .karutaClub("新しいかるた会")
                .grade(PlayerProfile.Grade.B)
                .dan(PlayerProfile.Dan.二)
                .validFrom(LocalDate.now())
                .validTo(null)
                .build();

        // When
        PlayerProfile saved = playerProfileRepository.save(newProfile);

        // Then
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("プロフィール更新時のタイムスタンプが更新される")
    void testTimestampUpdate() throws InterruptedException {
        // Given
        PlayerProfile profile = playerProfileRepository.findCurrentByPlayerId(player.getId()).orElseThrow();
        LocalDate originalUpdated = profile.getUpdatedAt().toLocalDate();

        Thread.sleep(10); // 更新タイムスタンプの差を確保

        // When
        profile.setKarutaClub("更新後のかるた会");
        PlayerProfile updated = playerProfileRepository.save(profile);

        // Then
        assertThat(updated.getUpdatedAt().toLocalDate()).isAfterOrEqualTo(originalUpdated);
    }
}
