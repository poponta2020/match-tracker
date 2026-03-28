package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineLinkingCode;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.service.LineChannelService;
import com.karuta.matchtracker.service.LineLinkingService;
import com.karuta.matchtracker.service.LineNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * LINE通知ユーザー向けAPIコントローラ
 */
@RestController
@RequestMapping("/api/line")
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
public class LineUserController {

    private final LineChannelService lineChannelService;
    private final LineLinkingService lineLinkingService;
    private final LineNotificationService lineNotificationService;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineChannelRepository lineChannelRepository;

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * LINE通知を有効化する（チャネル割り当て+コード発行）
     */
    @PostMapping("/enable")
    public ResponseEntity<?> enableLineNotification(@RequestBody Map<String, Long> body) {
        Long playerId = body.get("playerId");
        if (playerId == null) {
            return ResponseEntity.badRequest().body("playerId is required");
        }

        try {
            LineChannel channel = lineChannelService.assignChannel(playerId);
            LineLinkingCode code = lineLinkingService.issueCode(playerId, channel.getId());

            String friendAddUrl = channel.getBasicId() != null
                ? "https://line.me/R/ti/p/" + channel.getBasicId() : null;
            return ResponseEntity.ok(LineEnableResponse.builder()
                .friendAddUrl(friendAddUrl)
                .linkingCode(code.getCode())
                .codeExpiresAt(code.getExpiresAt().format(ISO_FORMAT))
                .status("ASSIGNED")
                .build());
        } catch (IllegalStateException e) {
            log.warn("Failed to enable LINE notification for player {}: {}", playerId, e.getMessage());
            return ResponseEntity.status(503).body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * LINE通知を無効化する（チャネル解放）
     */
    @DeleteMapping("/disable")
    public ResponseEntity<Void> disableLineNotification(@RequestBody Map<String, Long> body) {
        Long playerId = body.get("playerId");
        if (playerId == null) {
            return ResponseEntity.badRequest().build();
        }

        lineChannelService.releaseChannel(playerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * ワンタイムコードを再発行する
     */
    @PostMapping("/reissue-code")
    public ResponseEntity<?> reissueCode(@RequestBody Map<String, Long> body) {
        Long playerId = body.get("playerId");
        if (playerId == null) {
            return ResponseEntity.badRequest().body("playerId is required");
        }

        try {
            LineLinkingCode code = lineLinkingService.reissueCode(playerId);
            return ResponseEntity.ok(LineReissueCodeResponse.builder()
                .linkingCode(code.getCode())
                .codeExpiresAt(code.getExpiresAt().format(ISO_FORMAT))
                .build());
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * LINE連携状態を取得する
     */
    @GetMapping("/status")
    public ResponseEntity<LineStatusResponse> getStatus(@RequestParam Long playerId) {
        var assignmentOpt = lineChannelAssignmentRepository.findActiveByPlayerId(playerId);

        if (assignmentOpt.isEmpty()) {
            return ResponseEntity.ok(LineStatusResponse.builder()
                .enabled(false).linked(false).build());
        }

        LineChannelAssignment assignment = assignmentOpt.get();
        String friendAddUrl = lineChannelRepository.findById(assignment.getLineChannelId())
            .map(ch -> ch.getBasicId() != null ? "https://line.me/R/ti/p/" + ch.getBasicId() : null)
            .orElse(null);

        return ResponseEntity.ok(LineStatusResponse.builder()
            .enabled(true)
            .linked(assignment.getStatus() == LineChannelAssignment.AssignmentStatus.LINKED)
            .friendAddUrl(friendAddUrl)
            .build());
    }

    /**
     * 通知設定を取得する
     */
    @GetMapping("/preferences")
    public ResponseEntity<java.util.List<LineNotificationPreferenceDto>> getPreferences(@RequestParam Long playerId) {
        return ResponseEntity.ok(lineNotificationService.getPreferences(playerId));
    }

    /**
     * 通知設定を更新する
     */
    @PutMapping("/preferences")
    public ResponseEntity<Void> updatePreferences(@RequestBody LineNotificationPreferenceDto dto) {
        lineNotificationService.updatePreferences(dto);
        return ResponseEntity.ok().build();
    }
}
