package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PlayerServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerService 単体テスト")
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private PlayerService playerService;

    private Player testPlayer;
    private PlayerCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        testPlayer = Player.builder()
                .id(1L)
                .name("山田太郎")
                .password("password123")
                .gender(Player.Gender.男性)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .deletedAt(null)
                .build();

        createRequest = PlayerCreateRequest.builder()
                .name("佐藤花子")
                .password("password456")
                .gender(Player.Gender.女性)
                .dominantHand(Player.DominantHand.左)
                .build();
    }

    @Test
    @DisplayName("全てのアクティブな選手を取得できる")
    void testFindAllActivePlayers() {
        // Given
        Player player2 = Player.builder()
                .id(2L)
                .name("佐藤花子")
                .build();
        when(playerRepository.findAllActiveOrderByName())
                .thenReturn(List.of(testPlayer, player2));

        // When
        List<PlayerDto> result = playerService.findAllActivePlayers();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("山田太郎");
        assertThat(result.get(1).getName()).isEqualTo("佐藤花子");
        verify(playerRepository).findAllActiveOrderByName();
    }

    @Test
    @DisplayName("IDで選手を取得できる")
    void testFindById() {
        // Given
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When
        PlayerDto result = playerService.findById(1L);

        // Then
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("山田太郎");
        verify(playerRepository).findById(1L);
    }

    @Test
    @DisplayName("存在しないIDで選手を取得するとResourceNotFoundExceptionが発生")
    void testFindByIdNotFound() {
        // Given
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> playerService.findById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("999");
        verify(playerRepository).findById(999L);
    }

    @Test
    @DisplayName("名前で選手を検索できる")
    void testFindByName() {
        // Given
        when(playerRepository.findByNameAndActive("山田太郎"))
                .thenReturn(Optional.of(testPlayer));

        // When
        PlayerDto result = playerService.findByName("山田太郎");

        // Then
        assertThat(result.getName()).isEqualTo("山田太郎");
        verify(playerRepository).findByNameAndActive("山田太郎");
    }

    @Test
    @DisplayName("名前で部分一致検索ができる")
    void testSearchByName() {
        // Given
        when(playerRepository.findByNameContaining("山田"))
                .thenReturn(List.of(testPlayer));

        // When
        List<PlayerDto> result = playerService.searchByName("山田");

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("山田太郎");
        verify(playerRepository).findByNameContaining("山田");
    }

    @Test
    @DisplayName("ロール別で選手を検索できる")
    void testFindByRole() {
        // Given
        when(playerRepository.findByRoleAndActive(Player.Role.PLAYER))
                .thenReturn(List.of(testPlayer));

        // When
        List<PlayerDto> result = playerService.findByRole(Player.Role.PLAYER);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRole()).isEqualTo(Player.Role.PLAYER);
        verify(playerRepository).findByRoleAndActive(Player.Role.PLAYER);
    }

    @Test
    @DisplayName("アクティブな選手数を取得できる")
    void testCountActivePlayers() {
        // Given
        when(playerRepository.countActive()).thenReturn(5L);

        // When
        long count = playerService.countActivePlayers();

        // Then
        assertThat(count).isEqualTo(5L);
        verify(playerRepository).countActive();
    }

    @Test
    @DisplayName("選手を新規登録できる")
    void testCreatePlayer() {
        // Given
        Player newPlayer = createRequest.toEntity();
        newPlayer.setId(2L);
        when(playerRepository.findByNameAndActive(anyString())).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenReturn(newPlayer);

        // When
        PlayerDto result = playerService.createPlayer(createRequest);

        // Then
        assertThat(result.getId()).isEqualTo(2L);
        assertThat(result.getName()).isEqualTo("佐藤花子");
        verify(playerRepository).findByNameAndActive("佐藤花子");
        verify(playerRepository).save(any(Player.class));
    }

    @Test
    @DisplayName("既存の名前で選手を登録するとDuplicateResourceExceptionが発生")
    void testCreatePlayerDuplicateName() {
        // Given
        when(playerRepository.findByNameAndActive("佐藤花子"))
                .thenReturn(Optional.of(testPlayer));

        // When & Then
        assertThatThrownBy(() -> playerService.createPlayer(createRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Player")
                .hasMessageContaining("佐藤花子");
        verify(playerRepository).findByNameAndActive("佐藤花子");
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    @DisplayName("選手情報を更新できる")
    void testUpdatePlayer() {
        // Given
        PlayerUpdateRequest updateRequest = PlayerUpdateRequest.builder()
                .name("山田太郎（更新）")
                .build();
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(playerRepository.findByNameAndActive("山田太郎（更新）"))
                .thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenReturn(testPlayer);

        // When
        PlayerDto result = playerService.updatePlayer(1L, updateRequest);

        // Then
        assertThat(result).isNotNull();
        verify(playerRepository).findById(1L);
        verify(playerRepository).save(any(Player.class));
    }

    @Test
    @DisplayName("削除済みの選手を更新するとIllegalStateExceptionが発生")
    void testUpdateDeletedPlayer() {
        // Given
        testPlayer.setDeletedAt(LocalDateTime.now());
        PlayerUpdateRequest updateRequest = PlayerUpdateRequest.builder()
                .name("新しい名前")
                .build();
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When & Then
        assertThatThrownBy(() -> playerService.updatePlayer(1L, updateRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deleted player");
        verify(playerRepository).findById(1L);
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    @DisplayName("選手を論理削除できる")
    void testDeletePlayer() {
        // Given
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(playerRepository.save(any(Player.class))).thenReturn(testPlayer);

        // When
        playerService.deletePlayer(1L);

        // Then
        verify(playerRepository).findById(1L);
        verify(playerRepository).save(any(Player.class));
    }

    @Test
    @DisplayName("既に削除済みの選手を削除しても例外は発生しない")
    void testDeleteAlreadyDeletedPlayer() {
        // Given
        testPlayer.setDeletedAt(LocalDateTime.now());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When
        playerService.deletePlayer(1L);

        // Then
        verify(playerRepository).findById(1L);
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    @DisplayName("選手のロールを変更できる")
    void testUpdateRole() {
        // Given
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));
        when(playerRepository.save(any(Player.class))).thenReturn(testPlayer);

        // When
        PlayerDto result = playerService.updateRole(1L, Player.Role.ADMIN);

        // Then
        assertThat(result).isNotNull();
        verify(playerRepository).findById(1L);
        verify(playerRepository).save(any(Player.class));
    }

    @Test
    @DisplayName("削除済みの選手のロールを変更するとIllegalStateExceptionが発生")
    void testUpdateRoleDeletedPlayer() {
        // Given
        testPlayer.setDeletedAt(LocalDateTime.now());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        // When & Then
        assertThatThrownBy(() -> playerService.updateRole(1L, Player.Role.ADMIN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deleted player");
        verify(playerRepository).findById(1L);
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    @DisplayName("正しい選手名とパスワードでログインできる")
    void testLoginSuccess() {
        // Given
        Player player = Player.builder()
                .id(1L)
                .name("山田太郎")
                .password("password123")
                .gender(Player.Gender.その他)
                .dominantHand(Player.DominantHand.右)
                .role(Player.Role.PLAYER)
                .kyuRank(Player.KyuRank.A級)
                .build();

        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("山田太郎", "password123");

        when(playerRepository.findByNameAndActive("山田太郎")).thenReturn(Optional.of(player));

        // When
        com.karuta.matchtracker.dto.LoginResponse response = playerService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("山田太郎");
        assertThat(response.getRole()).isEqualTo(Player.Role.PLAYER);
        verify(playerRepository).findByNameAndActive("山田太郎");
    }

    @Test
    @DisplayName("存在しない選手名でログインするとResourceNotFoundExceptionが発生")
    void testLoginNonexistentUser() {
        // Given
        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("存在しない選手", "password");

        when(playerRepository.findByNameAndActive("存在しない選手")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> playerService.login(request))
                .isInstanceOf(com.karuta.matchtracker.exception.ResourceNotFoundException.class)
                .hasMessageContaining("選手名またはパスワードが正しくありません");

        verify(playerRepository).findByNameAndActive("存在しない選手");
    }

    @Test
    @DisplayName("誤ったパスワードでログインするとResourceNotFoundExceptionが発生")
    void testLoginWrongPassword() {
        // Given
        Player player = Player.builder()
                .id(1L)
                .name("山田太郎")
                .password("correctPassword")
                .role(Player.Role.PLAYER)
                .build();

        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("山田太郎", "wrongPassword");

        when(playerRepository.findByNameAndActive("山田太郎")).thenReturn(Optional.of(player));

        // When & Then
        assertThatThrownBy(() -> playerService.login(request))
                .isInstanceOf(com.karuta.matchtracker.exception.ResourceNotFoundException.class)
                .hasMessageContaining("選手名またはパスワードが正しくありません");

        verify(playerRepository).findByNameAndActive("山田太郎");
    }

    @Test
    @DisplayName("空のパスワードでログインするとResourceNotFoundExceptionが発生")
    void testLoginEmptyPassword() {
        // Given
        Player player = Player.builder()
                .id(1L)
                .name("山田太郎")
                .password("password123")
                .role(Player.Role.PLAYER)
                .build();

        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("山田太郎", "");

        when(playerRepository.findByNameAndActive("山田太郎")).thenReturn(Optional.of(player));

        // When & Then
        assertThatThrownBy(() -> playerService.login(request))
                .isInstanceOf(com.karuta.matchtracker.exception.ResourceNotFoundException.class)
                .hasMessageContaining("選手名またはパスワードが正しくありません");

        verify(playerRepository).findByNameAndActive("山田太郎");
    }
}
