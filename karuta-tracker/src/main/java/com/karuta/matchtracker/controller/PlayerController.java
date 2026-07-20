package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.LoginRequest;
import com.karuta.matchtracker.dto.LoginResponse;
import com.karuta.matchtracker.dto.PlayerBulkUpdateRequest;
import com.karuta.matchtracker.dto.PlayerCreateRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PlayerUpdateRequest;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.PlayerService;
import com.karuta.matchtracker.util.BearerTokenExtractor;
import jakarta.servlet.http.HttpServletRequest;
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
     * ログイン
     *
     * @param request ログインリクエスト
     * @return ログインレスポンス
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/players/login - Login attempt for: {}", request.getName());
        LoginResponse response = playerService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * ログアウト（当該トークンのみ失効させる）
     *
     * 認証必須（許可リスト外のため、有効なトークンが無ければインターセプタが 401 を返す）。
     *
     * @param httpRequest Authorization ヘッダーから失効対象のトークンを取得するために使用
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        log.info("POST /api/players/logout");
        playerService.logout(BearerTokenExtractor.extract(httpRequest));
        return ResponseEntity.noContent().build();
    }

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
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<PlayerDto> createPlayer(@Valid @RequestBody PlayerCreateRequest request) {
        log.info("POST /api/players - Creating new player: {}", request.getName());
        PlayerDto createdPlayer = playerService.createPlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPlayer);
    }

    /**
     * 複数選手の情報を一括更新（性別・級・段位・かるた会の上書き、所属練習会の追加）
     *
     * ADMIN / SUPER_ADMIN のみ利用可。対象選手の団体スコープ検証は行わない（トラストベース）。
     * "/bulk" は "/{id}" より優先してマッチするため、単体更新エンドポイントと競合しない。
     *
     * @param request 一括更新リクエスト
     * @return 更新された選手情報のリスト
     */
    @PutMapping("/bulk")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<PlayerDto>> bulkUpdatePlayers(
            @Valid @RequestBody PlayerBulkUpdateRequest request) {
        int count = request.getUpdates() == null ? 0 : request.getUpdates().size();
        log.info("PUT /api/players/bulk - Bulk updating {} players", count);
        List<PlayerDto> updatedPlayers = playerService.bulkUpdate(request);
        return ResponseEntity.ok(updatedPlayers);
    }

    /**
     * 選手情報を更新
     *
     * @param id 選手ID
     * @param request 更新リクエスト
     * @return 更新された選手情報
     */
    @PutMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<PlayerDto> updatePlayer(
            @PathVariable Long id,
            @Valid @RequestBody PlayerUpdateRequest request,
            HttpServletRequest httpRequest) {
        log.info("PUT /api/players/{} - Updating player", id);
        checkSelfOrSuperAdmin(id, httpRequest);
        PlayerDto updatedPlayer = playerService.updatePlayer(id, request);
        return ResponseEntity.ok(updatedPlayer);
    }

    /**
     * 本人または SUPER_ADMIN のみに操作を許可する。
     *
     * PlayerUpdateRequest は password を含むため、ここを開けると
     * 任意アカウントのパスワードを書き換えて成りすませてしまう（Issue #1105）。
     */
    private void checkSelfOrSuperAdmin(Long targetPlayerId, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId == null) {
            throw new ForbiddenException("認証が必要です");
        }
        if (currentUserId.equals(targetPlayerId)) {
            return;
        }
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));
        if (currentUserRole != Role.SUPER_ADMIN) {
            throw new ForbiddenException("他のユーザーの情報は更新できません");
        }
    }

    /**
     * 選手を論理削除
     *
     * @param id 選手ID
     * @return レスポンスなし
     */
    @DeleteMapping("/{id}")
    @RequireRole(Role.SUPER_ADMIN)
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
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<PlayerDto> updatePlayerRole(
            @PathVariable Long id,
            @RequestParam Player.Role role) {
        log.info("PUT /api/players/{}/role - Updating role to {}", id, role);
        PlayerDto updatedPlayer = playerService.updateRole(id, role);
        return ResponseEntity.ok(updatedPlayer);
    }
}
