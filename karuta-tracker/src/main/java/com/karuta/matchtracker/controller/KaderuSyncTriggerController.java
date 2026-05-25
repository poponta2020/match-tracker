package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.KaderuSyncStatusResponse;
import com.karuta.matchtracker.dto.KaderuSyncTriggerEventDto;
import com.karuta.matchtracker.dto.KaderuSyncTriggerRequest;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.KaderuSyncTriggerService;
import com.karuta.matchtracker.util.OrganizationScopeResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Kaderu 予約取り込み手動トリガー API。
 *
 * <ul>
 *   <li>{@code POST /api/kaderu-sync/trigger}: GitHub Actions の workflow_dispatch
 *       を発火し、PENDING イベントを返す。同一団体の PENDING が既にあれば 409。</li>
 *   <li>{@code GET /api/kaderu-sync/status}: 現在の PENDING イベント（あれば）を返す。
 *       フロントが30秒間隔でポーリングし、ボタンの活性/非活性に使う。</li>
 * </ul>
 *
 * <p>ADMIN は自団体のみ。SUPER_ADMIN は任意の団体を {@code organizationId} で指定する。
 * ロール判定とスコープ解決は {@link OrganizationScopeResolver} に委譲する。
 */
@RestController
@RequestMapping("/api/kaderu-sync")
@RequiredArgsConstructor
@Slf4j
public class KaderuSyncTriggerController {

    private final KaderuSyncTriggerService kaderuSyncTriggerService;
    private final OrganizationScopeResolver organizationScopeResolver;

    @PostMapping("/trigger")
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<KaderuSyncTriggerEventDto> trigger(
            @RequestBody(required = false) KaderuSyncTriggerRequest request,
            HttpServletRequest httpRequest) {

        Long requestedOrgId = request != null ? request.getOrganizationId() : null;
        Long effectiveOrgId = organizationScopeResolver.resolveEffectiveOrganizationId(httpRequest, requestedOrgId);
        if (effectiveOrgId == null) {
            // SUPER_ADMIN が organizationId 未指定で叩いたケース
            throw new IllegalArgumentException("organizationId は必須です");
        }

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        log.info("POST /api/kaderu-sync/trigger by playerId={} for organizationId={}", currentUserId, effectiveOrgId);

        KaderuSyncTriggerEventDto dto = kaderuSyncTriggerService.triggerSync(currentUserId, effectiveOrgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/status")
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<KaderuSyncStatusResponse> getStatus(
            @RequestParam(required = false) Long organizationId,
            HttpServletRequest httpRequest) {

        Long effectiveOrgId = organizationScopeResolver.resolveEffectiveOrganizationId(httpRequest, organizationId);
        if (effectiveOrgId == null) {
            // SUPER_ADMIN が指定しないケースは PENDING なし扱い（フロント側はデフォルトでボタン活性のまま）
            return ResponseEntity.ok(KaderuSyncStatusResponse.builder().pendingEvent(null).build());
        }
        return ResponseEntity.ok(kaderuSyncTriggerService.getStatus(effectiveOrgId));
    }
}
