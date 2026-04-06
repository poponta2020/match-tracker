package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.MatchPairingService;
import com.karuta.matchtracker.util.AdminScopeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/match-pairings")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class MatchPairingController {

    private final MatchPairingService matchPairingService;
    private final com.karuta.matchtracker.service.OrganizationService organizationService;
    private final com.karuta.matchtracker.repository.PracticeSessionRepository practiceSessionRepository;

    /**
     * 指定日の対戦組み合わせを取得
     */
    @GetMapping("/date")
    public ResponseEntity<List<MatchPairingDto>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "false") boolean light,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ取得: 日付={}, light={}", date, light);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId != null && !hasSessionOnDateForUser(date, currentUserId)) {
            return ResponseEntity.ok(List.of());
        }
        List<MatchPairingDto> pairings = matchPairingService.getByDate(date, light);
        return ResponseEntity.ok(pairings);
    }

    /**
     * 指定日・試合番号の対戦組み合わせを取得
     */
    @GetMapping("/date-and-match")
    public ResponseEntity<List<MatchPairingDto>> getByDateAndMatchNumber(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ取得: 日付={}, 試合番号={}", date, matchNumber);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId != null && !hasSessionOnDateForUser(date, currentUserId)) {
            return ResponseEntity.ok(List.of());
        }
        List<MatchPairingDto> pairings = matchPairingService.getByDateAndMatchNumber(date, matchNumber);
        return ResponseEntity.ok(pairings);
    }

    /**
     * 対戦組み合わせが存在するか確認
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> exists(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber) {
        log.info("対戦組み合わせ存在確認: 日付={}, 試合番号={}", date, matchNumber);
        boolean exists = matchPairingService.existsByDateAndMatchNumber(date, matchNumber);
        return ResponseEntity.ok(exists);
    }

    /**
     * 対戦組み合わせを作成
     */
    @PostMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<MatchPairingDto> create(
            @RequestBody MatchPairingCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ作成: {}", request);
        validateAdminScopeByDate(request.getSessionDate(), httpRequest);

        Long createdBy = (Long) httpRequest.getAttribute("currentUserId");

        MatchPairingDto created = matchPairingService.create(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 対戦組み合わせを一括作成
     */
    @PostMapping("/batch")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<MatchPairingDto>> createBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            @RequestBody MatchPairingBatchRequest request,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ一括作成: 日付={}, 試合番号={}, 件数={}, 待機者数={}",
                 date, matchNumber, request.getPairings().size(),
                 request.getWaitingPlayerIds() != null ? request.getWaitingPlayerIds().size() : 0);
        validateAdminScopeByDate(date, httpRequest);

        Long createdBy = (Long) httpRequest.getAttribute("currentUserId");
        Long organizationId = (Long) httpRequest.getAttribute("adminOrganizationId");

        List<MatchPairingDto> created = matchPairingService.createBatch(date, matchNumber, request.getPairings(), request.getWaitingPlayerIds(), createdBy, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 対戦組み合わせの選手を変更
     */
    @PutMapping("/{id}/player")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<MatchPairingDto> updatePlayer(
            @PathVariable Long id,
            @RequestParam Long newPlayerId,
            @RequestParam String side,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ選手変更: ID={}, newPlayerId={}, side={}", id, newPlayerId, side);
        validateAdminScopeByPairingId(id, httpRequest);
        Long updatedBy = (Long) httpRequest.getAttribute("currentUserId");
        MatchPairingDto updated = matchPairingService.updatePlayer(id, newPlayerId, side, updatedBy);
        return ResponseEntity.ok(updated);
    }

    /**
     * 対戦組み合わせを削除
     */
    @DeleteMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ削除: ID={}", id);
        validateAdminScopeByPairingId(id, httpRequest);
        matchPairingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 指定日・試合番号の対戦組み合わせを削除
     */
    @DeleteMapping("/date-and-match")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> deleteByDateAndMatchNumber(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ削除: 日付={}, 試合番号={}", date, matchNumber);
        validateAdminScopeByDate(date, httpRequest);
        Long organizationId = (Long) httpRequest.getAttribute("adminOrganizationId");
        matchPairingService.deleteByDateAndMatchNumber(date, matchNumber, organizationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * ペアの直近対戦履歴を取得（手動組み合わせ時のリアルタイム表示用）
     */
    @GetMapping("/pair-history")
    public ResponseEntity<List<AutoMatchingResult.MatchHistory>> getPairHistory(
            @RequestParam Long player1Id,
            @RequestParam Long player2Id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate sessionDate,
            @RequestParam(required = false) Integer matchNumber) {
        List<AutoMatchingResult.MatchHistory> history =
                matchPairingService.getPairRecentMatches(player1Id, player2Id, sessionDate, matchNumber);
        return ResponseEntity.ok(history);
    }

    /**
     * 自動マッチングを実行
     */
    @PostMapping("/auto-match")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<AutoMatchingResult> autoMatch(
            @RequestBody AutoMatchingRequest request,
            HttpServletRequest httpRequest) {
        log.info("自動マッチング実行: {}", request);
        validateAdminScopeByDate(request.getSessionDate(), httpRequest);
        Long organizationId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AutoMatchingResult result = matchPairingService.autoMatch(request, organizationId);
        return ResponseEntity.ok(result);
    }

    /**
     * ペアリングと対応する試合結果を同時に削除（リセット）
     */
    @DeleteMapping("/{id}/with-result")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<MatchPairingDto> resetWithResult(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせリセット（結果込み）: ID={}", id);
        validateAdminScopeByPairingId(id, httpRequest);
        MatchPairingDto result = matchPairingService.resetWithResult(id);
        return ResponseEntity.ok(result);
    }

    /**
     * ADMINスコープ検証（日付ベース）
     */
    private void validateAdminScopeByDate(LocalDate date, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if (!"ADMIN".equals(role)) return;

        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        if (adminOrgId == null) {
            throw new ForbiddenException("他団体の組み合わせは操作できません");
        }
        practiceSessionRepository.findBySessionDateAndOrganizationId(date, adminOrgId)
                .orElseThrow(() -> new ForbiddenException("他団体の組み合わせは操作できません"));
    }

    /**
     * ADMINスコープ検証（MatchPairing IDベース：ペアリング所属組織で照合）
     */
    private void validateAdminScopeByPairingId(Long pairingId, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if (!"ADMIN".equals(role)) return;

        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        if (adminOrgId == null) {
            throw new ForbiddenException("他団体の組み合わせは操作できません");
        }
        Long pairingOrgId = matchPairingService.getOrganizationIdByPairingId(pairingId);
        if (!adminOrgId.equals(pairingOrgId)) {
            throw new ForbiddenException("他団体の組み合わせは操作できません");
        }
    }

    private boolean hasSessionOnDateForUser(LocalDate date, Long userId) {
        List<Long> orgIds = organizationService.getPlayerOrganizationIds(userId);
        if (orgIds.isEmpty()) return true;
        List<com.karuta.matchtracker.entity.PracticeSession> sessions =
                practiceSessionRepository.findByDateRange(date, date);
        if (sessions.isEmpty()) return true;
        return sessions.stream().anyMatch(s ->
                s.getOrganizationId() == null || orgIds.contains(s.getOrganizationId()));
    }
}
