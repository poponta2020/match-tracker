package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting.ScheduleNotificationType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.repository.LineNotificationScheduleSettingRepository;
import com.karuta.matchtracker.service.LineChannelService;
import com.karuta.matchtracker.service.LineNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LINE通知管理者向けAPIコントローラ
 */
@RestController
@RequestMapping("/api/admin/line")
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
public class LineAdminController {

    private final LineChannelService lineChannelService;
    private final LineNotificationService lineNotificationService;
    private final LineNotificationScheduleSettingRepository scheduleSettingRepository;
    private final ObjectMapper objectMapper;

    /**
     * チャネル一覧を取得する
     */
    @GetMapping("/channels")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<List<LineChannelDto>> getChannels() {
        return ResponseEntity.ok(lineChannelService.getAllChannels());
    }

    /**
     * チャネルを登録する（個別）
     */
    @PostMapping("/channels")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<LineChannelDto> createChannel(@Valid @RequestBody LineChannelCreateRequest request) {
        var channel = lineChannelService.createChannel(request);
        return ResponseEntity.ok(LineChannelDto.fromEntity(channel));
    }

    /**
     * チャネルを一括登録する（CSV）
     */
    @PostMapping("/channels/import")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Map<String, Integer>> importChannels(@RequestBody List<LineChannelCreateRequest> channels) {
        int count = 0;
        for (LineChannelCreateRequest req : channels) {
            lineChannelService.createChannel(req);
            count++;
        }
        return ResponseEntity.ok(Map.of("importedCount", count));
    }

    /**
     * チャネルを無効化する
     */
    @PutMapping("/channels/{channelId}/disable")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Void> disableChannel(@PathVariable Long channelId) {
        lineChannelService.toggleChannelStatus(channelId, true);
        return ResponseEntity.ok().build();
    }

    /**
     * チャネルを有効化する
     */
    @PutMapping("/channels/{channelId}/enable")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Void> enableChannel(@PathVariable Long channelId) {
        lineChannelService.toggleChannelStatus(channelId, false);
        return ResponseEntity.ok().build();
    }

    /**
     * チャネルの強制割り当て解除
     */
    @DeleteMapping("/channels/{channelId}/assignment")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Void> forceReleaseChannel(@PathVariable Long channelId) {
        lineChannelService.forceReleaseChannel(channelId);
        return ResponseEntity.ok().build();
    }

    /**
     * 抽選結果をLINE送信する
     */
    @PostMapping("/send/lottery-result")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineSendResultResponse> sendLotteryResult(@RequestBody Map<String, Integer> body) {
        int year = body.getOrDefault("year", 0);
        int month = body.getOrDefault("month", 0);
        return ResponseEntity.ok(lineNotificationService.sendLotteryResults(year, month));
    }

    /**
     * 対戦組み合わせをLINE送信する
     */
    @PostMapping("/send/match-pairing")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineSendResultResponse> sendMatchPairing(@RequestBody Map<String, Long> body) {
        Long sessionId = body.get("sessionId");
        return ResponseEntity.ok(lineNotificationService.sendMatchPairings(sessionId));
    }

    /**
     * スケジュール型通知の設定を取得する
     */
    @GetMapping("/schedule-settings")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<LineScheduleSettingDto>> getScheduleSettings() {
        List<LineScheduleSettingDto> settings = scheduleSettingRepository.findAll().stream()
            .map(s -> {
                List<Integer> days;
                try {
                    days = objectMapper.readValue(s.getDaysBefore(), new TypeReference<>() {});
                } catch (Exception e) {
                    days = List.of();
                }
                return LineScheduleSettingDto.builder()
                    .notificationType(s.getNotificationType().name())
                    .enabled(s.getEnabled())
                    .daysBefore(days)
                    .build();
            })
            .collect(Collectors.toList());
        return ResponseEntity.ok(settings);
    }

    /**
     * スケジュール型通知の設定を更新する
     */
    @PutMapping("/schedule-settings")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> updateScheduleSettings(@RequestBody LineScheduleSettingDto dto) {
        ScheduleNotificationType type = ScheduleNotificationType.valueOf(dto.getNotificationType());

        LineNotificationScheduleSetting setting = scheduleSettingRepository
            .findByNotificationType(type)
            .orElse(LineNotificationScheduleSetting.builder().notificationType(type).build());

        setting.setEnabled(dto.isEnabled());
        try {
            setting.setDaysBefore(objectMapper.writeValueAsString(dto.getDaysBefore()));
        } catch (Exception e) {
            setting.setDaysBefore("[]");
        }

        scheduleSettingRepository.save(setting);
        return ResponseEntity.ok().build();
    }
}
