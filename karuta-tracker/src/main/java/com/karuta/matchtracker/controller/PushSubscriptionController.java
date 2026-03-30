package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.PushNotificationPreferenceDto;
import com.karuta.matchtracker.dto.PushSubscriptionRequest;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PushNotificationPreference;
import com.karuta.matchtracker.entity.PushSubscription;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.repository.PushNotificationPreferenceRepository;
import com.karuta.matchtracker.repository.PushSubscriptionRepository;
import com.karuta.matchtracker.service.PushNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final PushNotificationPreferenceRepository pushNotificationPreferenceRepository;

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
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Void> subscribe(@Valid @RequestBody PushSubscriptionRequest request,
                                          HttpServletRequest httpRequest) {
        validatePlayerAccess(request.getPlayerId(), httpRequest);

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
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Void> unsubscribe(@RequestParam Long playerId, @RequestParam String endpoint,
                                            HttpServletRequest httpRequest) {
        validatePlayerAccess(playerId, httpRequest);
        pushSubscriptionRepository.deleteByPlayerIdAndEndpoint(playerId, endpoint);
        log.info("Push subscription removed for player {}", playerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Web Push通知設定を取得（全団体分）
     */
    @GetMapping("/preferences/{playerId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<PushNotificationPreferenceDto>> getPreferences(
            @PathVariable Long playerId, HttpServletRequest httpRequest) {
        validatePlayerAccess(playerId, httpRequest);
        List<PushNotificationPreference> prefs = pushNotificationPreferenceRepository.findByPlayerId(playerId);
        if (prefs.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<PushNotificationPreferenceDto> dtos = prefs.stream()
                .map(PushNotificationPreferenceDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Web Push通知設定を更新（団体指定）
     */
    @PutMapping("/preferences")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<PushNotificationPreferenceDto> updatePreferences(
            @RequestBody PushNotificationPreferenceDto request, HttpServletRequest httpRequest) {
        validatePlayerAccess(request.getPlayerId(), httpRequest);
        PushNotificationPreference pref = pushNotificationPreferenceRepository
                .findByPlayerIdAndOrganizationId(request.getPlayerId(), request.getOrganizationId())
                .orElseGet(() -> PushNotificationPreference.builder()
                        .playerId(request.getPlayerId())
                        .organizationId(request.getOrganizationId())
                        .build());

        pref.setEnabled(request.isEnabled());
        pref.setLotteryResult(request.isLotteryResult());
        pref.setWaitlistOffer(request.isWaitlistOffer());
        pref.setOfferExpiring(request.isOfferExpiring());
        pref.setOfferExpired(request.isOfferExpired());
        pref.setChannelReclaimWarning(request.isChannelReclaimWarning());
        pref.setDensukeUnmatched(request.isDensukeUnmatched());

        pushNotificationPreferenceRepository.save(pref);
        log.info("Updated push notification preferences for player {} org {}", request.getPlayerId(), request.getOrganizationId());

        return ResponseEntity.ok(PushNotificationPreferenceDto.fromEntity(pref));
    }

    /**
     * PLAYERロールの場合、自分のplayerIdのみアクセス可能か検証する
     */
    private void validatePlayerAccess(Long playerId, HttpServletRequest httpRequest) {
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserRole == Role.PLAYER && !playerId.equals(currentUserId)) {
            throw new ForbiddenException("他のプレイヤーのPush設定にはアクセスできません");
        }
    }
}
