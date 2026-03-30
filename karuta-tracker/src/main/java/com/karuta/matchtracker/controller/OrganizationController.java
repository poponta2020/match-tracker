package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.OrganizationDto;
import com.karuta.matchtracker.dto.UpdatePlayerOrganizationsRequest;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * 団体一覧取得
     */
    @GetMapping
    public ResponseEntity<List<OrganizationDto>> getAllOrganizations() {
        return ResponseEntity.ok(organizationService.getAllOrganizations());
    }

    /**
     * ユーザーの参加団体一覧取得
     */
    @GetMapping("/players/{playerId}")
    public ResponseEntity<List<OrganizationDto>> getPlayerOrganizations(
            @PathVariable Long playerId,
            HttpServletRequest httpRequest) {
        checkPlayerAccess(playerId, httpRequest);
        return ResponseEntity.ok(organizationService.getPlayerOrganizations(playerId));
    }

    /**
     * ユーザーの参加団体更新
     */
    @PutMapping("/players/{playerId}")
    public ResponseEntity<List<OrganizationDto>> updatePlayerOrganizations(
            @PathVariable Long playerId,
            @RequestBody UpdatePlayerOrganizationsRequest request,
            HttpServletRequest httpRequest) {
        checkPlayerAccess(playerId, httpRequest);
        return ResponseEntity.ok(organizationService.updatePlayerOrganizations(playerId, request.getOrganizationIds()));
    }

    /**
     * ユーザーが自分自身のデータにアクセスしているか検証する。
     * SUPER_ADMIN は他ユーザーのデータにもアクセス可能。
     */
    private void checkPlayerAccess(Long playerId, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId == null) {
            throw new ForbiddenException("認証が必要です");
        }
        if (!currentUserId.equals(playerId)) {
            String userRole = httpRequest.getHeader("X-User-Role");
            if (!"SUPER_ADMIN".equals(userRole)) {
                throw new ForbiddenException("他のユーザーの参加団体は操作できません");
            }
        }
    }

    /**
     * ADMINの団体紐づけ変更（SUPER_ADMINのみ）
     */
    @RequireRole(Role.SUPER_ADMIN)
    @PutMapping("/admin/{playerId}")
    public ResponseEntity<Void> updateAdminOrganization(
            @PathVariable Long playerId,
            @RequestBody Map<String, Long> request) {
        Long organizationId = request.get("organizationId");
        organizationService.updateAdminOrganization(playerId, organizationId);
        return ResponseEntity.ok().build();
    }
}
