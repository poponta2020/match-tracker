package com.karuta.matchtracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final VenueRepository venueRepository;
    private final LineMessagingService lineMessagingService;
    private final LineChannelService lineChannelService;
    private final LineLinkingService lineLinkingService;
    private final WaitlistPromotionService waitlistPromotionService;
    private final LineNotificationService lineNotificationService;
    private final LineConfirmationService lineConfirmationService;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    /** 確認ダイアログを挟む対象のアクション */
    private static final Set<String> CONFIRMABLE_ACTIONS = Set.of(
        "waitlist_accept", "waitlist_decline", "waitlist_decline_session", "same_day_join"
    );

    @PostMapping("/{channelId}")
    public ResponseEntity<String> handleWebhook(
            @PathVariable Long channelId,
            @RequestHeader(value = "x-line-signature", required = false) String signature,
            @RequestBody String body) {

        // チャネル検索（DB内部ID）
        LineChannel channel = lineChannelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            log.warn("Webhook received for unknown channel ID: {}", channelId);
            return ResponseEntity.badRequest().body("Unknown channel");
        }

        // 署名検証
        if (signature == null || !lineMessagingService.verifySignature(channel.getChannelSecret(), body, signature)) {
            log.warn("Invalid signature for channel: {}", channelId);
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
            log.error("Error processing webhook for channel {}: {}", channelId, e.getMessage());
        }

        return ResponseEntity.ok("OK");
    }

    private void processEvent(LineChannel channel, JsonNode event, String type) {
        switch (type) {
            case "follow" -> handleFollow(channel, event);
            case "message" -> handleMessage(channel, event);
            case "postback" -> handlePostback(channel, event);
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

        // 連携成功後、確定済み抽選結果を送信（Replyの後にPushすることで正しい順序を保証）
        if (result == LineLinkingService.VerificationResult.SUCCESS) {
            lineChannelService.sendPendingLotteryResultsForChannel(channel.getId());
        }
    }

    private void handlePostback(LineChannel channel, JsonNode event) {
        String data = event.path("postback").path("data").asText("");
        String lineUserId = event.path("source").path("userId").asText();
        String replyToken = event.has("replyToken") ? event.get("replyToken").asText() : null;

        if (data.isEmpty()) {
            log.warn("Empty postback data from userId {} on channel {}", lineUserId, channel.getLineChannelId());
            return;
        }

        java.util.Map<String, String> params = parsePostbackData(data);
        String action = params.getOrDefault("action", "");

        // LINE userId + channelId でプレイヤーの紐付けを検証
        Optional<LineChannelAssignment> assignmentOpt =
            lineChannelAssignmentRepository.findByLineUserIdAndLineChannelIdAndStatus(
                lineUserId, channel.getId(), LineChannelAssignment.AssignmentStatus.LINKED);
        if (assignmentOpt.isEmpty()) {
            log.warn("No linked assignment for lineUserId: {}", lineUserId);
            sendReply(channel, replyToken, "LINE連携が見つかりません。アプリから操作してください。");
            return;
        }

        Long playerId = assignmentOpt.get().getPlayerId();

        // 確認対象のアクション → 確認Flexを返す
        if (CONFIRMABLE_ACTIONS.contains(action)) {
            handleConfirmationRequest(channel, replyToken, action, params, playerId);
            return;
        }

        // 確認実行アクション（confirm_*） → トークン検証後に本来の処理を実行
        if (action.startsWith("confirm_")) {
            handleConfirmAction(channel, replyToken, action, params, playerId);
            return;
        }

        // キャンセル
        if ("cancel_confirm".equals(action)) {
            sendReply(channel, replyToken, "操作をキャンセルしました");
            return;
        }

        log.debug("Ignoring unknown postback action: {}", data);
    }

    /**
     * 確認ダイアログを送信する（元アクション受信時）
     */
    private void handleConfirmationRequest(LineChannel channel, String replyToken,
                                            String action, java.util.Map<String, String> params, Long playerId) {
        try {
            // セッション情報をDBから取得
            String sessionLabel = null;
            Integer matchNumber = null;

            if ("waitlist_accept".equals(action) || "waitlist_decline".equals(action)) {
                // participantIdからセッション情報を取得
                String participantIdStr = params.getOrDefault("participantId", "");
                if (participantIdStr.isEmpty()) {
                    sendReply(channel, replyToken, "パラメータが不正です。");
                    return;
                }
                Long participantId = Long.parseLong(participantIdStr);
                PracticeParticipant participant = practiceParticipantRepository.findById(participantId).orElse(null);
                if (participant == null) {
                    sendReply(channel, replyToken, "操作対象が見つかりません。アプリから確認してください。");
                    return;
                }
                PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
                if (session == null) {
                    sendReply(channel, replyToken, "セッション情報が見つかりません。");
                    return;
                }
                sessionLabel = getSessionLabel(session);
                matchNumber = participant.getMatchNumber();

            } else if ("waitlist_decline_session".equals(action)) {
                // sessionIdからセッション情報を取得
                String sessionIdStr = params.getOrDefault("sessionId", "");
                if (sessionIdStr.isEmpty()) {
                    sendReply(channel, replyToken, "セッション情報が不正です。アプリから操作してください。");
                    return;
                }
                Long sessionId = Long.parseLong(sessionIdStr);
                PracticeSession session = practiceSessionRepository.findById(sessionId).orElse(null);
                if (session == null) {
                    sendReply(channel, replyToken, "セッション情報が見つかりません。");
                    return;
                }
                sessionLabel = getSessionLabel(session);

            } else if ("same_day_join".equals(action)) {
                // sessionId + matchNumberからセッション情報を取得
                String sessionIdStr = params.getOrDefault("sessionId", "");
                String matchNumberStr = params.getOrDefault("matchNumber", "");
                if (sessionIdStr.isEmpty() || matchNumberStr.isEmpty()) {
                    sendReply(channel, replyToken, "パラメータが不正です。アプリから操作してください。");
                    return;
                }
                Long sessionId = Long.parseLong(sessionIdStr);
                PracticeSession session = practiceSessionRepository.findById(sessionId).orElse(null);
                if (session == null) {
                    sendReply(channel, replyToken, "セッション情報が見つかりません。");
                    return;
                }
                sessionLabel = getSessionLabel(session);
                matchNumber = Integer.parseInt(matchNumberStr);
            }

            // パラメータをJSON文字列に変換
            String paramsJson = objectMapper.writeValueAsString(params);

            // 確認トークンを発行
            String token = lineConfirmationService.createToken(action, paramsJson, playerId);

            // 確認用のpostbackデータを構築
            String confirmAction = "action=confirm_" + action + "&token=" + token;
            String cancelAction = "action=cancel_confirm&token=" + token;

            // 確認用Flex Messageを構築・送信
            Map<String, Object> flex = lineNotificationService.buildConfirmationFlex(
                action, sessionLabel, matchNumber, confirmAction, cancelAction);
            sendReplyFlex(channel, replyToken, "操作の確認", flex);

            log.info("Sent confirmation dialog for action={}, player={}", action, playerId);

        } catch (NumberFormatException e) {
            log.warn("Invalid parameter in confirmation request: {}", e.getMessage());
            sendReply(channel, replyToken, "パラメータが不正です。アプリから操作してください。");
        } catch (Exception e) {
            log.error("Failed to send confirmation dialog: {}", e.getMessage());
            sendReply(channel, replyToken, "処理中にエラーが発生しました。管理者に連絡してください。");
        }
    }

    /**
     * 確認トークンを検証し、本来の処理を実行する（confirm_*アクション受信時）
     */
    private void handleConfirmAction(LineChannel channel, String replyToken,
                                      String action, java.util.Map<String, String> params, Long playerId) {
        String token = params.getOrDefault("token", "");
        if (token.isEmpty()) {
            sendReply(channel, replyToken, "この確認は期限切れです。もう一度操作してください。");
            return;
        }

        try {
            // トークン検証・消費
            LineConfirmationToken confirmationToken = lineConfirmationService.consumeToken(token, playerId);

            // 元のパラメータを復元
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> originalParams =
                objectMapper.readValue(confirmationToken.getParams(), java.util.Map.class);
            String originalAction = confirmationToken.getAction();

            // 元のアクションに応じて処理を実行
            executeOriginalAction(channel, replyToken, originalAction, originalParams, playerId);

        } catch (IllegalStateException e) {
            // トークン検証NG（期限切れ・使用済み・不在・本人不一致）
            sendReply(channel, replyToken, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process confirm action: {}", e.getMessage());
            sendReply(channel, replyToken, "処理中にエラーが発生しました。管理者に連絡してください。");
        }
    }

    /**
     * 元のアクションを実行する
     */
    private void executeOriginalAction(LineChannel channel, String replyToken,
                                        String action, java.util.Map<String, String> params, Long playerId) {
        switch (action) {
            case "waitlist_accept", "waitlist_decline" ->
                handleWaitlistResponse(channel, replyToken, action, params, playerId);
            case "waitlist_decline_session" ->
                handleWaitlistDeclineSession(channel, replyToken, params, playerId);
            case "same_day_join" ->
                handleSameDayJoin(channel, replyToken, params, playerId);
            default ->
                sendReply(channel, replyToken, "不明な操作です。");
        }
    }

    /**
     * 繰り上げオファー承諾/辞退を処理する
     */
    private void handleWaitlistResponse(LineChannel channel, String replyToken,
                                         String action, java.util.Map<String, String> params, Long playerId) {
        String participantIdStr = params.getOrDefault("participantId", "");
        if (participantIdStr.isEmpty()) {
            log.debug("Ignoring postback without participantId");
            return;
        }

        Long participantId;
        try {
            participantId = Long.parseLong(participantIdStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid participantId: {}", participantIdStr);
            return;
        }

        // 参加者レコードを取得し、本人確認
        Optional<PracticeParticipant> participantOpt =
            practiceParticipantRepository.findById(participantId);
        if (participantOpt.isEmpty() || !participantOpt.get().getPlayerId().equals(playerId)) {
            log.warn("Participant {} not found or not owned by player {}", participantId, playerId);
            sendReply(channel, replyToken, "操作対象が見つかりません。アプリから確認してください。");
            return;
        }

        PracticeParticipant participant = participantOpt.get();

        // 既にOFFERED以外のステータスなら処理済み
        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            log.info("Postback for participant {} but status is already {}", participantId, participant.getStatus());
            sendReply(channel, replyToken, "この操作は既に処理済みです。");
            return;
        }

        boolean accept = "waitlist_accept".equals(action);
        try {
            waitlistPromotionService.respondToOffer(participantId, accept);
            String replyMessage = accept
                ? "参加登録が完了しました。練習頑張ってください！"
                : "オファーを辞退しました。次の方に通知します。";
            sendReply(channel, replyToken, replyMessage);
            log.info("Waitlist offer {} via LINE postback: player={}, participant={}",
                accept ? "accepted" : "declined", playerId, participantId);
        } catch (Exception e) {
            log.error("Failed to process waitlist postback for participant {}: {}", participantId, e.getMessage());
            sendReply(channel, replyToken, "処理中にエラーが発生しました。管理者に連絡してください。");
        }
    }

    /**
     * キャンセル待ちセッション辞退のpostbackを処理する
     */
    private void handleWaitlistDeclineSession(LineChannel channel, String replyToken,
                                               java.util.Map<String, String> params, Long playerId) {
        String sessionIdStr = params.getOrDefault("sessionId", "");
        if (sessionIdStr.isEmpty()) {
            sendReply(channel, replyToken, "セッション情報が不正です。アプリから操作してください。");
            return;
        }

        try {
            Long sessionId = Long.parseLong(sessionIdStr);
            int count = waitlistPromotionService.declineWaitlistBySession(sessionId, playerId);
            sendReply(channel, replyToken,
                String.format("キャンセル待ちを辞退しました（%d件）。", count));
            log.info("Waitlist session decline via LINE postback: player={}, session={}, count={}",
                playerId, sessionId, count);
        } catch (IllegalStateException e) {
            sendReply(channel, replyToken, "辞退対象のキャンセル待ちがありません。");
        } catch (Exception e) {
            log.error("Failed to decline waitlist via LINE postback: player={}, error={}",
                playerId, e.getMessage());
            sendReply(channel, replyToken, "処理中にエラーが発生しました。管理者に連絡してください。");
        }
    }

    /**
     * 当日補充参加のpostbackを処理する
     */
    private void handleSameDayJoin(LineChannel channel, String replyToken,
                                    java.util.Map<String, String> params, Long playerId) {
        String sessionIdStr = params.getOrDefault("sessionId", "");
        String matchNumberStr = params.getOrDefault("matchNumber", "");

        if (sessionIdStr.isEmpty() || matchNumberStr.isEmpty()) {
            sendReply(channel, replyToken, "パラメータが不正です。アプリから操作してください。");
            return;
        }

        try {
            Long sessionId = Long.parseLong(sessionIdStr);
            int matchNumber = Integer.parseInt(matchNumberStr);
            waitlistPromotionService.handleSameDayJoin(sessionId, matchNumber, playerId);
            sendReply(channel, replyToken, "参加登録が完了しました！練習頑張ってください！");
            log.info("Same-day join via LINE postback: player={}, session={}, match={}",
                    playerId, sessionId, matchNumber);
        } catch (IllegalStateException e) {
            sendReply(channel, replyToken, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to process same-day join: player={}, error={}", playerId, e.getMessage());
            sendReply(channel, replyToken, "処理中にエラーが発生しました。管理者に連絡してください。");
        }
    }

    /**
     * セッションのラベルを生成する（例: "4月5日（中央公民館）"）
     */
    private String getSessionLabel(PracticeSession session) {
        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        if (session.getVenueId() != null) {
            Venue venue = venueRepository.findById(session.getVenueId()).orElse(null);
            if (venue != null) {
                return dateStr + "（" + venue.getName() + "）";
            }
        }
        return dateStr;
    }

    /**
     * postbackデータをキー=値ペアにパースする
     */
    private java.util.Map<String, String> parsePostbackData(String data) {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        for (String pair : data.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    /**
     * Reply APIでテキスト返信する（nullチェック付き）
     */
    private void sendReply(LineChannel channel, String replyToken, String message) {
        if (replyToken != null) {
            lineMessagingService.sendReplyMessage(channel.getChannelAccessToken(), replyToken, message);
        }
    }

    /**
     * Reply APIでFlex Message返信する（nullチェック付き）
     */
    private void sendReplyFlex(LineChannel channel, String replyToken, String altText, Map<String, Object> flex) {
        if (replyToken != null) {
            lineMessagingService.sendReplyFlexMessage(channel.getChannelAccessToken(), replyToken, altText, flex);
        }
    }

    private void handleUnfollow(LineChannel channel, JsonNode event) {
        String lineUserId = event.path("source").path("userId").asText();
        log.info("Unfollow event from userId {} on channel {}", lineUserId, channel.getLineChannelId());
        // ブロックされてもアサインは維持（送信対象から自動的に外れる）
    }
}
