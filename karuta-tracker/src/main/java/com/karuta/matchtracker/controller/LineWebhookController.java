package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.service.LineLinkingService;
import com.karuta.matchtracker.service.LineMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * LINE Webhookエンドポイント
 *
 * LINEプラットフォームからのWebhookイベントを受信・処理する。
 * チャネルごとに異なるURLを持つ: /api/line/webhook/{channelId}
 */
@RestController
@RequestMapping("/api/line/webhook")
@RequiredArgsConstructor
@Slf4j
public class LineWebhookController {

    private final LineChannelRepository lineChannelRepository;
    private final LineMessagingService lineMessagingService;
    private final LineLinkingService lineLinkingService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{lineChannelId}")
    public ResponseEntity<String> handleWebhook(
            @PathVariable String lineChannelId,
            @RequestHeader(value = "x-line-signature", required = false) String signature,
            @RequestBody String body) {

        // チャネル検索
        LineChannel channel = lineChannelRepository.findByLineChannelId(lineChannelId).orElse(null);
        if (channel == null) {
            log.warn("Webhook received for unknown channel: {}", lineChannelId);
            return ResponseEntity.badRequest().body("Unknown channel");
        }

        // 署名検証
        if (signature == null || !lineMessagingService.verifySignature(channel.getChannelSecret(), body, signature)) {
            log.warn("Invalid signature for channel: {}", lineChannelId);
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.get("events");
            if (events == null || !events.isArray()) {
                return ResponseEntity.ok("OK");
            }

            for (JsonNode event : events) {
                String type = event.has("type") ? event.get("type").asText() : "";
                processEvent(channel, event, type);
            }
        } catch (Exception e) {
            log.error("Error processing webhook for channel {}: {}", lineChannelId, e.getMessage());
        }

        return ResponseEntity.ok("OK");
    }

    private void processEvent(LineChannel channel, JsonNode event, String type) {
        switch (type) {
            case "follow" -> handleFollow(channel, event);
            case "message" -> handleMessage(channel, event);
            case "unfollow" -> handleUnfollow(channel, event);
            default -> log.debug("Ignoring event type: {}", type);
        }
    }

    private void handleFollow(LineChannel channel, JsonNode event) {
        String replyToken = event.has("replyToken") ? event.get("replyToken").asText() : null;
        if (replyToken != null) {
            lineMessagingService.sendReplyMessage(
                channel.getChannelAccessToken(),
                replyToken,
                "友だち追加ありがとうございます！\nアプリに表示された連携コードをこのトークに貼り付けてください。"
            );
        }
        log.info("Follow event received on channel {}", channel.getLineChannelId());
    }

    private void handleMessage(LineChannel channel, JsonNode event) {
        JsonNode messageNode = event.get("message");
        if (messageNode == null || !"text".equals(messageNode.path("type").asText())) {
            return;
        }

        String text = messageNode.path("text").asText().trim();
        String lineUserId = event.path("source").path("userId").asText();
        String replyToken = event.has("replyToken") ? event.get("replyToken").asText() : null;

        // ワンタイムコードとして検証
        LineLinkingService.VerificationResult result =
            lineLinkingService.verifyCode(text, lineUserId, channel.getId());

        String replyMessage = switch (result) {
            case SUCCESS -> "連携が完了しました！アプリからLINE通知を受け取れるようになりました。";
            case EXPIRED -> "コードの有効期限が切れています。アプリから再発行してください。";
            case MAX_ATTEMPTS -> "試行回数の上限に達しました。アプリから新しいコードを発行してください。";
            case INVALID -> "コードが無効です。アプリに表示されているコードを正確に入力してください。";
        };

        if (replyToken != null) {
            lineMessagingService.sendReplyMessage(channel.getChannelAccessToken(), replyToken, replyMessage);
        }
    }

    private void handleUnfollow(LineChannel channel, JsonNode event) {
        String lineUserId = event.path("source").path("userId").asText();
        log.info("Unfollow event from userId {} on channel {}", lineUserId, channel.getLineChannelId());
        // ブロックされてもアサインは維持（送信対象から自動的に外れる）
    }
}
