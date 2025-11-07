package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 選手管理のRESTコントローラ
 */
@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerController {

    private final PlayerService playerService;

    /**
     * 全アクティブ選手を取得
     *
     * @return 選手リスト
     */
    @GetMapping
    public ResponseEntity<List<PlayerDto>> getAllPlayers() {
        log.debug("GET /api/players - Getting all active players");
        List<PlayerDto> players = playerService.findAllActivePlayers();
        return ResponseEntity.ok(players);
    }

    /**
     * IDで選手を取得
     *
     * @param id 選手ID
     * @return 選手情報
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlayerDto> getPlayerById(@PathVariable Long id) {
        log.debug("GET /api/players/{} - Getting player by id", id);
        PlayerDto player = playerService.findById(id);
        return ResponseEntity.ok(player);
    }

    /**
     * 名前で選手を検索（部分一致）
     *
     * @param name 検索文字列
     * @return 選手リスト
     */
    @GetMapping("/search")
    public ResponseEntity<List<PlayerDto>> searchPlayers(@RequestParam String name) {
        log.debug("GET /api/players/search?name={} - Searching players", name);
        List<PlayerDto> players = playerService.searchByName(name);
        return ResponseEntity.ok(players);
    }

    /**
     * ロール別で選手を取得
     *
     * @param role ロール
     * @return 選手リスト
     */
    @GetMapping("/role/{role}")
    public ResponseEntity<List<PlayerDto>> getPlayersByRole(@PathVariable Player.Role role) {
        log.debug("GET /api/players/role/{} - Getting players by role", role);
        List<PlayerDto> players = playerService.findByRole(role);
        return ResponseEntity.ok(players);
    }

    /**
     * アクティブな選手数を取得
     *
     * @return 選手数
     */
    @GetMapping("/count")
    public ResponseEntity<Long> countActivePlayers() {
        log.debug("GET /api/players/count - Counting active players");
        long count = playerService.countActivePlayers();
        return ResponseEntity.ok(count);
    }

    /**
     * 選手を新規登録
     *
     * @param request 登録リクエスト
     * @return 登録された選手情報
     */
    @PostMapping
    public ResponseEntity<PlayerDto> createPlayer(@Valid @RequestBody PlayerCreateRequest request) {
        log.info("POST /api/players - Creating new player: {}", request.getName());
        PlayerDto createdPlayer = playerService.createPlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlayer);
    }

    /**
     * 選手情報を更新
     *
     * @param id 選手ID
     * @param request 更新リクエスト
     * @return 更新された選手情報
     */
    @PutMapping("/{id}")
    public ResponseEntity<PlayerDto> updatePlayer(
            @PathVariable Long id,
            @Valid @RequestBody PlayerUpdateRequest request) {
        log.info("PUT /api/players/{} - Updating player", id);
        PlayerDto updatedPlayer = playerService.updatePlayer(id, request);
        return ResponseEntity.ok(updatedPlayer);
    }

    /**
     * 選手を論理削除
     *
     * @param id 選手ID
     * @return レスポンスなし
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlayer(@PathVariable Long id) {
        log.info("DELETE /api/players/{} - Deleting player", id);
        playerService.deletePlayer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 選手のロールを変更
     *
     * @param id 選手ID
     * @param role 新しいロール
     * @return 更新された選手情報
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<PlayerDto> updatePlayerRole(
            @PathVariable Long id,
            @RequestParam Player.Role role) {
        log.info("PUT /api/players/{}/role - Updating role to {}", id, role);
        PlayerDto updatedPlayer = playerService.updateRole(id, role);
        return ResponseEntity.ok(updatedPlayer);
    }
}
