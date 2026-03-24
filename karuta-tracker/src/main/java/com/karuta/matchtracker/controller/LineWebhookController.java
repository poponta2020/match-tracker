package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.service.LineWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * LINE Webhookコントローラ
 *
 * 各LINEチャネルからのWebhookイベントを受信する。
 * エンドポイント: POST /api/line/webhook/{channelId}
 */
@RestController
@RequestMapping("/api/line/webhook")
@RequiredArgsConstructor
@Slf4j
public class LineWebhookController {

    private final LineWebhookService lineWebhookService;
    private final ObjectMapper objectMapper;

    /**
     * LINEプラットフォームからのWebhookを受信
     */
    @PostMapping("/{channelId}")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable Long channelId,
            @RequestBody String body,
            @RequestHeader(value = "x-line-signature", required = false) String signature) {

        // 署名検証
        if (signature == null || !lineWebhookService.verifySignature(channelId, body, signature)) {
            log.warn("Invalid webhook signature for channel {}", channelId);
            return ResponseEntity.badRequest().build();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.get("events");

            if (events == null || !events.isArray()) {
                return ResponseEntity.ok().build();
            }

            for (JsonNode event : events) {
                String type = event.has("type") ? event.get("type").asText() : "";
                String userId = event.has("source") && event.get("source").has("userId")
                        ? event.get("source").get("userId").asText() : null;

                if (userId == null) continue;

                switch (type) {
                    case "follow" -> lineWebhookService.handleFollow(channelId, userId);
                    case "unfollow" -> lineWebhookService.handleUnfollow(channelId, userId);
                    case "postback" -> {
                        String postbackData = event.has("postback") && event.get("postback").has("data")
                                ? event.get("postback").get("data").asText() : "";
                        lineWebhookService.handlePostback(channelId, userId, postbackData);
                    }
                    default -> log.debug("Unhandled webhook event type: {} for channel {}", type, channelId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing webhook for channel {}: {}", channelId, e.getMessage());
        }

        // LINEには常に200を返す
        return ResponseEntity.ok().build();
    }
}
