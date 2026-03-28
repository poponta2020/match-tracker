package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.OrganizationDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.OrganizationService;
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
    public ResponseEntity<List<OrganizationDto>> getPlayerOrganizations(@PathVariable Long playerId) {
        return ResponseEntity.ok(organizationService.getPlayerOrganizations(playerId));
    }

    /**
     * ユーザーの参加団体更新
     */
    @PutMapping("/players/{playerId}")
    public ResponseEntity<List<OrganizationDto>> updatePlayerOrganizations(
            @PathVariable Long playerId,
            @RequestBody Map<String, List<Long>> request) {
        List<Long> organizationIds = request.get("organizationIds");
        return ResponseEntity.ok(organizationService.updatePlayerOrganizations(playerId, organizationIds));
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
