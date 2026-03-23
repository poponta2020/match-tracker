package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.WebPushConfig;
import com.karuta.matchtracker.entity.PushSubscription;
import com.karuta.matchtracker.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.List;

/**
 * Web Push通知サービス（VAPID署名付き）
 *
 * nl.martijndwars:web-push ライブラリを使用して
 * RFC 8030準拠のWeb Push通知を送信する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final WebPushConfig webPushConfig;
    private PushService pushService;
    private boolean enabled = false;

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        if (webPushConfig.getPublicKey() != null && !webPushConfig.getPublicKey().isEmpty()
                && webPushConfig.getPrivateKey() != null && !webPushConfig.getPrivateKey().isEmpty()) {
            try {
                pushService = new PushService(
                        webPushConfig.getPublicKey(),
                        webPushConfig.getPrivateKey(),
                        webPushConfig.getSubject()
                );
                enabled = true;
                log.info("Web Push service initialized with VAPID signing");
            } catch (GeneralSecurityException e) {
                log.error("Failed to initialize Web Push service: {}", e.getMessage());
            }
        } else {
            log.warn("VAPID keys not configured - Web Push notifications disabled");
        }
    }

    /**
     * 指定プレイヤーの全デバイスにPush通知を送信する
     */
    public void sendPush(Long playerId, String title, String body, String url) {
        if (!enabled) {
            log.debug("Push service not available, skipping push for player {}", playerId);
            return;
        }

        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByPlayerId(playerId);

        if (subscriptions.isEmpty()) {
            log.debug("No push subscriptions for player {}", playerId);
            return;
        }

        String payload = String.format(
                "{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\"}",
                escapeJson(title), escapeJson(body), url != null ? escapeJson(url) : "");

        for (PushSubscription sub : subscriptions) {
            try {
                sendToEndpoint(sub, payload);
                log.debug("Push sent to player {} (endpoint: {}...)",
                        playerId, sub.getEndpoint().substring(0, Math.min(50, sub.getEndpoint().length())));
            } catch (Exception e) {
                log.warn("Failed to send push to player {}: {}", playerId, e.getMessage());
                if (isSubscriptionExpired(e)) {
                    log.info("Removing expired push subscription for player {}", playerId);
                    pushSubscriptionRepository.delete(sub);
                }
            }
        }
    }

    /**
     * VAPID公開鍵を取得（フロントエンド用）
     */
    public String getPublicKey() {
        return webPushConfig.getPublicKey();
    }

    /**
     * VAPID署名付きでPush通知をエンドポイントに送信する
     */
    private void sendToEndpoint(PushSubscription sub, String payload) throws Exception {
        Notification notification = new Notification(
                sub.getEndpoint(),
                sub.getP256dhKey(),
                sub.getAuthKey(),
                payload.getBytes()
        );

        HttpResponse response = pushService.send(notification);
        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode == 410 || statusCode == 404) {
            throw new RuntimeException(statusCode + " - subscription expired");
        }
        if (statusCode >= 400) {
            log.warn("Push endpoint returned status {}: {}", statusCode,
                    response.getStatusLine().getReasonPhrase());
        }
    }

    private boolean isSubscriptionExpired(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("410") || msg.contains("404"));
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
