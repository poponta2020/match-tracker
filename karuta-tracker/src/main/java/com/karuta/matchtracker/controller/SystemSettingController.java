package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.SystemSetting;
import com.karuta.matchtracker.service.SystemSettingService;
import com.karuta.matchtracker.util.AdminScopeValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * システム設定コントローラ
 */
@RestController
@RequestMapping("/api/system-settings")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    /**
     * 団体ごとの設定取得
     * ADMINは自団体のみ、SUPER_ADMINはorganizationIdを指定
     */
    @GetMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<SystemSetting>> getAll(
            @RequestParam(required = false) Long organizationId,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");

        // ADMINは自団体に強制（明示的に他団体が指定された場合は拒否）
        if (organizationId != null) {
            AdminScopeValidator.validateScope(role, adminOrgId, organizationId,
                    "他団体のシステム設定は参照できません");
        }
        Long targetOrgId = "ADMIN".equals(role) ? adminOrgId
                : (organizationId != null ? organizationId : adminOrgId);
        if (targetOrgId != null) {
            return ResponseEntity.ok(systemSettingService.getAllByOrganization(targetOrgId));
        }
        return ResponseEntity.ok(systemSettingService.getAll());
    }

    /**
     * 設定値取得（団体指定）
     */
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, String>> getValue(
            @PathVariable String key,
            @RequestParam(required = false) Long organizationId) {
        String value = systemSettingService.getValue(key, organizationId).orElse(null);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    /**
     * 設定値更新（団体指定）
     * ADMINは自団体のみ、SUPER_ADMINはorganizationIdを指定
     */
    @PutMapping("/{key}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<SystemSetting> setValue(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");

        Long organizationId = body.get("organizationId") != null
                ? Long.parseLong(body.get("organizationId"))
                : null;
        // ADMINは自団体に強制（明示的に他団体が指定された場合は拒否）
        if (organizationId != null) {
            AdminScopeValidator.validateScope(role, adminOrgId, organizationId,
                    "他団体のシステム設定は更新できません");
        }
        Long targetOrgId = "ADMIN".equals(role) ? adminOrgId
                : (organizationId != null ? organizationId : adminOrgId);

        if (targetOrgId == null) {
            return ResponseEntity.badRequest().build();
        }

        SystemSetting setting = systemSettingService.setValue(key, body.get("value"), targetOrgId, currentUserId);
        return ResponseEntity.ok(setting);
    }
}
