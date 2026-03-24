package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.LineConfig;
import com.karuta.matchtracker.dto.LineSendResultDto;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * LINE通知送信サービス
 *
 * LINE Messaging APIを使用してユーザーにメッセージを送信する。
 * Push API（個別送信）のみを使用し、Broadcast APIは使用しない。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineNotificationService {

    private final LineChannelService lineChannelService;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    private final LineMessageLogRepository lineMessageLogRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final LineConfig lineConfig;

    private static final String PUSH_API_URL = "https://api.line.me/v2/bot/message/push";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private static final List<LineAssignmentStatus> ACTIVE_STATUSES =
            List.of(LineAssignmentStatus.PENDING, LineAssignmentStatus.LINKED);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 個別送信 ==========

    /**
     * テキストメッセージをPush送信する
     */
    public boolean sendTextMessage(Long playerId, LineNotificationType type, String text) {
        return sendTextMessage(playerId, type, text, null);
    }

    /**
     * テキストメッセージをPush送信する（参照ID付き）
     */
    public boolean sendTextMessage(Long playerId, LineNotificationType type, String text, Long referenceId) {
        Optional<LineChannelAssignment> assignment = getLinkedAssignment(playerId);
        if (assignment.isEmpty()) return false;

        if (!isNotificationEnabled(playerId, type)) return false;

        LineChannelAssignment a = assignment.get();
        if (!lineChannelService.isWithinMonthlyLimit(a.getLineChannelId())) {
            logMessage(a, type, text, "SKIPPED", "Monthly limit exceeded", referenceId);
            log.warn("Monthly limit exceeded for channel {}, skipping notification for player {}",
                    a.getLineChannelId(), playerId);
            return false;
        }

        String accessToken = lineChannelService.getDecryptedAccessToken(a.getLineChannelId());
        String payload = buildTextPayload(a.getLineUserId(), text);

        boolean success = callPushApi(accessToken, payload);

        if (success) {
            lineChannelService.incrementMessageCount(a.getLineChannelId());
            logMessage(a, type, text, "SUCCESS", null, referenceId);
        } else {
            logMessage(a, type, text, "FAILED", "Push API call failed", referenceId);
        }

        return success;
    }

    /**
     * キャンセル待ち連絡のボタンテンプレートを送信する
     */
    public boolean sendWaitlistOfferTemplate(PracticeParticipant participant) {
        Long playerId = participant.getPlayerId();
        Optional<LineChannelAssignment> assignment = getLinkedAssignment(playerId);
        if (assignment.isEmpty()) return false;

        if (!isNotificationEnabled(playerId, LineNotificationType.WAITLIST_OFFER)) return false;

        LineChannelAssignment a = assignment.get();
        if (!lineChannelService.isWithinMonthlyLimit(a.getLineChannelId())) {
            logMessage(a, LineNotificationType.WAITLIST_OFFER, "ボタンテンプレート", "SKIPPED",
                    "Monthly limit exceeded", participant.getId());
            return false;
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return false;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String deadlineStr = participant.getOfferDeadline() != null
                ? participant.getOfferDeadline().format(DEADLINE_FORMAT) : "不明";

        String text = String.format("%sの練習 試合%dに空きが出ました。参加しますか？\n（回答期限: %s）",
                dateStr, participant.getMatchNumber(), deadlineStr);
        String altText = String.format("繰り上げ参加のご連絡: %sの練習 試合%d", dateStr, participant.getMatchNumber());

        String payload = buildConfirmTemplatePayload(
                a.getLineUserId(), altText, text,
                "参加する", "action=accept&participantId=" + participant.getId(),
                "参加しない", "action=decline&participantId=" + participant.getId());

        String accessToken = lineChannelService.getDecryptedAccessToken(a.getLineChannelId());
        boolean success = callPushApi(accessToken, payload);

        if (success) {
            lineChannelService.incrementMessageCount(a.getLineChannelId());
            logMessage(a, LineNotificationType.WAITLIST_OFFER, altText, "SUCCESS", null, participant.getId());
        } else {
            logMessage(a, LineNotificationType.WAITLIST_OFFER, altText, "FAILED", "Push API call failed",
                    participant.getId());
        }

        return success;
    }

    /**
     * Postback応答の確認メッセージを送信する
     */
    public void sendPostbackResponse(Long channelDbId, String lineUserId, String message) {
        if (!lineChannelService.isWithinMonthlyLimit(channelDbId)) {
            log.warn("Monthly limit exceeded for channel {}, skipping postback response", channelDbId);
            return;
        }

        String accessToken = lineChannelService.getDecryptedAccessToken(channelDbId);
        String payload = buildTextPayload(lineUserId, message);

        if (callPushApi(accessToken, payload)) {
            lineChannelService.incrementMessageCount(channelDbId);
        }
    }

    // ========== 一括送信（管理者手動） ==========

    /**
     * 抽選結果をLINE一括送信
     */
    @Transactional(readOnly = true)
    public LineSendResultDto sendLotteryResults(int year, int month) {
        List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionYearAndMonth(year, month);

        int sent = 0, failed = 0, skipped = 0;

        for (PracticeParticipant p : participants) {
            if (p.getStatus() != ParticipantStatus.WON && p.getStatus() != ParticipantStatus.WAITLISTED) {
                continue;
            }

            PracticeSession session = practiceSessionRepository.findById(p.getSessionId()).orElse(null);
            if (session == null) continue;

            String dateStr = session.getSessionDate().format(DATE_FORMAT);
            String message;
            if (p.getStatus() == ParticipantStatus.WON) {
                message = String.format("%sの練習 試合%dに当選しました", dateStr, p.getMatchNumber());
            } else {
                message = String.format("%sの練習 試合%d: キャンセル待ち%d番です",
                        dateStr, p.getMatchNumber(), p.getWaitlistNumber());
            }

            boolean result = sendTextMessage(p.getPlayerId(), LineNotificationType.LOTTERY_RESULT,
                    message, p.getId());
            if (result) {
                sent++;
            } else {
                Optional<LineChannelAssignment> assignment = getLinkedAssignment(p.getPlayerId());
                if (assignment.isEmpty() || !isNotificationEnabled(p.getPlayerId(), LineNotificationType.LOTTERY_RESULT)) {
                    skipped++;
                } else {
                    failed++;
                }
            }
        }

        log.info("Lottery results LINE notification: sent={}, failed={}, skipped={}", sent, failed, skipped);
        return LineSendResultDto.builder().sentCount(sent).failedCount(failed).skippedCount(skipped).build();
    }

    /**
     * 対戦組み合わせをLINE一括送信
     */
    @Transactional(readOnly = true)
    public LineSendResultDto sendMatchPairings(Long sessionId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return LineSendResultDto.builder().build();
        }

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String message = String.format("%sの練習の対戦組み合わせが確定しました", dateStr);

        List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionIdAndStatus(sessionId, ParticipantStatus.WON);

        // 重複プレイヤーを排除（同セッション複数試合の場合）
        List<Long> playerIds = participants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .toList();

        int sent = 0, failed = 0, skipped = 0;

        for (Long playerId : playerIds) {
            boolean result = sendTextMessage(playerId, LineNotificationType.MATCH_PAIRING, message, sessionId);
            if (result) {
                sent++;
            } else {
                Optional<LineChannelAssignment> assignment = getLinkedAssignment(playerId);
                if (assignment.isEmpty() || !isNotificationEnabled(playerId, LineNotificationType.MATCH_PAIRING)) {
                    skipped++;
                } else {
                    failed++;
                }
            }
        }

        log.info("Match pairing LINE notification for session {}: sent={}, failed={}, skipped={}",
                sessionId, sent, failed, skipped);
        return LineSendResultDto.builder().sentCount(sent).failedCount(failed).skippedCount(skipped).build();
    }

    // ========== イベント発火型 ==========

    /**
     * オファー期限切れ通知を送信
     */
    public void sendOfferExpiredNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String message = String.format("%sの練習 試合%dの繰り上げ参加の期限が切れました",
                dateStr, participant.getMatchNumber());

        sendTextMessage(participant.getPlayerId(), LineNotificationType.OFFER_EXPIRED, message, participant.getId());
    }

    // ========== スケジュール型 ==========

    /**
     * 参加予定リマインダーを送信
     */
    public LineSendResultDto sendPracticeReminders(LocalDate practiceDate) {
        String dateStr = practiceDate.format(DATE_FORMAT);
        String message = String.format("%sは練習日です。準備をお忘れなく！", dateStr);

        List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionDateAndStatus(practiceDate, ParticipantStatus.WON);

        List<Long> playerIds = participants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .toList();

        int sent = 0, failed = 0, skipped = 0;

        for (Long playerId : playerIds) {
            boolean result = sendTextMessage(playerId, LineNotificationType.PRACTICE_REMINDER, message);
            if (result) sent++; else skipped++;
        }

        return LineSendResultDto.builder().sentCount(sent).failedCount(failed).skippedCount(skipped).build();
    }

    /**
     * 締め切りリマインダーを送信
     */
    public LineSendResultDto sendDeadlineReminders(int year, int month, LocalDate deadline) {
        String message = String.format("%d月分のスケジュール登録締め切りは%sです。登録はお済みですか？",
                month, deadline.format(DATE_FORMAT));

        // LINE連携済みの全ユーザーに送信
        List<LineChannelAssignment> linkedAssignments = lineChannelAssignmentRepository
                .findLinkedByPlayerIds(List.of()); // 全LINKED取得のため後述のリポジトリメソッドを使う

        // 簡易的に全LINKED割り当てを取得
        List<LineChannelAssignment> allLinked = lineChannelAssignmentRepository
                .findAll().stream()
                .filter(a -> a.getStatus() == LineAssignmentStatus.LINKED)
                .toList();

        int sent = 0, failed = 0, skipped = 0;

        for (LineChannelAssignment a : allLinked) {
            boolean result = sendTextMessage(a.getPlayerId(), LineNotificationType.DEADLINE_REMINDER, message);
            if (result) sent++; else skipped++;
        }

        return LineSendResultDto.builder().sentCount(sent).failedCount(failed).skippedCount(skipped).build();
    }

    // ========== 内部メソッド ==========

    private Optional<LineChannelAssignment> getLinkedAssignment(Long playerId) {
        return lineChannelAssignmentRepository.findByPlayerIdAndStatusIn(playerId,
                List.of(LineAssignmentStatus.LINKED));
    }

    private boolean isNotificationEnabled(Long playerId, LineNotificationType type) {
        if (type == LineNotificationType.POSTBACK_RESPONSE) return true;
        return lineNotificationPreferenceRepository.findByPlayerId(playerId)
                .map(pref -> pref.isEnabled(type))
                .orElse(true); // デフォルトON
    }

    private boolean callPushApi(String accessToken, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(PUSH_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return true;
            } else {
                log.warn("LINE Push API returned status {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            log.error("LINE Push API call failed: {}", e.getMessage());
            return false;
        }
    }

    private String buildTextPayload(String lineUserId, String text) {
        try {
            Map<String, Object> payload = Map.of(
                    "to", lineUserId,
                    "messages", List.of(Map.of("type", "text", "text", text))
            );
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build payload", e);
        }
    }

    private String buildConfirmTemplatePayload(String lineUserId, String altText, String text,
                                                String yesLabel, String yesData,
                                                String noLabel, String noData) {
        try {
            Map<String, Object> payload = Map.of(
                    "to", lineUserId,
                    "messages", List.of(Map.of(
                            "type", "template",
                            "altText", altText,
                            "template", Map.of(
                                    "type", "confirm",
                                    "text", text,
                                    "actions", List.of(
                                            Map.of("type", "postback", "label", yesLabel, "data", yesData),
                                            Map.of("type", "postback", "label", noLabel, "data", noData)
                                    )
                            )
                    ))
            );
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build template payload", e);
        }
    }

    private void logMessage(LineChannelAssignment assignment, LineNotificationType type,
                            String content, String status, String errorMessage, Long referenceId) {
        LineMessageLog logEntry = LineMessageLog.builder()
                .lineChannelId(assignment.getLineChannelId())
                .playerId(assignment.getPlayerId())
                .notificationType(type)
                .messageContent(content)
                .status(status)
                .errorMessage(errorMessage)
                .referenceId(referenceId)
                .build();
        lineMessageLogRepository.save(logEntry);
    }
}
