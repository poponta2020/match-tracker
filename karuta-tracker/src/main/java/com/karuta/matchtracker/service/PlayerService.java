package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LoginRequest;
import com.karuta.matchtracker.dto.LoginResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

        Player player = request.toEntity();
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

        request.applyTo(player);
        Player updated = playerRepository.save(player);

        log.info("Successfully updated player with id: {}", id);
        return PlayerDto.fromEntity(updated);
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

        // 簡易実装: パスワードを平文で比較（本番環境ではBCryptを使用すべき）
        if (!request.getPassword().equals(player.getPassword())) {
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

        log.info("Successful login for user: {} (firstLogin: {})", request.getName(), isFirstLogin);
        return LoginResponse.fromEntity(player, isFirstLogin, organizationIds);
    }
}
