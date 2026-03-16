package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.GoogleCalendarSyncRequest;
import com.karuta.matchtracker.dto.GoogleCalendarSyncResponse;
import com.karuta.matchtracker.service.GoogleCalendarSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/google-calendar")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class GoogleCalendarController {

    private final GoogleCalendarSyncService googleCalendarSyncService;

    /**
     * Google Calendarと同期
     */
    @PostMapping("/sync")
    public ResponseEntity<?> sync(@RequestBody GoogleCalendarSyncRequest request) {
        if (request.getAccessToken() == null || request.getAccessToken().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "アクセストークンが必要です"));
        }
        if (request.getPlayerId() == null) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "プレイヤーIDが必要です"));
        }

        log.info("POST /api/google-calendar/sync - player {}", request.getPlayerId());

        try {
            GoogleCalendarSyncResponse result =
                googleCalendarSyncService.sync(request.getAccessToken(), request.getPlayerId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Google Calendar sync failed", e);
            return ResponseEntity.status(502)
                .body(Map.of("message", "Google Calendarとの同期に失敗しました: " + e.getMessage()));
        }
    }
}
