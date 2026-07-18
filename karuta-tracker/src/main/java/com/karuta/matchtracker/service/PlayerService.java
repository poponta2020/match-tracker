package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LoginRequest;
import com.karuta.matchtracker.dto.LoginResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 選手管理サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final OrganizationService organizationService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;

    /**
     * 全てのアクティブな選手を取得（名前順）
     */
    public List<PlayerDto> findAllActivePlayers() {
        log.debug("Finding all active players");
        List<Player> players = playerRepository.findAllActiveOrderByName();

        // アクティブ選手のみの所属団体IDをバッチ取得（N+1回避）
        List<Long> playerIds = players.stream().map(Player::getId).collect(Collectors.toList());
        Map<Long, List<Long>> orgIdsByPlayerId = playerOrganizationRepository.findByPlayerIdIn(playerIds).stream()
                .collect(Collectors.groupingBy(
                        PlayerOrganization::getPlayerId,
                        Collectors.mapping(PlayerOrganization::getOrganizationId, Collectors.toList())
                ));

        return players.stream()
                .map(p -> PlayerDto.fromEntity(p, orgIdsByPlayerId.getOrDefault(p.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /**
     * IDで選手を取得
     */
    public PlayerDto findById(Long id) {
        log.debug("Finding player by id: {}", id);
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", id));
        return PlayerDto.fromEntity(player);
    }

    /**
     * 名前で選手を検索（アクティブのみ）
     */
    public PlayerDto findByName(String name) {
        log.debug("Finding player by name: {}", name);
        Player player = playerRepository.findByNameAndActive(name)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "name", name));
        return PlayerDto.fromEntity(player);
    }

    /**
     * 名前で部分一致検索
     */
    public List<PlayerDto> searchByName(String nameFragment) {
        log.debug("Searching players by name fragment: {}", nameFragment);
        return playerRepository.findByNameContaining(nameFragment)
                .stream()
                .map(PlayerDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * ロール別でアクティブな選手を取得
     */
    public List<PlayerDto> findByRole(Player.Role role) {
        log.debug("Finding active players by role: {}", role);
        return playerRepository.findByRoleAndActive(role)
                .stream()
                .map(PlayerDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 全選手をキャッシュ付きで取得（60秒 TTL）。
     * DensukeImportService のスケジューラーから毎分呼ばれるため、DBアクセスを抑制する。
     * プレイヤーの作成・更新・削除時に自動的にキャッシュが破棄される。
     */
    @Cacheable("players")
    public List<Player> findAllPlayersRaw() {
        return playerRepository.findAll();
    }

    /**
     * playersキャッシュを明示的にクリアする。
     *
     * PlayerService.createPlayer/updatePlayer/... を経由せず、
     * playerRepository.save() で直接プレイヤーを保存・更新したパスから呼び出すこと。
     * 例: DensukeImportService.registerAndSync() が伝助同期由来で新規プレイヤーを作成した直後、
     * 同一トランザクション内で findAllPlayersRaw() を呼ぶ前にキャッシュを破棄する必要がある。
     */
    @CacheEvict(value = "players", allEntries = true)
    public void evictPlayersCache() {
        // AOP の @CacheEvict によるキャッシュ破棄のみが目的。本体は no-op。
    }

    /**
     * アクティブな選手数を取得
     */
    public long countActivePlayers() {
        log.debug("Counting active players");
        return playerRepository.countActive();
    }

    /**
     * 選手を新規登録
     */
    @Transactional
    @CacheEvict(value = "players", allEntries = true)
    public PlayerDto createPlayer(PlayerCreateRequest request) {
        log.info("Creating new player: {}", request.getName());

        // 名前の重複チェック
        playerRepository.findByNameAndActive(request.getName())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Player", "name", request.getName());
                });

        Player player = request.toEntity(passwordEncoder.encode(request.getPassword()));
        Player saved = playerRepository.save(player);

        log.info("Successfully created player with id: {}", saved.getId());
        return PlayerDto.fromEntity(saved);
    }

    /**
     * 選手情報を更新
     */
    @Transactional
    @CacheEvict(value = "players", allEntries = true)
    public PlayerDto updatePlayer(Long id, PlayerUpdateRequest request) {
        log.info("Updating player with id: {}", id);

        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", id));

        // 削除済みの選手は更新不可
        if (player.isDeleted()) {
            throw new IllegalStateException("Cannot update deleted player: " + id);
        }

        // 名前を変更する場合、重複チェック
        if (request.getName() != null && !request.getName().equals(player.getName())) {
            playerRepository.findByNameAndActive(request.getName())
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Player", "name", request.getName());
                    });
        }

        boolean passwordChanged = request.getPassword() != null;
        String encodedPassword = passwordChanged ? passwordEncoder.encode(request.getPassword()) : null;

        request.applyTo(player, encodedPassword);
        Player updated = playerRepository.save(player);

        // パスワード変更時は発行済みトークンをすべて失効させる（AC-12）
        if (passwordChanged) {
            authTokenService.revokeAllForPlayer(id);
        }

        log.info("Successfully updated player with id: {}", id);
        return PlayerDto.fromEntity(updated);
    }

    /**
     * 複数選手の情報を一括更新（トランザクション・all-or-nothing）。
     *
     * <ul>
     *   <li>各選手の players 列（性別・級・段位・かるた会）を、指定された項目のみ上書きする。</li>
     *   <li>addOrganizationIds の所属団体を追加する（追加のみ・冪等）。既に所属していれば二重登録しない。
     *       追加は {@link OrganizationService#ensurePlayerBelongsToOrganization} 経由で行い、
     *       単体更新・招待登録と同じく団体別の通知設定（push/LINE）も初期化する。</li>
     * </ul>
     *
     * 級↔段位の整合はフロントエンドで算出するため、単体更新と同様にここでは検証しない。
     * 団体スコープ検証は行わない（@RequireRole(ADMIN以上) のみで制御）。
     *
     * @param request 一括更新リクエスト
     * @return 更新後の選手DTOリスト（organizationIds 付き）
     */
    @Transactional
    @CacheEvict(value = "players", allEntries = true)
    public List<PlayerDto> bulkUpdate(PlayerBulkUpdateRequest request) {
        List<PlayerBulkUpdateRequest.Item> updates = request.getUpdates();
        log.info("Bulk updating {} players", updates == null ? 0 : updates.size());

        if (updates == null || updates.isEmpty()) {
            return List.of();
        }

        // 追加対象の団体IDを事前に存在確認（不正IDは更新前に弾き、all-or-nothing を維持。
        // player_organizations にFKが無いため、孤児データの作成をサービス層で防ぐ）
        List<Long> addOrgIdsToValidate = updates.stream()
                .map(PlayerBulkUpdateRequest.Item::getAddOrganizationIds)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!addOrgIdsToValidate.isEmpty()) {
            organizationService.validateOrganizationsExist(addOrgIdsToValidate);
        }

        // 既存の所属団体をバッチ取得（N+1回避）。playerId -> 所属団体IDの可変リスト
        List<Long> playerIds = updates.stream()
                .map(PlayerBulkUpdateRequest.Item::getPlayerId)
                .collect(Collectors.toList());
        Map<Long, List<Long>> existingOrgIdsByPlayerId = playerOrganizationRepository.findByPlayerIdIn(playerIds).stream()
                .collect(Collectors.groupingBy(
                        PlayerOrganization::getPlayerId,
                        Collectors.mapping(PlayerOrganization::getOrganizationId,
                                Collectors.toCollection(ArrayList::new))
                ));

        List<PlayerDto> result = new ArrayList<>();

        for (PlayerBulkUpdateRequest.Item item : updates) {
            Player player = playerRepository.findById(item.getPlayerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Player", item.getPlayerId()));

            // 削除済みの選手は更新不可（単体更新と同一挙動。1件でも該当すれば全体ロールバック）
            if (player.isDeleted()) {
                throw new IllegalStateException("Cannot update deleted player: " + item.getPlayerId());
            }

            // players 列の更新（指定された項目のみ反映）
            if (item.getGender() != null) {
                player.setGender(item.getGender());
            }
            if (item.getKyuRank() != null) {
                player.setKyuRank(item.getKyuRank());
            }
            if (item.getDanRank() != null) {
                player.setDanRank(item.getDanRank());
            }
            if (item.getKarutaClub() != null) {
                player.setKarutaClub(item.getKarutaClub());
            }
            playerRepository.save(player);

            // 所属団体の追加（追加のみ・冪等）
            List<Long> existingOrgIds = existingOrgIdsByPlayerId
                    .computeIfAbsent(player.getId(), k -> new ArrayList<>());
            List<Long> addIds = item.getAddOrganizationIds();
            if (addIds != null) {
                for (Long orgId : addIds) {
                    if (orgId != null && !existingOrgIds.contains(orgId)) {
                        // 単体更新・招待登録と同じ正規経路を通し、団体別の通知設定（push/LINE）も初期化する
                        organizationService.ensurePlayerBelongsToOrganization(player.getId(), orgId);
                        existingOrgIds.add(orgId);
                    }
                }
            }

            result.add(PlayerDto.fromEntity(player, new ArrayList<>(existingOrgIds)));
        }

        log.info("Bulk update completed for {} players", result.size());
        return result;
    }

    /**
     * 選手を論理削除
     */
    @Transactional
    @CacheEvict(value = "players", allEntries = true)
    public void deletePlayer(Long id) {
        log.info("Deleting player with id: {}", id);

        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", id));

        if (player.isDeleted()) {
            log.warn("Player already deleted: {}", id);
            return;
        }

        player.setDeletedAt(JstDateTimeUtil.now());
        playerRepository.save(player);

        // 論理削除した選手の発行済みトークンをすべて失効させる（AC-13）
        authTokenService.revokeAllForPlayer(id);

        log.info("Successfully deleted player with id: {}", id);
    }

    /**
     * 選手のロールを変更（管理者用）
     */
    @Transactional
    @CacheEvict(value = "players", allEntries = true)
    public PlayerDto updateRole(Long id, Player.Role newRole) {
        log.info("Updating role for player id: {} to {}", id, newRole);

        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", id));

        if (player.isDeleted()) {
            throw new IllegalStateException("Cannot update role of deleted player: " + id);
        }

        player.setRole(newRole);
        Player updated = playerRepository.save(player);

        log.info("Successfully updated role for player id: {}", id);
        return PlayerDto.fromEntity(updated);
    }

    /**
     * ログイン認証
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        log.info("Login attempt for user: {}", request.getName());

        Player player = playerRepository.findByNameAndActive(request.getName())
                .orElseThrow(() -> new ResourceNotFoundException("選手名またはパスワードが正しくありません"));

        // パスワードは BCrypt ハッシュで照合する（平文は保存されていない）
        if (!passwordEncoder.matches(request.getPassword(), player.getPassword())) {
            log.warn("Failed login attempt for user: {}", request.getName());
            throw new ResourceNotFoundException("選手名またはパスワードが正しくありません");
        }

        boolean isFirstLogin = player.getLastLoginAt() == null;

        // 最終ログイン日時を更新
        player.setLastLoginAt(JstDateTimeUtil.now());
        playerRepository.save(player);

        // ユーザーの参加団体IDリストを取得
        List<Long> organizationIds = playerOrganizationRepository.findByPlayerId(player.getId()).stream()
                .map(PlayerOrganization::getOrganizationId)
                .collect(Collectors.toList());

        // 認証トークンを発行する。以降のリクエストはこのトークンだけを根拠に本人性を判定する
        String token = authTokenService.issue(player);

        log.info("Successful login for user: {} (firstLogin: {})", request.getName(), isFirstLogin);
        return LoginResponse.fromEntity(player, isFirstLogin, organizationIds, token);
    }

    /**
     * ログアウト（当該トークンのみ失効させる）
     *
     * 未知・失効済みのトークンを渡されても例外にはしない（冪等）。
     *
     * @param rawToken 失効させる生トークン
     */
    @Transactional
    public void logout(String rawToken) {
        authTokenService.revoke(rawToken);
    }
}
