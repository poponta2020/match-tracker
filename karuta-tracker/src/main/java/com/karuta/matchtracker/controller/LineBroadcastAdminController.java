package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.LineBroadcastBotAssignRequest;
import com.karuta.matchtracker.dto.LineBroadcastGroupCreateRequest;
import com.karuta.matchtracker.dto.LineBroadcastGroupDto;
import com.karuta.matchtracker.dto.LineBroadcastGroupUpdateRequest;
import com.karuta.matchtracker.dto.LineBroadcastLogsDto;
import com.karuta.matchtracker.dto.LineBroadcastStatusDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.LineBroadcastAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 全体LINE配信管理者向けAPIコントローラ（配信グループ登録・bot割当・稼働状況/ログ）。
 * すべて ADMIN+ でスコープ制御（ADMIN は自団体・SUPER_ADMIN は全団体）。
 */
@RestController
@RequestMapping("/api/admin/line/broadcast")
@CrossOrigin
@RequiredArgsConstructor
public class LineBroadcastAdminController {

    private final LineBroadcastAdminService lineBroadcastAdminService;

    private String role(HttpServletRequest request) {
        return (String) request.getAttribute("currentUserRole");
    }

    private Long adminOrgId(HttpServletRequest request) {
        return (Long) request.getAttribute("adminOrganizationId");
    }

    /** 配信グループ一覧＋状態 */
    @GetMapping("/groups")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<LineBroadcastGroupDto>> getGroups(HttpServletRequest request) {
        return ResponseEntity.ok(lineBroadcastAdminService.listGroups(role(request), adminOrgId(request)));
    }

    /** 配信グループ作成 */
    @PostMapping("/groups")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineBroadcastGroupDto> createGroup(
            @Valid @RequestBody LineBroadcastGroupCreateRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(
                lineBroadcastAdminService.createGroup(role(request), adminOrgId(request), body));
    }

    /** 配信グループ更新（有効化・名称・想定受信数） */
    @PutMapping("/groups/{groupId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineBroadcastGroupDto> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody LineBroadcastGroupUpdateRequest body, HttpServletRequest request) {
        return ResponseEntity.ok(
                lineBroadcastAdminService.updateGroup(role(request), adminOrgId(request), groupId, body));
    }

    /** bot 割当（未使用チャネルを GROUP に転用） */
    @PostMapping("/groups/{groupId}/bots")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> assignBot(
            @PathVariable Long groupId,
            @Valid @RequestBody LineBroadcastBotAssignRequest body, HttpServletRequest request) {
        lineBroadcastAdminService.assignBot(role(request), adminOrgId(request), groupId, body.getChannelId());
        return ResponseEntity.ok().build();
    }

    /** bot 割当解除（PLAYER プールに戻す） */
    @DeleteMapping("/groups/{groupId}/bots/{channelId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> unassignBot(
            @PathVariable Long groupId, @PathVariable Long channelId, HttpServletRequest request) {
        lineBroadcastAdminService.unassignBot(role(request), adminOrgId(request), groupId, channelId);
        return ResponseEntity.ok().build();
    }

    /** 稼働状況（次配信bot・各bot残枠・当月残り可能回数・枯渇アラート） */
    @GetMapping("/groups/{groupId}/status")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineBroadcastStatusDto> getStatus(
            @PathVariable Long groupId, HttpServletRequest request) {
        return ResponseEntity.ok(lineBroadcastAdminService.getStatus(role(request), adminOrgId(request), groupId));
    }

    /** 配信ログ一覧＋枯渇アラート状態 */
    @GetMapping("/groups/{groupId}/logs")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineBroadcastLogsDto> getLogs(
            @PathVariable Long groupId, HttpServletRequest request) {
        return ResponseEntity.ok(lineBroadcastAdminService.getLogs(role(request), adminOrgId(request), groupId));
    }
}
