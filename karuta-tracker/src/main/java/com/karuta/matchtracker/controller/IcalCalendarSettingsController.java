package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.FeedInfoDto;
import com.karuta.matchtracker.dto.UpdateDisplayNamesRequest;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.IcalCalendarFeedService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * カレンダーフィード設定用エンドポイント（認証必須）
 *
 * 自分のフィードURL取得、トークン再発行、団体表示名カスタマイズを提供する。
 * playerId は X-User-Id ヘッダーから RoleCheckInterceptor がリクエスト属性にセットしたものを使う。
 */
@RestController
@RequestMapping("/api/calendar/feed")
@RequiredArgsConstructor
@Slf4j
public class IcalCalendarSettingsController {

    private final IcalCalendarFeedService icalCalendarFeedService;

    @GetMapping("/info")
    @RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<FeedInfoDto> getInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return ResponseEntity.ok(icalCalendarFeedService.getFeedInfo(userId));
    }

    @PostMapping("/regenerate")
    @RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<FeedInfoDto> regenerate(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        icalCalendarFeedService.regenerateTokenForPlayer(userId);
        return ResponseEntity.ok(icalCalendarFeedService.getFeedInfo(userId));
    }

    @PatchMapping("/display-names")
    @RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<FeedInfoDto> updateDisplayNames(
            HttpServletRequest request,
            @RequestBody UpdateDisplayNamesRequest body) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Map<String, String> raw = body.getDisplayNames() != null ? body.getDisplayNames() : Collections.emptyMap();
        Map<Long, String> displayNames = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                displayNames.put(Long.parseLong(k), v);
            } catch (NumberFormatException e) {
                log.warn("Invalid organizationId in display-names request: {}", k);
            }
        });
        return ResponseEntity.ok(icalCalendarFeedService.updateDisplayNames(userId, displayNames));
    }
}
