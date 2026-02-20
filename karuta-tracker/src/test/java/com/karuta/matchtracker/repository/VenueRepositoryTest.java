package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.config.TestContainersConfig;
import com.karuta.matchtracker.entity.Venue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VenueRepositoryの結合テスト
 */
@DataJpaTest
@Import(TestContainersConfig.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@DisplayName("VenueRepository 結合テスト")
class VenueRepositoryTest {

    @Autowired
    private VenueRepository venueRepository;

    private Venue venue1;
    private Venue venue2;

    @BeforeEach
    void setUp() {
        // テストデータの準備
        venue1 = Venue.builder()
                .name("東京会場")
                .defaultMatchCount(5)
                .build();

        venue2 = Venue.builder()
                .name("大阪会場")
                .defaultMatchCount(6)
                .build();

        venueRepository.saveAll(List.of(venue1, venue2));
    }

    // ===== findByName テスト =====

    @Test
    @DisplayName("名前で会場を検索できる")
    void testFindByName_ExistingVenue_ReturnsVenue() {
        // When
        Optional<Venue> found = venueRepository.findByName("東京会場");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("東京会場");
        assertThat(found.get().getDefaultMatchCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("存在しない名前で検索すると空のOptionalが返る")
    void testFindByName_NonExisting_ReturnsEmpty() {
        // When
        Optional<Venue> found = venueRepository.findByName("存在しない会場");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("名前検索は大文字小文字を区別する")
    void testFindByName_CaseSensitivity() {
        // When
        Optional<Venue> found = venueRepository.findByName("東京会場");
        Optional<Venue> notFound = venueRepository.findByName("TOKYO会場");

        // Then
        assertThat(found).isPresent();
        assertThat(notFound).isEmpty();
    }

    // ===== existsByName テスト =====

    @Test
    @DisplayName("存在する会場名でtrueを返す")
    void testExistsByName_Existing_ReturnsTrue() {
        // When
        boolean exists = venueRepository.existsByName("東京会場");

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("存在しない会場名でfalseを返す")
    void testExistsByName_NonExisting_ReturnsFalse() {
        // When
        boolean exists = venueRepository.existsByName("存在しない会場");

        // Then
        assertThat(exists).isFalse();
    }

    // ===== CRUD操作テスト =====

    @Test
    @DisplayName("会場を保存するとIDが自動生成される")
    void testSave_NewVenue_GeneratesId() {
        // Given
        Venue newVenue = Venue.builder()
                .name("名古屋会場")
                .defaultMatchCount(4)
                .build();

        // When
        Venue saved = venueRepository.save(newVenue);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("名古屋会場");
        assertThat(saved.getDefaultMatchCount()).isEqualTo(4);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("全会場を取得できる")
    void testFindAll_MultipleVenues_ReturnsAll() {
        // When
        List<Venue> venues = venueRepository.findAll();

        // Then
        assertThat(venues).hasSize(2);
        assertThat(venues)
                .extracting(Venue::getName)
                .containsExactlyInAnyOrder("東京会場", "大阪会場");
    }

    @Test
    @DisplayName("IDで会場を取得できる")
    void testFindById_ExistingId_ReturnsVenue() {
        // When
        Optional<Venue> found = venueRepository.findById(venue1.getId());

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("東京会場");
    }

    @Test
    @DisplayName("存在しないIDで検索すると空のOptionalが返る")
    void testFindById_NonExistingId_ReturnsEmpty() {
        // When
        Optional<Venue> found = venueRepository.findById(9999L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("会場を更新できる")
    void testSave_UpdateVenue_UpdatesFields() {
        // Given
        venue1.setName("東京新会場");
        venue1.setDefaultMatchCount(7);

        // When
        Venue updated = venueRepository.save(venue1);

        // Then
        assertThat(updated.getName()).isEqualTo("東京新会場");
        assertThat(updated.getDefaultMatchCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("会場を削除できる")
    void testDelete_ExistingVenue_RemovesFromDatabase() {
        // Given
        Long venueId = venue1.getId();

        // When
        venueRepository.delete(venue1);

        // Then
        Optional<Venue> found = venueRepository.findById(venueId);
        assertThat(found).isEmpty();
        assertThat(venueRepository.count()).isEqualTo(1);
    }

    // ===== 境界値テスト =====

    @Test
    @DisplayName("200文字の名前で保存できる")
    void testSave_MaxLengthName_Success() {
        // Given
        String maxLengthName = "あ".repeat(200);
        Venue venue = Venue.builder()
                .name(maxLengthName)
                .defaultMatchCount(5)
                .build();

        // When
        Venue saved = venueRepository.save(venue);

        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).hasSize(200);
    }
}
