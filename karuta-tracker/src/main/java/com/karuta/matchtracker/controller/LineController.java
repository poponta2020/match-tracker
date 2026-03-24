package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.LineNotificationPreferenceDto;
import com.karuta.matchtracker.dto.LineStatusDto;
import com.karuta.matchtracker.entity.LineNotificationPreference;
import com.karuta.matchtracker.repository.LineNotificationPreferenceRepository;
import com.karuta.matchtracker.service.LineChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * LINE通知ユーザー向けコントローラ
 */
@RestController
@RequestMapping("/api/line")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class LineController {

    private final LineChannelService lineChannelService;
    private final LineNotificationPreferenceRepository lineNotificationPreferenceRepository;

    /**
     * LINE通知を有効化（チャネル割り当て）
     */
    @PostMapping("/enable")
    public ResponseEntity<LineStatusDto> enableLine(@RequestBody Map<String, Long> body) {
        Long playerId = body.get("playerId");
        try {
            LineStatusDto status = lineChannelService.enableLineNotification(playerId);
            return ResponseEntity.ok(status);
        } catch (IllegalStateException e) {
            log.warn("Failed to enable LINE for player {}: {}", playerId, e.getMessage());
            return ResponseEntity.status(503).build();
        }
    }

    /**
     * LINE通知を無効化（チャネル解放）
     */
    @DeleteMapping("/disable")
    public ResponseEntity<Void> disableLine(@RequestParam Long playerId) {
        lineChannelService.disableLineNotification(playerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * LINE連携状態を取得
     */
    @GetMapping("/status")
    public ResponseEntity<LineStatusDto> getStatus(@RequestParam Long playerId) {
        return ResponseEntity.ok(lineChannelService.getLineStatus(playerId));
    }

    /**
     * 通知設定を取得
     */
    @GetMapping("/preferences")
    public ResponseEntity<LineNotificationPreferenceDto> getPreferences(@RequestParam Long playerId) {
        LineNotificationPreference pref = lineNotificationPreferenceRepository.findByPlayerId(playerId)
                .orElse(LineNotificationPreference.builder().playerId(playerId).build());
        return ResponseEntity.ok(LineNotificationPreferenceDto.fromEntity(pref));
    }

    /**
     * 通知設定を更新
     */
    @PutMapping("/preferences")
    public ResponseEntity<Void> updatePreferences(@RequestBody LineNotificationPreferenceDto dto) {
        LineNotificationPreference pref = lineNotificationPreferenceRepository
                .findByPlayerId(dto.getPlayerId())
                .orElse(LineNotificationPreference.builder().playerId(dto.getPlayerId()).build());

        if (dto.getLotteryResult() != null) pref.setLotteryResult(dto.getLotteryResult());
        if (dto.getWaitlistOffer() != null) pref.setWaitlistOffer(dto.getWaitlistOffer());
        if (dto.getOfferExpired() != null) pref.setOfferExpired(dto.getOfferExpired());
        if (dto.getMatchPairing() != null) pref.setMatchPairing(dto.getMatchPairing());
        if (dto.getPracticeReminder() != null) pref.setPracticeReminder(dto.getPracticeReminder());
        if (dto.getDeadlineReminder() != null) pref.setDeadlineReminder(dto.getDeadlineReminder());

        lineNotificationPreferenceRepository.save(pref);
        return ResponseEntity.ok().build();
    }
}
