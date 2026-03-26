package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.SystemSetting;
import com.karuta.matchtracker.service.SystemSettingService;
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
     * 全設定取得
     */
    @GetMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<SystemSetting>> getAll() {
        return ResponseEntity.ok(systemSettingService.getAll());
    }

    /**
     * 設定値取得
     */
    @GetMapping("/{key}")
    public ResponseEntity<Map<String, String>> getValue(@PathVariable String key) {
        String value = systemSettingService.getValue(key).orElse(null);
        if (value == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("key", key, "value", value));
    }

    /**
     * 設定値更新
     */
    @PutMapping("/{key}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<SystemSetting> setValue(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        SystemSetting setting = systemSettingService.setValue(key, body.get("value"), currentUserId);
        return ResponseEntity.ok(setting);
    }
}
