package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.PushSubscriptionRequest;
import com.karuta.matchtracker.entity.PushSubscription;
import com.karuta.matchtracker.repository.PushSubscriptionRepository;
import com.karuta.matchtracker.service.PushNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Web Pushサブスクリプション管理コントローラ
 */
@RestController
@RequestMapping("/api/push-subscriptions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class PushSubscriptionController {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushNotificationService pushNotificationService;

    /**
     * VAPID公開鍵を取得
     */
    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getVapidPublicKey() {
        String publicKey = pushNotificationService.getPublicKey();
        if (publicKey == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Web Push is not configured"));
        }
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }

    /**
     * Push購読を登録
     */
    @PostMapping
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request) {
        // 既存の同一エンドポイントがあればスキップ
        if (pushSubscriptionRepository.existsByEndpoint(request.getEndpoint())) {
            return ResponseEntity.ok().build();
        }

        PushSubscription subscription = PushSubscription.builder()
                .playerId(request.getPlayerId())
                .endpoint(request.getEndpoint())
                .p256dhKey(request.getP256dhKey())
                .authKey(request.getAuthKey())
                .userAgent(request.getUserAgent())
                .build();

        pushSubscriptionRepository.save(subscription);
        log.info("Push subscription registered for player {}", request.getPlayerId());

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Push購読を解除
     */
    @DeleteMapping
    public ResponseEntity<Void> unsubscribe(@RequestParam Long playerId, @RequestParam String endpoint) {
        pushSubscriptionRepository.deleteByPlayerIdAndEndpoint(playerId, endpoint);
        log.info("Push subscription removed for player {}", playerId);
        return ResponseEntity.noContent().build();
    }
}
