package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.DensukeTemplateDto;
import com.karuta.matchtracker.dto.DensukeTemplateUpdateRequest;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.DensukeTemplateService;
import com.karuta.matchtracker.util.AdminScopeValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 伝助テンプレート管理 Controller
 */
@RestController
@RequestMapping("/api/densuke-templates")
@RequiredArgsConstructor
@Slf4j
public class DensukeTemplateController {

    private final DensukeTemplateService densukeTemplateService;

    /**
     * 指定団体のテンプレートを取得する。未登録団体にはデフォルト値を返す。
     */
    @GetMapping("/{organizationId}")
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<DensukeTemplateDto> getTemplate(
            @PathVariable Long organizationId,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AdminScopeValidator.validateScope(role, adminOrgId, organizationId,
                "他団体のテンプレートは取得できません");

        return ResponseEntity.ok(densukeTemplateService.getTemplate(organizationId));
    }

    /**
     * 指定団体のテンプレートを upsert する。
     */
    @PutMapping("/{organizationId}")
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<DensukeTemplateDto> updateTemplate(
            @PathVariable Long organizationId,
            @Valid @RequestBody DensukeTemplateUpdateRequest request,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AdminScopeValidator.validateScope(role, adminOrgId, organizationId,
                "他団体のテンプレートは更新できません");

        log.info("PUT /api/densuke-templates/{} - Updating template", organizationId);
        return ResponseEntity.ok(densukeTemplateService.updateTemplate(organizationId, request));
    }
}
