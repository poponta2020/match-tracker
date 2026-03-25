package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * LINE Messaging APIとの低レベル通信サービス
 */
@Service
@Slf4j
public class LineMessagingService {

    private static final String PUSH_API_URL = "https://api.line.me/v2/bot/message/push";
    private static final String REPLY_API_URL = "https://api.line.me/v2/bot/message/reply";
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Push APIでテキストメッセージを送信する
     */
    public boolean sendPushMessage(String channelAccessToken, String lineUserId, String messageText) {
        Object[] messages = new Object[]{ Map.of("type", "text", "text", messageText) };
        return sendPushMessages(channelAccessToken, lineUserId, messages);
    }

    /**
     * Push APIでFlex Messageを送信する
     */
    public boolean sendPushFlexMessage(String channelAccessToken, String lineUserId,
                                        String altText, Map<String, Object> contents) {
        Object[] messages = new Object[]{
            Map.of("type", "flex", "altText", altText, "contents", contents)
        };
        return sendPushMessages(channelAccessToken, lineUserId, messages);
    }

    /**
     * Push APIでメッセージ配列を送信する（共通処理）
     */
    private boolean sendPushMessages(String channelAccessToken, String lineUserId, Object[] messages) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(channelAccessToken);

            Map<String, Object> body = Map.of(
                "to", lineUserId,
                "messages", messages
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(PUSH_API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.debug("LINE Push message sent to userId: {}", lineUserId);
                return true;
            } else {
                log.warn("LINE Push API returned status {}: {}", response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to send LINE Push message to userId {}: {}", lineUserId, e.getMessage());
            return false;
        }
    }

    /**
     * Reply APIでメッセージを返信する
     */
    public void sendReplyMessage(String channelAccessToken, String replyToken, String messageText) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(channelAccessToken);

            Map<String, Object> body = Map.of(
                "replyToken", replyToken,
                "messages", new Object[]{
                    Map.of("type", "text", "text", messageText)
                }
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(REPLY_API_URL, HttpMethod.POST, request, String.class);
            log.debug("LINE Reply message sent with replyToken: {}...", replyToken.substring(0, Math.min(10, replyToken.length())));
        } catch (Exception e) {
            log.error("Failed to send LINE Reply message: {}", e.getMessage());
        }
    }

    /**
     * Webhook署名を検証する（HMAC-SHA256）
     */
    public boolean verifySignature(String channelSecret, String body, String signature) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computed = Base64.getEncoder().encodeToString(hash);
            return computed.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification failed: {}", e.getMessage());
            return false;
        }
    }
}
