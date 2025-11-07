package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.PlayerProfileCreateRequest;
import com.karuta.matchtracker.dto.PlayerProfileDto;
import com.karuta.matchtracker.service.PlayerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 選手プロフィール管理のRESTコントローラ
 */
@RestController
@RequestMapping("/api/player-profiles")
@RequiredArgsConstructor
@Slf4j
public class PlayerProfileController {

    private final PlayerProfileService playerProfileService;

    /**
     * 選手の現在有効なプロフィールを取得
     *
     * @param playerId 選手ID
     * @return 現在のプロフィール情報
     */
    @GetMapping("/current/{playerId}")
    public ResponseEntity<PlayerProfileDto> getCurrentProfile(@PathVariable Long playerId) {
        log.debug("GET /api/player-profiles/current/{} - Getting current profile", playerId);
        Optional<PlayerProfileDto> profile = playerProfileService.findCurrentProfile(playerId);
        return profile.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 選手の特定日時点のプロフィールを取得
     *
     * @param playerId 選手ID
     * @param date 対象日付
     * @return 対象日のプロフィール情報
     */
    @GetMapping("/at-date/{playerId}")
    public ResponseEntity<PlayerProfileDto> getProfileAtDate(
            @PathVariable Long playerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /api/player-profiles/at-date/{}?date={} - Getting profile at date", playerId, date);
        Optional<PlayerProfileDto> profile = playerProfileService.findProfileAtDate(playerId, date);
        return profile.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 選手のプロフィール履歴を取得
     *
     * @param playerId 選手ID
     * @return プロフィール履歴リスト
     */
    @GetMapping("/history/{playerId}")
    public ResponseEntity<List<PlayerProfileDto>> getProfileHistory(@PathVariable Long playerId) {
        log.debug("GET /api/player-profiles/history/{} - Getting profile history", playerId);
        List<PlayerProfileDto> history = playerProfileService.findProfileHistory(playerId);
        return ResponseEntity.ok(history);
    }

    /**
     * プロフィールを新規登録
     *
     * @param request 登録リクエスト
     * @return 登録されたプロフィール情報
     */
    @PostMapping
    public ResponseEntity<PlayerProfileDto> createProfile(@Valid @RequestBody PlayerProfileCreateRequest request) {
        log.info("POST /api/player-profiles - Creating new profile for player {}", request.getPlayerId());
        PlayerProfileDto createdProfile = playerProfileService.createProfile(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdProfile);
    }

    /**
     * プロフィールの有効期限を設定
     *
     * @param profileId プロフィールID
     * @param validTo 有効期限
     * @return 更新されたプロフィール情報
     */
    @PutMapping("/{profileId}/valid-to")
    public ResponseEntity<PlayerProfileDto> setValidTo(
            @PathVariable Long profileId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo) {
        log.info("PUT /api/player-profiles/{}/valid-to - Setting valid_to to {}", profileId, validTo);
        PlayerProfileDto updatedProfile = playerProfileService.setValidTo(profileId, validTo);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * プロフィールを削除
     *
     * @param profileId プロフィールID
     * @return レスポンスなし
     */
    @DeleteMapping("/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable Long profileId) {
        log.info("DELETE /api/player-profiles/{} - Deleting profile", profileId);
        playerProfileService.deleteProfile(profileId);
        return ResponseEntity.noContent().build();
    }
}
