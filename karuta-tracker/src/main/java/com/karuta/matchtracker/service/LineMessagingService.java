package com.karuta.matchtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String RICH_MENU_API_URL = "https://api.line.me/v2/bot/richmenu";
    private static final String RICH_MENU_IMAGE_API_URL = "https://api-data.line.me/v2/bot/richmenu";
    private static final String RICH_MENU_DEFAULT_API_URL = "https://api.line.me/v2/bot/user/all/richmenu";
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
     * Reply APIでFlex Messageを返信する
     */
    public void sendReplyFlexMessage(String channelAccessToken, String replyToken,
                                      String altText, Map<String, Object> contents) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(channelAccessToken);

            Map<String, Object> body = Map.of(
                "replyToken", replyToken,
                "messages", new Object[]{
                    Map.of("type", "flex", "altText", altText, "contents", contents)
                }
            );

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.exchange(REPLY_API_URL, HttpMethod.POST, request, String.class);
            log.debug("LINE Reply Flex message sent with replyToken: {}...", replyToken.substring(0, Math.min(10, replyToken.length())));
        } catch (Exception e) {
            log.error("Failed to send LINE Reply Flex message: {}", e.getMessage());
        }
    }

    // ===== Rich Menu API =====

    /**
     * リッチメニューを作成する
     *
     * @return 作成されたリッチメニューのID。失敗時はnull
     */
    public String createRichMenu(String channelAccessToken, Map<String, Object> richMenuJson) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(channelAccessToken);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(richMenuJson, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                RICH_MENU_API_URL, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode node = objectMapper.readTree(response.getBody());
                String richMenuId = node.path("richMenuId").asText(null);
                log.info("Rich menu created: {}", richMenuId);
                return richMenuId;
            } else {
                log.warn("Failed to create rich menu. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to create rich menu: {}", e.getMessage());
            return null;
        }
    }

    /**
     * リッチメニューに画像をアップロードする
     */
    public boolean uploadRichMenuImage(String channelAccessToken, String richMenuId,
                                        byte[] imageData, String contentType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.valueOf(contentType));
            headers.setBearerAuth(channelAccessToken);

            HttpEntity<byte[]> request = new HttpEntity<>(imageData, headers);
            String url = RICH_MENU_IMAGE_API_URL + "/" + richMenuId + "/content";
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Rich menu image uploaded for richMenuId: {}", richMenuId);
                return true;
            } else {
                log.warn("Failed to upload rich menu image. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to upload rich menu image for richMenuId {}: {}", richMenuId, e.getMessage());
            return false;
        }
    }

    /**
     * リッチメニューをデフォルトに設定する（全ユーザーに適用）
     */
    public boolean setDefaultRichMenu(String channelAccessToken, String richMenuId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(channelAccessToken);

            HttpEntity<Void> request = new HttpEntity<>(headers);
            String url = RICH_MENU_DEFAULT_API_URL + "/" + richMenuId;
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Default rich menu set: {}", richMenuId);
                return true;
            } else {
                log.warn("Failed to set default rich menu. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to set default rich menu {}: {}", richMenuId, e.getMessage());
            return false;
        }
    }

    private static final String WEBHOOK_ENDPOINT_API_URL = "https://api.line.me/v2/bot/channel/webhook/endpoint";

    /**
     * LINE Developer ConsoleのWebhook URLを更新する
     */
    public boolean updateWebhookUrl(String channelAccessToken, String webhookUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(channelAccessToken);

            Map<String, String> body = Map.of("endpoint", webhookUrl);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                WEBHOOK_ENDPOINT_API_URL, HttpMethod.PUT, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook URL updated successfully: {}", webhookUrl);
                return true;
            } else {
                log.warn("Failed to update webhook URL. Status: {}, Body: {}",
                    response.getStatusCode(), response.getBody());
                return false;
            }
        } catch (Exception e) {
            log.error("Failed to update webhook URL: {}", e.getMessage());
            return false;
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
