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

    /** PlayerOrganization.calendar_display_name の DB 上の VARCHAR(50) と一致させる */
    private static final int DISPLAY_NAME_MAX_LENGTH = 50;

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
            @RequestBody(required = false) UpdateDisplayNamesRequest body) {
        Long userId = (Long) request.getAttribute("currentUserId");
        if (body == null || body.getDisplayNames() == null || body.getDisplayNames().isEmpty()) {
            // 空ボディは表示名の変更なしとして扱い、現状を返す
            return ResponseEntity.ok(icalCalendarFeedService.getFeedInfo(userId));
        }
        Map<Long, String> displayNames = new HashMap<>();
        for (Map.Entry<String, String> entry : body.getDisplayNames().entrySet()) {
            long orgId;
            try {
                orgId = Long.parseLong(entry.getKey());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("organizationId は数値で指定してください: " + entry.getKey());
            }
            String value = entry.getValue();
            if (value != null && value.length() > DISPLAY_NAME_MAX_LENGTH) {
                throw new IllegalArgumentException("表示名は" + DISPLAY_NAME_MAX_LENGTH + "文字以下にしてください");
            }
            displayNames.put(orgId, value);
        }
        return ResponseEntity.ok(icalCalendarFeedService.updateDisplayNames(userId, displayNames));
    }
}
