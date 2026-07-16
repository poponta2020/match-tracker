package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.MatchPairingService;
import com.karuta.matchtracker.util.OrganizationScopeResolver;
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
    private final OrganizationScopeResolver organizationScopeResolver;

    /**
     * 指定日の対戦組み合わせを取得
     *
     * 閲覧用の組織スコープ (OrganizationScopeResolver#resolveViewingOrganizationId):
     *  - ADMIN / PLAYER: organizationId 任意。未指定なら日付のみ（非限定）。指定時は本人の
     *    所属団体でなければ 403（ADMIN も admin_org 固定ではなく会員団体スコープ）。
     *  - SUPER_ADMIN: organizationId 任意。未指定なら日付のみ。
     *
     * 他団体の会員でもある ADMIN が、その会員団体のペアリングを閲覧できるようにするため、
     * 書き込み系の自団体強制とは分けて会員団体スコープで解決する。
     */
    @GetMapping("/date")
    @RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<List<MatchPairingDto>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false, defaultValue = "false") boolean light,
            @RequestParam(required = false) Long organizationId,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ取得: 日付={}, light={}, organizationId={}", date, light, organizationId);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId != null && !hasSessionOnDateForUser(date, currentUserId)) {
            return ResponseEntity.ok(List.of());
        }
        Long effectiveOrgId = organizationScopeResolver.resolveViewingOrganizationId(httpRequest, organizationId);
        List<MatchPairingDto> pairings = matchPairingService.getByDate(date, light, effectiveOrgId);
        return ResponseEntity.ok(pairings);
    }

    /**
     * 指定日・試合番号の対戦組み合わせを取得
     *
     * 組織スコープは getByDate と同じルール（OrganizationScopeResolver）。
     */
    @GetMapping("/date-and-match")
    @RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<List<MatchPairingDto>> getByDateAndMatchNumber(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            @RequestParam(required = false) Long organizationId,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ取得: 日付={}, 試合番号={}, organizationId={}", date, matchNumber, organizationId);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId != null && !hasSessionOnDateForUser(date, currentUserId)) {
            return ResponseEntity.ok(List.of());
        }
        Long effectiveOrgId = organizationScopeResolver.resolveViewingOrganizationId(httpRequest, organizationId);
        List<MatchPairingDto> pairings = matchPairingService.getByDateAndMatchNumber(date, matchNumber, effectiveOrgId);
        return ResponseEntity.ok(pairings);
    }

    /**
     * 指定選手の最近の対戦組み合わせを取得
     *
     * 動画倉庫の登録モーダル「選手起点」で、結果未入力（match_pairings にのみ存在）の試合も
     * 選択肢に含めるために使用する。指定選手が player1 または player2 に含まれるペアリングを
     * sessionDate DESC, matchNumber DESC の新しい順で直近30件返す。
     *
     * <p>閲覧は全選手可のため団体スコープは適用しない（getByDate 等の書き込み系・組織限定取得とは
     * 用途が異なる、選手別履歴の参照系）。返すのはペアリングであり結果(matches)とは別物。</p>
     */
    @GetMapping("/player/{playerId}")
    @RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<List<MatchPairingDto>> getRecentByPlayer(@PathVariable Long playerId) {
        log.info("選手起点の最近ペアリング取得: playerId={}", playerId);
        List<MatchPairingDto> pairings = matchPairingService.getRecentByPlayerId(playerId);
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
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchPairingDto> create(
            @RequestBody MatchPairingCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ作成: {}", request);
        validateScopeByDate(request.getSessionDate(), httpRequest);

        Long createdBy = (Long) httpRequest.getAttribute("currentUserId");
        Long organizationId = resolveOrganizationIdForScopedWrite(request.getSessionDate(), httpRequest);

        MatchPairingDto created = matchPairingService.create(request, createdBy, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 対戦組み合わせを一括作成
     */
    @PostMapping("/batch")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<MatchPairingDto>> createBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            @RequestBody MatchPairingBatchRequest request,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ一括作成: 日付={}, 試合番号={}, 件数={}, 待機者数={}",
                 date, matchNumber, request.getPairings().size(),
                 request.getWaitingPlayerIds() != null ? request.getWaitingPlayerIds().size() : 0);
        validateScopeByDate(date, httpRequest);

        Long createdBy = (Long) httpRequest.getAttribute("currentUserId");
        Long organizationId = resolveOrganizationIdForScopedWrite(date, httpRequest);

        List<MatchPairingDto> created = matchPairingService.createBatch(date, matchNumber, request.getPairings(), request.getWaitingPlayerIds(), createdBy, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 対戦組み合わせの選手を変更
     */
    @PutMapping("/{id}/player")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchPairingDto> updatePlayer(
            @PathVariable Long id,
            @RequestParam Long newPlayerId,
            @RequestParam String side,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ選手変更: ID={}, newPlayerId={}, side={}", id, newPlayerId, side);
        validateScopeByPairingId(id, httpRequest);
        Long updatedBy = (Long) httpRequest.getAttribute("currentUserId");
        Long organizationId = resolveOrganizationIdForScopedWriteByPairingId(id, httpRequest);
        MatchPairingDto updated = matchPairingService.updatePlayer(id, newPlayerId, side, updatedBy, organizationId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 対戦組み合わせを削除
     */
    @DeleteMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ削除: ID={}", id);
        validateScopeByPairingId(id, httpRequest);
        matchPairingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 指定日・試合番号の対戦組み合わせを削除
     */
    @DeleteMapping("/date-and-match")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Void> deleteByDateAndMatchNumber(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ削除: 日付={}, 試合番号={}", date, matchNumber);
        validateScopeByDate(date, httpRequest);
        Long organizationId = resolveOrganizationIdForScopedWrite(date, httpRequest);
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
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<AutoMatchingResult> autoMatch(
            @RequestBody AutoMatchingRequest request,
            HttpServletRequest httpRequest) {
        log.info("自動マッチング実行: {}", request);
        validateScopeByDate(request.getSessionDate(), httpRequest);
        Long organizationId = resolveOrganizationIdForScopedWrite(request.getSessionDate(), httpRequest);
        AutoMatchingResult result = matchPairingService.autoMatch(request, organizationId);
        return ResponseEntity.ok(result);
    }

    /**
     * ペアリングと対応する試合結果を同時に削除（リセット）
     */
    @DeleteMapping("/{id}/with-result")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchPairingDto> resetWithResult(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせリセット（結果込み）: ID={}", id);
        validateScopeByPairingId(id, httpRequest);
        MatchPairingDto result = matchPairingService.resetWithResult(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 指定組を手動ロックする（二重ブッキング検証付き・即時反映）。
     *
     * 参加者なら誰でも（PLAYER/ADMIN/SUPER_ADMIN）実行可能。組織スコープは
     * {@link #validateScopeByPairingId} で検証し、同回戦・同組織内で対象2選手が
     * 既に別の組に入っている場合はサービス層で 409 Conflict を返す。
     */
    @PatchMapping("/{id}/lock")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchPairingDto> lock(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ手動ロック: ID={}", id);
        validateScopeByPairingId(id, httpRequest);
        Long organizationId = resolveOrganizationIdForScopedWriteByPairingId(id, httpRequest);
        MatchPairingDto result = matchPairingService.lock(id, organizationId);
        return ResponseEntity.ok(result);
    }

    /**
     * 指定組の手動ロックを解除する（即時反映）。組は残り、通常の未ロック組に戻る。
     *
     * 参加者なら誰でも（PLAYER/ADMIN/SUPER_ADMIN）実行可能。組織スコープは
     * {@link #validateScopeByPairingId} で検証する。
     */
    @PatchMapping("/{id}/unlock")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchPairingDto> unlock(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        log.info("対戦組み合わせ手動ロック解除: ID={}", id);
        validateScopeByPairingId(id, httpRequest);
        MatchPairingDto result = matchPairingService.unlock(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 書き込みリクエストの団体スコープ検証（日付ベース）。
     *
     * - SUPER_ADMIN: スコープ強制なし。
     * - ADMIN / PLAYER: 所属団体（player_organizations）のいずれかのセッションが対象日付に
     *   存在しなければ ForbiddenException。ADMIN も admin_org 固定ではなく PLAYER と同じ
     *   会員団体スコープで統一する（他団体の会員でもある ADMIN がその団体の組み合わせを操作できる）。
     * - その他のロール: ForbiddenException。
     */
    private void validateScopeByDate(LocalDate date, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if ("SUPER_ADMIN".equals(role)) return;

        if ("ADMIN".equals(role) || "PLAYER".equals(role)) {
            Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
            if (currentUserId == null) {
                throw new ForbiddenException("他団体の組み合わせは操作できません");
            }
            List<Long> playerOrgIds = organizationService.getPlayerOrganizationIds(currentUserId);
            boolean accessible = practiceSessionRepository.findByDateRange(date, date).stream()
                    .map(com.karuta.matchtracker.entity.PracticeSession::getOrganizationId)
                    .anyMatch(playerOrgIds::contains);
            if (!accessible) {
                throw new ForbiddenException("他団体の組み合わせは操作できません");
            }
            return;
        }

        throw new ForbiddenException("操作権限がありません");
    }

    /**
     * 書き込みリクエストの団体スコープ検証（MatchPairing IDベース：ペアリング所属組織で照合）。
     *
     * - SUPER_ADMIN: スコープ強制なし。
     * - ADMIN / PLAYER: ペアリングの所属組織が本人の所属団体（player_organizations）に
     *   含まれなければ ForbiddenException。ADMIN も admin_org 固定ではなく会員団体スコープで統一。
     * - その他のロール: ForbiddenException。
     */
    private void validateScopeByPairingId(Long pairingId, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if ("SUPER_ADMIN".equals(role)) return;

        Long pairingOrgId = matchPairingService.getOrganizationIdByPairingId(pairingId);
        if (pairingOrgId == null) {
            throw new ForbiddenException("他団体の組み合わせは操作できません");
        }

        if ("ADMIN".equals(role) || "PLAYER".equals(role)) {
            Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
            if (currentUserId == null) {
                throw new ForbiddenException("他団体の組み合わせは操作できません");
            }
            List<Long> playerOrgIds = organizationService.getPlayerOrganizationIds(currentUserId);
            if (!playerOrgIds.contains(pairingOrgId)) {
                throw new ForbiddenException("他団体の組み合わせは操作できません");
            }
            return;
        }

        throw new ForbiddenException("操作権限がありません");
    }

    /**
     * 書き込みリクエストの団体スコープに使う organizationId を、ロールに応じて解決する。
     *
     * - SUPER_ADMIN: null（組織非限定。サービス層は同日全セッションを対象に動作）。
     * - ADMIN / PLAYER: 対象日付の PracticeSession のうち、所属団体に含まれるものの組織ID
     *   （ADMIN も admin_org 固定ではなく会員団体スコープで統一）。2件以上該当する
     *   （複数団体所属で同日に複数団体の練習がある）場合は、どの団体のペアリングを操作
     *   しているか曖昧になり別団体データの誤汚染リスクがあるため ForbiddenException で拒否する
     *   （フロント側で organizationId を明示する仕組みが整うまでの安全側フォールバック）。
     */
    private Long resolveOrganizationIdForScopedWrite(LocalDate date, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if ("ADMIN".equals(role) || "PLAYER".equals(role)) {
            Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
            if (currentUserId == null) return null;
            List<Long> playerOrgIds = organizationService.getPlayerOrganizationIds(currentUserId);
            List<Long> matchedOrgIds = practiceSessionRepository.findByDateRange(date, date).stream()
                    .map(com.karuta.matchtracker.entity.PracticeSession::getOrganizationId)
                    .filter(playerOrgIds::contains)
                    .distinct()
                    .toList();
            if (matchedOrgIds.size() > 1) {
                throw new ForbiddenException(
                        "同日に複数の所属団体で練習があるため、操作対象の団体を特定できません");
            }
            return matchedOrgIds.isEmpty() ? null : matchedOrgIds.get(0);
        }
        return null;
    }

    /**
     * MatchPairing ID を起点に、書き込みリクエストの団体スコープに使う organizationId を解決する。
     *
     * - SUPER_ADMIN: null（組織非限定）。
     * - ADMIN / PLAYER: ペアリングの所属組織ID（既に validateScopeByPairingId で所属団体に
     *   含まれることが検証されている前提。ADMIN も admin_org 固定ではなく会員団体スコープで統一）。
     */
    private Long resolveOrganizationIdForScopedWriteByPairingId(Long pairingId, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if ("ADMIN".equals(role) || "PLAYER".equals(role)) {
            return matchPairingService.getOrganizationIdByPairingId(pairingId);
        }
        return null;
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
