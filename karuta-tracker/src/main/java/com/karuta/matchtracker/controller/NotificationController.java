package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.NotificationDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 通知のRESTコントローラ
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 通知一覧取得
     */
    @GetMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<NotificationDto>> getNotifications(
            @RequestParam Long playerId, HttpServletRequest httpRequest) {
        validatePlayerAccess(playerId, httpRequest);
        return ResponseEntity.ok(notificationService.getNotifications(playerId));
    }

    /**
     * 未読通知数取得
     */
    @GetMapping("/unread-count")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @RequestParam Long playerId, HttpServletRequest httpRequest) {
        validatePlayerAccess(playerId, httpRequest);
        long count = notificationService.getUnreadCount(playerId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * 通知を既読にする
     */
    @PutMapping("/{id}/read")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        notificationService.markAsRead(id, currentUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * 通知を一括削除（論理削除）
     */
    @DeleteMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Map<String, Integer>> deleteAll(
            @RequestParam Long playerId, HttpServletRequest httpRequest) {
        validatePlayerAccess(playerId, httpRequest);
        int count = notificationService.deleteAllByPlayerId(playerId);
        return ResponseEntity.ok(Map.of("deleted", count));
    }

    /**
     * PLAYERロールの場合、自分のplayerIdのみアクセス可能か検証する
     */
    private void validatePlayerAccess(Long playerId, HttpServletRequest httpRequest) {
        Role currentUserRole = (Role) httpRequest.getAttribute("currentUserRole");
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserRole == Role.PLAYER && !playerId.equals(currentUserId)) {
            throw new ForbiddenException("他のプレイヤーの通知にはアクセスできません");
        }
    }
}
