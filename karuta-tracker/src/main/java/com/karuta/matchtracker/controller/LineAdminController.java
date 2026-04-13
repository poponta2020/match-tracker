package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ChannelType;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting;
import com.karuta.matchtracker.entity.LineNotificationScheduleSetting.ScheduleNotificationType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.repository.LineNotificationScheduleSettingRepository;
import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.service.LineChannelService;
import com.karuta.matchtracker.service.LineMessagingService;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.util.AdminScopeValidator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final LineMessagingService lineMessagingService;
    private final LineNotificationService lineNotificationService;
    private final LineChannelRepository lineChannelRepository;
    private final LineNotificationScheduleSettingRepository scheduleSettingRepository;
    private final ObjectMapper objectMapper;
    private final PracticeSessionRepository practiceSessionRepository;

    /**
     * チャネル一覧を取得する（用途別フィルタ対応）
     */
    @GetMapping("/channels")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<List<LineChannelDto>> getChannels(
            @RequestParam(required = false) ChannelType channelType) {
        return ResponseEntity.ok(lineChannelService.getAllChannels(channelType));
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
     * 全チャネルのWebhook URLをLINEチャネルIDベースに一括移行する
     */
    @PostMapping("/channels/migrate-webhook-urls")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Map<String, Integer>> migrateWebhookUrls(@RequestBody Map<String, String> body) {
        String baseUrl = body.get("baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // 末尾スラッシュを除去
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return ResponseEntity.ok(lineChannelService.migrateWebhookUrls(baseUrl));
    }

    /**
     * PLAYERチャネル全体にリッチメニューを一括設定する
     */
    @PostMapping("/rich-menu/setup")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<RichMenuSetupResponse> setupRichMenu(
            @RequestParam("image") MultipartFile image) {
        try {
            byte[] imageData = image.getBytes();
            String contentType = image.getContentType() != null ? image.getContentType() : "image/png";

            // リッチメニューJSON定義を構築
            Map<String, Object> richMenuJson = buildRichMenuDefinition();

            // 全PLAYERチャネルを取得
            List<LineChannel> channels = lineChannelRepository.findAllByChannelType(ChannelType.PLAYER);

            int successCount = 0;
            List<String> failures = new ArrayList<>();

            for (LineChannel channel : channels) {
                try {
                    String accessToken = channel.getChannelAccessToken();

                    // 1. リッチメニュー作成
                    String richMenuId = lineMessagingService.createRichMenu(accessToken, richMenuJson);
                    if (richMenuId == null) {
                        failures.add(channel.getChannelName() + " (作成失敗)");
                        continue;
                    }

                    // 2. 画像アップロード
                    boolean uploaded = lineMessagingService.uploadRichMenuImage(
                        accessToken, richMenuId, imageData, contentType);
                    if (!uploaded) {
                        failures.add(channel.getChannelName() + " (画像アップロード失敗)");
                        continue;
                    }

                    // 3. デフォルトメニューに設定
                    boolean set = lineMessagingService.setDefaultRichMenu(accessToken, richMenuId);
                    if (!set) {
                        failures.add(channel.getChannelName() + " (デフォルト設定失敗)");
                        continue;
                    }

                    successCount++;
                } catch (Exception e) {
                    log.error("Rich menu setup failed for channel {}: {}",
                        channel.getChannelName(), e.getMessage());
                    failures.add(channel.getChannelName() + " (" + e.getMessage() + ")");
                }
            }

            log.info("Rich menu setup completed: success={}, failure={}", successCount, failures.size());

            return ResponseEntity.ok(RichMenuSetupResponse.builder()
                .successCount(successCount)
                .failureCount(failures.size())
                .failures(failures)
                .build());

        } catch (Exception e) {
            log.error("Rich menu setup failed: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * リッチメニューのJSON定義を構築する（3列x2行の6エリア）
     *
     * ┌──────────┬──────────┬──────────┐
     * │  ロゴ    │ 今日の   │キャンセル│
     * │(アプリへ)│ 参加者   │待ち状況  │
     * ├──────────┼──────────┼──────────┤
     * │ 通知設定 │アプリを  │ 当日     │
     * │          │ 開く     │ 参加申込 │
     * └──────────┴──────────┴──────────┘
     */
    private Map<String, Object> buildRichMenuDefinition() {
        int width = 2500;
        int height = 1686;
        int rowHeight = 843;
        int colWidth = width / 3; // 833

        List<Map<String, Object>> areas = List.of(
            // 上段左: ロゴ（何もしない）
            buildArea(0, 0, colWidth, rowHeight,
                Map.of("type", "postback", "data", "action=noop")),
            // 上段中: 今日の練習参加者を確認する（アプリの練習画面へ遷移）
            buildArea(colWidth, 0, colWidth, rowHeight,
                Map.of("type", "uri", "uri", "https://match-tracker-eight-gilt.vercel.app/practice?openToday=true")),
            // 上段右: キャンセル待ち状況を見る
            buildArea(colWidth * 2, 0, width - colWidth * 2, rowHeight,
                Map.of("type", "postback", "data", "action=check_waitlist_status",
                    "displayText", "キャンセル待ち状況確認")),
            // 下段左: 結果入力
            buildArea(0, rowHeight, colWidth, rowHeight,
                Map.of("type", "uri", "uri", "https://match-tracker-eight-gilt.vercel.app/matches/results")),
            // 下段中: アプリを開く
            buildArea(colWidth, rowHeight, colWidth, rowHeight,
                Map.of("type", "uri", "uri", "https://match-tracker-eight-gilt.vercel.app/")),
            // 下段右: 当日参加申込
            buildArea(colWidth * 2, rowHeight, width - colWidth * 2, rowHeight,
                Map.of("type", "postback", "data", "action=check_same_day_join",
                    "displayText", "当日参加申込"))
        );

        Map<String, Object> size = Map.of("width", width, "height", height);
        Map<String, Object> richMenu = new LinkedHashMap<>();
        richMenu.put("size", size);
        richMenu.put("selected", true);
        richMenu.put("name", "Match Tracker メニュー");
        richMenu.put("chatBarText", "メニュー");
        richMenu.put("areas", areas);

        return richMenu;
    }

    private Map<String, Object> buildArea(int x, int y, int w, int h, Map<String, Object> action) {
        return Map.of(
            "bounds", Map.of("x", x, "y", y, "width", w, "height", h),
            "action", action
        );
    }

    /**
     * 対戦組み合わせをLINE送信する
     */
    @PostMapping("/send/match-pairing")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LineSendResultResponse> sendMatchPairing(@RequestBody Map<String, Long> body,
                                                                      HttpServletRequest httpRequest) {
        Long sessionId = body.get("sessionId");

        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        if ("ADMIN".equals(role)) {
            PracticeSession session = practiceSessionRepository.findById(sessionId)
                    .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));
            AdminScopeValidator.validateScope(role, adminOrgId, session.getOrganizationId(),
                    "他団体の組み合わせはLINE送信できません");
        }

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
