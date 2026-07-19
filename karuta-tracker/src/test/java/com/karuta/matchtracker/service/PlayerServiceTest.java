package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.PlayerBulkUpdateRequest;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PlayerOrganization;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
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
import org.mockito.ArgumentCaptor;

/**
 * PlayerServiceの単体テスト
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlayerService 単体テスト")
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerOrganizationRepository playerOrganizationRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicy passwordPolicy;

    @Mock
    private AuthTokenService authTokenService;

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

        PlayerOrganization po = PlayerOrganization.builder()
                .id(1L).playerId(1L).organizationId(10L).build();
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(1L, 2L))).thenReturn(List.of(po));

        // When
        List<PlayerDto> result = playerService.findAllActivePlayers();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("山田太郎");
        assertThat(result.get(0).getOrganizationIds()).containsExactly(10L);
        assertThat(result.get(1).getName()).isEqualTo("佐藤花子");
        assertThat(result.get(1).getOrganizationIds()).isEmpty();
        verify(playerRepository).findAllActiveOrderByName();
        verify(playerOrganizationRepository).findByPlayerIdIn(List.of(1L, 2L));
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
        Player newPlayer = createRequest.toEntity("$2a$10$encoded");
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
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("山田太郎（更新）");
        verify(playerRepository).findById(1L);
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
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getDeletedAt()).isNotNull();
        verify(playerRepository).findById(1L);
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
        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(playerRepository).save(captor.capture());
        Player saved = captor.getValue();
        assertThat(saved.getRole()).isEqualTo(Player.Role.ADMIN);
        verify(playerRepository).findById(1L);
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

        when(playerRepository.findByNameAndActiveForUpdate("山田太郎")).thenReturn(Optional.of(player));
        // パスワードは BCrypt で照合される（平文比較ではない）
        when(passwordEncoder.matches("password123", "password123")).thenReturn(true);
        when(authTokenService.issue(player)).thenReturn("issued-token");

        PlayerOrganization po = PlayerOrganization.builder()
                .id(1L).playerId(1L).organizationId(10L).build();
        when(playerOrganizationRepository.findByPlayerId(1L)).thenReturn(List.of(po));

        // When
        com.karuta.matchtracker.dto.LoginResponse response = playerService.login(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("山田太郎");
        assertThat(response.getRole()).isEqualTo(Player.Role.PLAYER);
        assertThat(response.getOrganizationIds()).containsExactly(10L);
        assertThat(response.getToken()).isEqualTo("issued-token");
        verify(playerRepository).findByNameAndActiveForUpdate("山田太郎");
        verify(playerOrganizationRepository).findByPlayerId(1L);
    }

    @Test
    @DisplayName("存在しない選手名でログインするとResourceNotFoundExceptionが発生")
    void testLoginNonexistentUser() {
        // Given
        com.karuta.matchtracker.dto.LoginRequest request =
                new com.karuta.matchtracker.dto.LoginRequest("存在しない選手", "password");

        when(playerRepository.findByNameAndActiveForUpdate("存在しない選手")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> playerService.login(request))
                .isInstanceOf(com.karuta.matchtracker.exception.ResourceNotFoundException.class)
                .hasMessageContaining("選手名またはパスワードが正しくありません");

        verify(playerRepository).findByNameAndActiveForUpdate("存在しない選手");
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

        when(playerRepository.findByNameAndActiveForUpdate("山田太郎")).thenReturn(Optional.of(player));

        // When & Then
        assertThatThrownBy(() -> playerService.login(request))
                .isInstanceOf(com.karuta.matchtracker.exception.ResourceNotFoundException.class)
                .hasMessageContaining("選手名またはパスワードが正しくありません");

        verify(playerRepository).findByNameAndActiveForUpdate("山田太郎");
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

        when(playerRepository.findByNameAndActiveForUpdate("山田太郎")).thenReturn(Optional.of(player));

        // When & Then
        assertThatThrownBy(() -> playerService.login(request))
                .isInstanceOf(com.karuta.matchtracker.exception.ResourceNotFoundException.class)
                .hasMessageContaining("選手名またはパスワードが正しくありません");

        verify(playerRepository).findByNameAndActiveForUpdate("山田太郎");
    }

    // ===== bulkUpdate（一括更新） =====

    @Test
    @DisplayName("複数選手の players 列（性別・級・段位・かるた会）を一括更新できる")
    void testBulkUpdateAppliesPlayerColumns() {
        // Given
        Player p1 = Player.builder().id(1L).name("一郎").gender(Player.Gender.男性)
                .kyuRank(Player.KyuRank.E級).danRank(Player.DanRank.無段).build();
        Player p2 = Player.builder().id(2L).name("二郎").gender(Player.Gender.女性)
                .kyuRank(Player.KyuRank.E級).danRank(Player.DanRank.無段).build();

        when(playerOrganizationRepository.findByPlayerIdIn(List.of(1L, 2L))).thenReturn(List.of());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(playerRepository.findById(2L)).thenReturn(Optional.of(p2));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(
                        PlayerBulkUpdateRequest.Item.builder()
                                .playerId(1L).gender(Player.Gender.その他)
                                .kyuRank(Player.KyuRank.A級).danRank(Player.DanRank.四段)
                                .karutaClub("北海道大学かるた会").build(),
                        PlayerBulkUpdateRequest.Item.builder()
                                .playerId(2L).gender(Player.Gender.女性)
                                .kyuRank(Player.KyuRank.D級).danRank(Player.DanRank.初段)
                                .karutaClub("わすらもち会").build()
                ))
                .build();

        // When
        List<PlayerDto> result = playerService.bulkUpdate(request);

        // Then
        assertThat(result).hasSize(2);
        assertThat(p1.getGender()).isEqualTo(Player.Gender.その他);
        assertThat(p1.getKyuRank()).isEqualTo(Player.KyuRank.A級);
        assertThat(p1.getDanRank()).isEqualTo(Player.DanRank.四段);
        assertThat(p1.getKarutaClub()).isEqualTo("北海道大学かるた会");
        assertThat(p2.getKyuRank()).isEqualTo(Player.KyuRank.D級);
        assertThat(p2.getDanRank()).isEqualTo(Player.DanRank.初段);
        verify(playerRepository, times(2)).save(any(Player.class));
        verify(organizationService, never()).ensurePlayerBelongsToOrganization(any(), any());
    }

    @Test
    @DisplayName("級↔段位は単体更新と同じくフロント算出値をそのまま保存する")
    void testBulkUpdateStoresKyuDanPairAsGiven() {
        // Given: フロントが算出した「A級→四段」のペアをそのまま受け取り保存する
        Player p1 = Player.builder().id(1L).name("一郎").gender(Player.Gender.男性)
                .kyuRank(Player.KyuRank.E級).danRank(Player.DanRank.無段).build();
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(1L))).thenReturn(List.of());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(PlayerBulkUpdateRequest.Item.builder()
                        .playerId(1L).kyuRank(Player.KyuRank.A級).danRank(Player.DanRank.六段)
                        .build()))
                .build();

        // When
        playerService.bulkUpdate(request);

        // Then
        assertThat(p1.getKyuRank()).isEqualTo(Player.KyuRank.A級);
        assertThat(p1.getDanRank()).isEqualTo(Player.DanRank.六段);
    }

    @Test
    @DisplayName("所属団体の追加は既存に無いものだけ保存される（追加のみ・マージ）")
    void testBulkUpdateAddsOnlyMissingOrganizations() {
        // Given: 選手1は既に org 10 に所属
        Player p1 = Player.builder().id(1L).name("一郎").gender(Player.Gender.男性).build();
        PlayerOrganization existing = PlayerOrganization.builder()
                .id(100L).playerId(1L).organizationId(10L).build();
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(1L))).thenReturn(List.of(existing));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(PlayerBulkUpdateRequest.Item.builder()
                        .playerId(1L).addOrganizationIds(List.of(10L, 20L)) // 10は既存、20が新規
                        .build()))
                .build();

        // When
        List<PlayerDto> result = playerService.bulkUpdate(request);

        // Then: 正規経路 ensurePlayerBelongsToOrganization 経由で org 20 のみ追加（通知設定も初期化される）
        verify(organizationService).ensurePlayerBelongsToOrganization(1L, 20L);
        verify(organizationService, never()).ensurePlayerBelongsToOrganization(1L, 10L);
        // 返却DTOの organizationIds は既存+追加
        assertThat(result.get(0).getOrganizationIds()).containsExactlyInAnyOrder(10L, 20L);
    }

    @Test
    @DisplayName("既に所属している団体を追加しても二重登録されない（冪等）")
    void testBulkUpdateOrganizationAddIsIdempotent() {
        // Given
        Player p1 = Player.builder().id(1L).name("一郎").gender(Player.Gender.男性).build();
        PlayerOrganization existing = PlayerOrganization.builder()
                .id(100L).playerId(1L).organizationId(10L).build();
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(1L))).thenReturn(List.of(existing));
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(PlayerBulkUpdateRequest.Item.builder()
                        .playerId(1L).addOrganizationIds(List.of(10L)).build()))
                .build();

        // When
        playerService.bulkUpdate(request);

        // Then: 既存と同じ org なので追加処理は呼ばれない（冪等）
        verify(organizationService, never()).ensurePlayerBelongsToOrganization(any(), any());
    }

    @Test
    @DisplayName("null 項目は更新対象外（指定された項目のみ反映）")
    void testBulkUpdateOnlyNonNullFieldsApplied() {
        // Given
        Player p1 = Player.builder().id(1L).name("一郎")
                .gender(Player.Gender.男性).kyuRank(Player.KyuRank.C級)
                .danRank(Player.DanRank.弐段).karutaClub("既存会").build();
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(1L))).thenReturn(List.of());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(p1));
        when(playerRepository.save(any(Player.class))).thenAnswer(inv -> inv.getArgument(0));

        // 級・段位のみ変更、性別・かるた会は null（据え置き）
        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(PlayerBulkUpdateRequest.Item.builder()
                        .playerId(1L).kyuRank(Player.KyuRank.B級).danRank(Player.DanRank.参段)
                        .build()))
                .build();

        // When
        playerService.bulkUpdate(request);

        // Then
        assertThat(p1.getKyuRank()).isEqualTo(Player.KyuRank.B級);
        assertThat(p1.getDanRank()).isEqualTo(Player.DanRank.参段);
        assertThat(p1.getGender()).isEqualTo(Player.Gender.男性);   // 据え置き
        assertThat(p1.getKarutaClub()).isEqualTo("既存会");          // 据え置き
    }

    @Test
    @DisplayName("空の updates では何も更新せず空リストを返す")
    void testBulkUpdateEmptyUpdates() {
        // Given
        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of()).build();

        // When
        List<PlayerDto> result = playerService.bulkUpdate(request);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(playerRepository);
        verifyNoInteractions(playerOrganizationRepository);
    }

    @Test
    @DisplayName("存在しない選手IDが含まれる場合 ResourceNotFoundException で全体ロールバック")
    void testBulkUpdateNonexistentPlayerThrows() {
        // Given
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(999L))).thenReturn(List.of());
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(PlayerBulkUpdateRequest.Item.builder()
                        .playerId(999L).gender(Player.Gender.男性).build()))
                .build();

        // When & Then
        assertThatThrownBy(() -> playerService.bulkUpdate(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Player");
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    @DisplayName("削除済みの選手が含まれる場合 IllegalStateException で全体ロールバック")
    void testBulkUpdateDeletedPlayerThrows() {
        // Given
        Player deleted = Player.builder().id(1L).name("一郎").gender(Player.Gender.男性)
                .deletedAt(LocalDateTime.now()).build();
        when(playerOrganizationRepository.findByPlayerIdIn(List.of(1L))).thenReturn(List.of());
        when(playerRepository.findById(1L)).thenReturn(Optional.of(deleted));

        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(PlayerBulkUpdateRequest.Item.builder()
                        .playerId(1L).gender(Player.Gender.女性).build()))
                .build();

        // When & Then
        assertThatThrownBy(() -> playerService.bulkUpdate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deleted player");
        verify(playerRepository, never()).save(any(Player.class));
    }

    @Test
    @DisplayName("存在しない団体IDが addOrganizationIds に含まれる場合、更新前に弾く（all-or-nothing）")
    void testBulkUpdateValidatesOrganizationsExist() {
        // Given: 団体存在検証で失敗させる
        doThrow(new ResourceNotFoundException("指定された団体が見つかりません"))
                .when(organizationService).validateOrganizationsExist(any());

        PlayerBulkUpdateRequest request = PlayerBulkUpdateRequest.builder()
                .updates(List.of(PlayerBulkUpdateRequest.Item.builder()
                        .playerId(1L).addOrganizationIds(List.of(999L)).build()))
                .build();

        // When & Then: 検証は更新前に行われ、選手・所属は一切変更されない
        assertThatThrownBy(() -> playerService.bulkUpdate(request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(playerRepository, never()).save(any(Player.class));
        verify(organizationService, never()).ensurePlayerBelongsToOrganization(any(), any());
        verify(playerOrganizationRepository, never()).findByPlayerIdIn(any());
    }
}
