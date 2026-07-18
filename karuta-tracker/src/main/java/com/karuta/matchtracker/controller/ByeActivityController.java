package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.ByeActivity;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.ByeActivityRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.ByeActivityService;
import com.karuta.matchtracker.service.OrganizationService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/bye-activities")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class ByeActivityController {

    private final ByeActivityService byeActivityService;
    private final ByeActivityRepository byeActivityRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final OrganizationService organizationService;

    /**
     * 指定日の抜け番活動を取得（matchNumber指定時はその試合のみ）
     */
    @GetMapping
    public ResponseEntity<List<ByeActivityDto>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer matchNumber) {
        log.info("抜け番活動取得: date={}, matchNumber={}", date, matchNumber);
        List<ByeActivityDto> result;
        if (matchNumber != null) {
            result = byeActivityService.getByDateAndMatch(date, matchNumber);
        } else {
            result = byeActivityService.getByDate(date);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 選手別の活動履歴を取得（集計用）
     */
    @GetMapping("/player/{playerId}")
    public ResponseEntity<List<ByeActivityDto>> getByPlayer(
            @PathVariable Long playerId,
            @RequestParam(required = false) ActivityType type) {
        log.info("選手別抜け番活動取得: playerId={}, type={}", playerId, type);
        List<ByeActivityDto> result = byeActivityService.getByPlayer(playerId, type);
        return ResponseEntity.ok(result);
    }

    /**
     * 抜け番活動を作成（本人入力）
     * Service層でplayerId == userIdの検証を実施
     */
    @PostMapping
    public ResponseEntity<ByeActivityDto> create(
            @Valid @RequestBody ByeActivityCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("抜け番活動作成: {}", request);
        Long userId = (Long) httpRequest.getAttribute("currentUserId");
        ByeActivityDto created = byeActivityService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 抜け番活動を一括作成（PLAYER+: 所属団体スコープ強制）
     *
     * PLAYER もペアリング一括入力経路から抜け番活動をまとめて保存できるよう開放。
     * ADMIN は自団体、PLAYER は所属団体のセッションに限り操作可能。
     */
    @PostMapping("/batch")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<ByeActivityDto>> createBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            @Valid @RequestBody List<ByeActivityBatchItemRequest> items,
            HttpServletRequest httpRequest) {
        log.info("抜け番活動一括作成: date={}, matchNumber={}, count={}", date, matchNumber, items.size());
        validateScopeByDate(date, httpRequest);
        Long userId = (Long) httpRequest.getAttribute("currentUserId");
        Long organizationId = resolveOrganizationIdForScopedWrite(date, httpRequest);
        List<ByeActivityDto> created = byeActivityService.createBatch(date, matchNumber, items, userId, organizationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 抜け番活動を更新（本人 or 管理者）
     * 本人の場合はplayerIdの一致を検証
     */
    @PutMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<ByeActivityDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ByeActivityUpdateRequest request,
            HttpServletRequest httpRequest) {
        log.info("抜け番活動更新: id={}, type={}", id, request.getActivityType());
        Long userId = (Long) httpRequest.getAttribute("currentUserId");
        // PLAYER は本人の記録のみ更新可（ADMIN+ は代理修正のため他人も可）。
        // この検証は javadoc に書かれながら長らく未配線だった（Issue #1105）。
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));
        if (currentUserRole == Role.PLAYER) {
            Long ownerId = byeActivityService.getPlayerIdForActivity(id);
            if (!ownerId.equals(userId)) {
                throw new ForbiddenException("他の選手の抜け番活動は更新できません");
            }
        }
        ByeActivityDto updated = byeActivityService.update(id, request, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 抜け番活動を削除（管理者のみ）
     */
    @DeleteMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        log.info("抜け番活動削除: id={}", id);
        ByeActivity activity = byeActivityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ByeActivity", id));
        validateScopeByDate(activity.getSessionDate(), httpRequest);
        byeActivityService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 書き込みリクエストの団体スコープ検証（日付ベース）。
     *
     * - SUPER_ADMIN: スコープなし
     * - ADMIN: 自団体のセッションが対象日付に存在しなければ ForbiddenException
     * - PLAYER: 所属団体のいずれかのセッションが対象日付に存在しなければ ForbiddenException
     */
    private void validateScopeByDate(LocalDate date, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if ("SUPER_ADMIN".equals(role)) return;

        if ("ADMIN".equals(role)) {
            Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
            if (adminOrgId == null) {
                throw new ForbiddenException("他団体の抜け番活動は操作できません");
            }
            practiceSessionRepository.findBySessionDateAndOrganizationId(date, adminOrgId)
                    .orElseThrow(() -> new ForbiddenException("他団体の抜け番活動は操作できません"));
            return;
        }

        if ("PLAYER".equals(role)) {
            Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
            if (currentUserId == null) {
                throw new ForbiddenException("他団体の抜け番活動は操作できません");
            }
            List<Long> playerOrgIds = organizationService.getPlayerOrganizationIds(currentUserId);
            boolean accessible = practiceSessionRepository.findByDateRange(date, date).stream()
                    .map(com.karuta.matchtracker.entity.PracticeSession::getOrganizationId)
                    .anyMatch(playerOrgIds::contains);
            if (!accessible) {
                throw new ForbiddenException("他団体の抜け番活動は操作できません");
            }
            return;
        }

        throw new ForbiddenException("操作権限がありません");
    }

    /**
     * 書き込みリクエストの団体スコープに使う organizationId を、ロールに応じて解決する。
     *
     * - SUPER_ADMIN: null（組織非限定）。
     * - ADMIN: adminOrganizationId。
     * - PLAYER: 対象日付の PracticeSession のうち所属団体に属するものの組織ID。
     *   2件以上該当する場合は ForbiddenException で安全側拒否（団体一意特定不能）。
     */
    private Long resolveOrganizationIdForScopedWrite(LocalDate date, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if ("ADMIN".equals(role)) {
            return (Long) httpRequest.getAttribute("adminOrganizationId");
        }
        if ("PLAYER".equals(role)) {
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
}
