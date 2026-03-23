package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.NotificationDto;
import com.karuta.matchtracker.service.NotificationService;
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
    public ResponseEntity<List<NotificationDto>> getNotifications(@RequestParam Long playerId) {
        return ResponseEntity.ok(notificationService.getNotifications(playerId));
    }

    /**
     * 未読通知数取得
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@RequestParam Long playerId) {
        long count = notificationService.getUnreadCount(playerId);
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * 通知を既読にする
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}
