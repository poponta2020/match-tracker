package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.DensukeDeletionCandidateDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.DensukeDeletionCandidateService;
import com.karuta.matchtracker.util.AdminScopeValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 伝助削除候補（伝助側で削除された試合）の一覧・承認・却下を扱う Controller
 */
@RestController
@RequestMapping("/api/densuke-deletion-candidates")
@RequiredArgsConstructor
@Slf4j
public class DensukeDeletionCandidateController {

    private final DensukeDeletionCandidateService densukeDeletionCandidateService;

    /**
     * 指定団体の未承認の削除候補一覧を取得する。
     */
    @GetMapping
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<List<DensukeDeletionCandidateDto>> listPending(
            @RequestParam Long organizationId,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AdminScopeValidator.validateScope(role, adminOrgId, organizationId,
                "他団体の削除候補は取得できません");

        List<DensukeDeletionCandidateDto> dtos = densukeDeletionCandidateService.listPending(organizationId).stream()
                .map(DensukeDeletionCandidateDto::fromEntity)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * 削除候補を承認する。該当試合の出欠エントリを削除する（totalMatches・対戦結果には触れない）。
     * organizationId はクライアントから受け取らず、候補自身が持つ所属団体を正としてスコープ検証する。
     */
    @PostMapping("/{id}/approve")
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<DensukeDeletionCandidateDto> approve(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        densukeDeletionCandidateService.checkAdminScope(id, role, adminOrgId);

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("POST /api/densuke-deletion-candidates/{}/approve by userId={}", id, currentUserId);
        return ResponseEntity.ok(DensukeDeletionCandidateDto.fromEntity(
                densukeDeletionCandidateService.approve(id, currentUserId)));
    }

    /**
     * 削除候補を却下する（データは変更せず、通常表示に戻す）。
     * organizationId はクライアントから受け取らず、候補自身が持つ所属団体を正としてスコープ検証する。
     */
    @PostMapping("/{id}/reject")
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<DensukeDeletionCandidateDto> reject(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        densukeDeletionCandidateService.checkAdminScope(id, role, adminOrgId);

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("POST /api/densuke-deletion-candidates/{}/reject by userId={}", id, currentUserId);
        return ResponseEntity.ok(DensukeDeletionCandidateDto.fromEntity(
                densukeDeletionCandidateService.reject(id, currentUserId)));
    }
}
