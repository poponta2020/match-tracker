package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting;
import com.karuta.matchtracker.entity.LineNotificationType;
import com.karuta.matchtracker.repository.LineNotificationScheduleSettingRepository;
import com.karuta.matchtracker.service.LineChannelService;
import com.karuta.matchtracker.service.LineNotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LINE管理者向けコントローラ
 */
@RestController
@RequestMapping("/api/admin/line")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class LineAdminController {

    private final LineChannelService lineChannelService;
    private final LineNotificationService lineNotificationService;
    private final LineNotificationScheduleSettingRepository scheduleSettingRepository;
    private final ObjectMapper objectMapper;

    // ========== チャネル管理 ==========

    /**
     * チャネル一覧取得
     */
    @GetMapping("/channels")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<List<LineChannelDto>> getChannels() {
        return ResponseEntity.ok(lineChannelService.getAllChannels());
    }

    /**
     * チャネル個別登録
     */
    @PostMapping("/channels")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<LineChannelDto> createChannel(@Valid @RequestBody LineChannelCreateRequest request) {
        return ResponseEntity.status(201).body(lineChannelService.createChannel(request));
    }

    /**
     * チャネル一括登録（CSV）
     */
    @PostMapping("/channels/import")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Map<String, Integer>> importChannels(@RequestBody List<LineChannelCreateRequest> requests) {
        int count = lineChannelService.importChannels(requests);
        return ResponseEntity.ok(Map.of("importedCount", count));
    }

    /**
     * チャネル無効化
     */
    @PutMapping("/channels/{id}/disable")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Void> disableChannel(@PathVariable Long id) {
        lineChannelService.disableChannel(id);
        return ResponseEntity.ok().build();
    }

    /**
     * チャネル有効化
     */
    @PutMapping("/channels/{id}/enable")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Void> enableChannel(@PathVariable Long id) {
        lineChannelService.enableChannel(id);
        return ResponseEntity.ok().build();
    }

    /**
     * チャネル強制割り当て解除
     */
    @PutMapping("/channels/{id}/release")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Void> releaseChannel(@PathVariable Long id) {
        lineChannelService.forceReleaseChannel(id);
        return ResponseEntity.ok().build();
    }

    // ========== 手動通知送信 ==========

    /**
     * 抽選結果をLINE送信
     */
    @PostMapping("/send/lottery-result")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineSendResultDto> sendLotteryResult(@RequestBody Map<String, Integer> body) {
        int year = body.get("year");
        int month = body.get("month");
        LineSendResultDto result = lineNotificationService.sendLotteryResults(year, month);
        return ResponseEntity.ok(result);
    }

    /**
     * 対戦組み合わせをLINE送信
     */
    @PostMapping("/send/match-pairing")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineSendResultDto> sendMatchPairing(@RequestBody Map<String, Long> body) {
        Long sessionId = body.get("sessionId");
        LineSendResultDto result = lineNotificationService.sendMatchPairings(sessionId);
        return ResponseEntity.ok(result);
    }

    // ========== スケジュール設定 ==========

    /**
     * スケジュール設定一覧取得
     */
    @GetMapping("/schedule-settings")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<LineScheduleSettingDto>> getScheduleSettings() {
        List<LineNotificationScheduleSetting> settings = scheduleSettingRepository.findAll();
        List<LineScheduleSettingDto> dtos = settings.stream().map(s -> {
            List<Integer> daysBefore;
            try {
                daysBefore = objectMapper.readValue(s.getDaysBefore(), new TypeReference<>() {});
            } catch (Exception e) {
                daysBefore = List.of();
            }
            return LineScheduleSettingDto.builder()
                    .notificationType(s.getNotificationType())
                    .enabled(s.getEnabled())
                    .daysBefore(daysBefore)
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    /**
     * スケジュール設定更新
     */
    @PutMapping("/schedule-settings")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> updateScheduleSetting(@RequestBody LineScheduleSettingDto dto) {
        LineNotificationScheduleSetting setting = scheduleSettingRepository
                .findByNotificationType(dto.getNotificationType())
                .orElse(LineNotificationScheduleSetting.builder()
                        .notificationType(dto.getNotificationType())
                        .build());

        setting.setEnabled(dto.getEnabled());
        try {
            setting.setDaysBefore(objectMapper.writeValueAsString(dto.getDaysBefore()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize daysBefore", e);
        }

        scheduleSettingRepository.save(setting);
        return ResponseEntity.ok().build();
    }
}
