package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PlayerProfileCreateRequest;
import com.karuta.matchtracker.dto.PlayerProfileDto;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PlayerProfile;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerProfileRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PlayerProfileServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerProfileService 単体テスト")
class PlayerProfileServiceTest {

    @Mock
    private PlayerProfileRepository playerProfileRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerProfileService playerProfileService;

    private Player testPlayer;
    private PlayerProfile currentProfile;
    private PlayerProfile oldProfile;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now();

        testPlayer = Player.builder()
                .id(1L)
                .name("山田太郎")
                .build();

        currentProfile = PlayerProfile.builder()
                .id(1L)
                .playerId(1L)
                .grade(PlayerProfile.Grade.C)
                .dan(PlayerProfile.Dan.初)
                .validFrom(LocalDate.of(2024, 1, 1))
                .validTo(null)
                .build();

        oldProfile = PlayerProfile.builder()
                .id(2L)
                .playerId(1L)
                .grade(PlayerProfile.Grade.D)
                .dan(PlayerProfile.Dan.無)
                .validFrom(LocalDate.of(2023, 1, 1))
                .validTo(LocalDate.of(2023, 12, 31))
                .build();
    }

    @Test
    @DisplayName("選手の現在有効なプロフィールを取得できる")
    void testFindCurrentProfile() {
        // Given
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerProfileRepository.findCurrentByPlayerId(1L))
                .thenReturn(Optional.of(currentProfile));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When
        Optional<PlayerProfileDto> result = playerProfileService.findCurrentProfile(1L);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getGrade()).isEqualTo(PlayerProfile.Grade.C);
        assertThat(result.get().getDan()).isEqualTo(PlayerProfile.Dan.初);
        assertThat(result.get().getPlayerName()).isEqualTo("山田太郎");
        verify(playerProfileRepository).findCurrentByPlayerId(1L);
    }

    @Test
    @DisplayName("存在しない選手の現在プロフィールを取得するとResourceNotFoundExceptionが発生")
    void testFindCurrentProfilePlayerNotFound() {
        // Given
        when(playerRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> playerProfileService.findCurrentProfile(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("999");
        verify(playerRepository).existsById(999L);
        verify(playerProfileRepository, never()).findCurrentByPlayerId(any());
    }

    @Test
    @DisplayName("選手の特定日時点のプロフィールを取得できる")
    void testFindProfileAtDate() {
        // Given
        LocalDate targetDate = LocalDate.of(2023, 6, 1);
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerProfileRepository.findByPlayerIdAndDate(1L, targetDate))
                .thenReturn(Optional.of(oldProfile));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When
        Optional<PlayerProfileDto> result = playerProfileService.findProfileAtDate(1L, targetDate);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getGrade()).isEqualTo(PlayerProfile.Grade.D);
        verify(playerProfileRepository).findByPlayerIdAndDate(1L, targetDate);
    }

    @Test
    @DisplayName("選手の全プロフィール履歴を取得できる")
    void testFindProfileHistory() {
        // Given
        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerProfileRepository.findAllByPlayerIdOrderByValidFromDesc(1L))
                .thenReturn(List.of(currentProfile, oldProfile));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When
        List<PlayerProfileDto> result = playerProfileService.findProfileHistory(1L);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getGrade()).isEqualTo(PlayerProfile.Grade.C);
        assertThat(result.get(1).getGrade()).isEqualTo(PlayerProfile.Grade.D);
        verify(playerProfileRepository).findAllByPlayerIdOrderByValidFromDesc(1L);
    }

    @Test
    @DisplayName("プロフィールを新規登録できる")
    void testCreateProfile() {
        // Given
        PlayerProfileCreateRequest request = PlayerProfileCreateRequest.builder()
                .playerId(1L)
                .grade(PlayerProfile.Grade.B)
                .dan(PlayerProfile.Dan.二)
                .validFrom(LocalDate.of(2024, 6, 1))
                .build();

        when(playerRepository.existsById(1L)).thenReturn(true);
        when(playerProfileRepository.findCurrentByPlayerId(1L))
                .thenReturn(Optional.of(currentProfile));
        when(playerProfileRepository.save(any(PlayerProfile.class)))
                .thenAnswer(invocation -> {
                    PlayerProfile saved = invocation.getArgument(0);
                    saved.setId(3L);
                    return saved;
                });
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When
        PlayerProfileDto result = playerProfileService.createProfile(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getGrade()).isEqualTo(PlayerProfile.Grade.B);
        verify(playerProfileRepository, times(2)).save(any(PlayerProfile.class));
    }

    @Test
    @DisplayName("プロフィールの有効期限を設定できる")
    void testSetValidTo() {
        // Given
        LocalDate validTo = LocalDate.of(2024, 12, 31);
        when(playerProfileRepository.findById(1L)).thenReturn(Optional.of(currentProfile));
        when(playerProfileRepository.save(any(PlayerProfile.class))).thenReturn(currentProfile);
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When
        PlayerProfileDto result = playerProfileService.setValidTo(1L, validTo);

        // Then
        assertThat(result).isNotNull();
        verify(playerProfileRepository).findById(1L);
        verify(playerProfileRepository).save(any(PlayerProfile.class));
    }

    @Test
    @DisplayName("有効期限が開始日より前の場合はIllegalArgumentExceptionが発生")
    void testSetValidToBeforeValidFrom() {
        // Given
        LocalDate invalidValidTo = LocalDate.of(2023, 12, 31);
        when(playerProfileRepository.findById(1L)).thenReturn(Optional.of(currentProfile));

        // When & Then
        assertThatThrownBy(() -> playerProfileService.setValidTo(1L, invalidValidTo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid_to must be after or equal to valid_from");
        verify(playerProfileRepository).findById(1L);
        verify(playerProfileRepository, never()).save(any(PlayerProfile.class));
    }

    @Test
    @DisplayName("プロフィールを削除できる")
    void testDeleteProfile() {
        // Given
        when(playerProfileRepository.existsById(1L)).thenReturn(true);

        // When
        playerProfileService.deleteProfile(1L);

        // Then
        verify(playerProfileRepository).existsById(1L);
        verify(playerProfileRepository).deleteById(1L);
    }

    @Test
    @DisplayName("存在しないプロフィールを削除するとResourceNotFoundExceptionが発生")
    void testDeleteProfileNotFound() {
        // Given
        when(playerProfileRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> playerProfileService.deleteProfile(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("PlayerProfile")
                .hasMessageContaining("999");
        verify(playerProfileRepository, never()).deleteById(any());
    }
}
