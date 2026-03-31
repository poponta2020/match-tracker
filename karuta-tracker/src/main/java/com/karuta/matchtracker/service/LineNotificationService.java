package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LineNotificationPreferenceDto;
import com.karuta.matchtracker.dto.LineSendResultResponse;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineMessageLog.MessageStatus;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LINE通知オーケストレーションサービス
 *
 * 通知の送信可否チェック、メッセージ生成、送信実行、ログ記録を一元管理する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineNotificationService {

    private final LineChannelRepository lineChannelRepository;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    private final LineMessageLogRepository lineMessageLogRepository;
    private final LineMessagingService lineMessagingService;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final LotteryService lotteryService;

    private static final int MONTHLY_MESSAGE_LIMIT = 200;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    /**
     * キャンセル待ち繰り上げ通知をFlex Messageで送信する
     */
    public void sendWaitlistOfferNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String deadlineStr = participant.getOfferDeadline() != null
            ? participant.getOfferDeadline().format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            : "不明";
        String altText = String.format("%sの練習 試合%dに空きが出ました。参加しますか？（期限: %s）",
            dateStr, participant.getMatchNumber(), deadlineStr);

        // 応答期限まで12時間未満の場合は緊急フラグ
        boolean isUrgent = participant.getOfferDeadline() != null
            && java.time.Duration.between(JstDateTimeUtil.now(), participant.getOfferDeadline()).toHours() < 12;

        Map<String, Object> flexContents = buildWaitlistOfferFlex(
            dateStr, participant.getMatchNumber(), deadlineStr, participant.getId(), isUrgent);

        sendFlexToPlayer(participant.getPlayerId(), LineNotificationType.WAITLIST_OFFER, altText, flexContents);
    }

    /**
     * キャンセル待ち繰り上げ用Flex Message（Bubble）を構築する
     */
    private Map<String, Object> buildWaitlistOfferFlex(String dateStr, int matchNumber,
                                                        String deadlineStr, Long participantId, boolean isUrgent) {
        // ヘッダー
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "繰り上げ参加のお知らせ",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#27AE60",
            "paddingAll", "15px"
        );

        // ボディ
        List<Object> bodyContents = new java.util.ArrayList<>(List.of(
            Map.of("type", "text", "text", dateStr + "の練習",
                "weight", "bold", "size", "lg", "margin", "none"),
            Map.of("type", "text", "text", "試合" + matchNumber + "に空きが出ました",
                "size", "md", "margin", "md", "color", "#333333"),
            Map.of("type", "separator", "margin", "lg"),
            Map.of("type", "box", "layout", "horizontal", "margin", "lg",
                "contents", List.of(
                    Map.of("type", "text", "text", "応答期限",
                        "size", "sm", "color", "#888888", "flex", 0),
                    Map.of("type", "text", "text", deadlineStr,
                        "size", "sm", "color", "#E74C3C", "weight", "bold",
                        "align", "end")
                ))
        ));
        if (isUrgent) {
            bodyContents.add(Map.of("type", "text",
                "text", "※ 応答期限まで残りわずかです。お早めにご回答ください。",
                "size", "xs", "color", "#E74C3C", "margin", "md", "wrap", true));
        }
        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        // フッター（参加する・辞退するボタン）
        Map<String, Object> acceptButton = Map.of(
            "type", "button",
            "action", Map.of(
                "type", "postback",
                "label", "参加する",
                "data", "action=waitlist_accept&participantId=" + participantId
            ),
            "style", "primary",
            "color", "#27AE60",
            "height", "sm"
        );

        Map<String, Object> declineButton = Map.of(
            "type", "button",
            "action", Map.of(
                "type", "postback",
                "label", "辞退する",
                "data", "action=waitlist_decline&participantId=" + participantId
            ),
            "style", "secondary",
            "height", "sm"
        );

        Map<String, Object> footer = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(acceptButton, declineButton),
            "spacing", "sm",
            "paddingAll", "15px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body,
            "footer", footer
        );
    }

    /**
     * 繰り上げオファー応答確認通知を送信する（Webアプリから応答した場合用）
     */
    public void sendOfferResponseConfirmation(PracticeParticipant participant, boolean accepted) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String message = accepted
            ? String.format("%sの練習 試合%dの繰り上げ参加を承諾しました。", dateStr, participant.getMatchNumber())
            : String.format("%sの練習 試合%dの繰り上げ参加を辞退しました。", dateStr, participant.getMatchNumber());

        sendToPlayer(participant.getPlayerId(), LineNotificationType.WAITLIST_OFFER, message);
    }

    /**
     * オファー期限切れ通知を送信する
     */
    public void sendOfferExpiredNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String message = String.format("%sの練習 試合%dの繰り上げ参加の期限が切れました",
            dateStr, participant.getMatchNumber());

        sendToPlayer(participant.getPlayerId(), LineNotificationType.OFFER_EXPIRED, message);
    }

    /**
     * 抽選結果をLINEまとめ送信する（管理者手動送信）
     *
     * プレイヤーごとにグルーピングし、以下のパターンで送信:
     * - 全当選: テキスト1通
     * - 一部落選: イントロテキスト + セッション別Flex + クロージングテキスト
     * - 全落選: イントロテキスト + セッション別Flexのみ
     */
    @Transactional
    public LineSendResultResponse sendLotteryResults(int year, int month) {
        int sentPlayers = 0, failedPlayers = 0, skippedPlayers = 0;

        List<PracticeParticipant> participants = practiceParticipantRepository
            .findBySessionDateYearAndMonth(year, month);

        // セッション情報をキャッシュ
        Map<Long, PracticeSession> sessionCache = new HashMap<>();
        for (PracticeParticipant p : participants) {
            sessionCache.computeIfAbsent(p.getSessionId(),
                id -> practiceSessionRepository.findById(id).orElse(null));
        }

        // WON/WAITLISTED のみ対象、プレイヤーごとにグルーピング
        Map<Long, List<PracticeParticipant>> byPlayer = participants.stream()
            .filter(p -> p.getStatus() == ParticipantStatus.WON || p.getStatus() == ParticipantStatus.WAITLISTED)
            .collect(Collectors.groupingBy(PracticeParticipant::getPlayerId));

        for (Map.Entry<Long, List<PracticeParticipant>> entry : byPlayer.entrySet()) {
            Long playerId = entry.getKey();

            // プレイヤーがセッションの団体に登録しているか確認（団体フィルタ）
            Long sessionOrgId = entry.getValue().stream()
                    .map(p -> sessionCache.get(p.getSessionId()))
                    .filter(java.util.Objects::nonNull)
                    .map(PracticeSession::getOrganizationId)
                    .findFirst().orElse(null);
            if (sessionOrgId != null && !playerOrganizationRepository.existsByPlayerIdAndOrganizationId(playerId, sessionOrgId)) {
                skippedPlayers++;
                continue;
            }
            List<PracticeParticipant> playerParticipants = entry.getValue();

            List<PracticeParticipant> waitlisted = playerParticipants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED)
                .collect(Collectors.toList());
            boolean hasWon = playerParticipants.stream()
                .anyMatch(p -> p.getStatus() == ParticipantStatus.WON);

            // プレイヤー単位の送信結果を追跡（1通でも成功すればSUCCESS）
            boolean anySuccess = false;
            boolean anyFailed = false;

            if (waitlisted.isEmpty() && hasWon) {
                // 全当選: テキスト1通
                SendResult r = sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                    "申し込んだ練習はすべて当選しました");
                if (r == SendResult.SUCCESS) anySuccess = true;
                else if (r == SendResult.FAILED) anyFailed = true;
            } else if (!waitlisted.isEmpty()) {
                // イントロメッセージ
                SendResult r1 = sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                    "落選した試合があります");
                if (r1 == SendResult.SUCCESS) anySuccess = true;
                else if (r1 == SendResult.FAILED) anyFailed = true;

                // セッション別Flex Message
                Map<Long, List<PracticeParticipant>> waitlistedBySession = waitlisted.stream()
                    .collect(Collectors.groupingBy(PracticeParticipant::getSessionId));

                for (Map.Entry<Long, List<PracticeParticipant>> sessionEntry : waitlistedBySession.entrySet()) {
                    PracticeSession session = sessionCache.get(sessionEntry.getKey());
                    if (session == null) continue;

                    String dateStr = session.getSessionDate().format(DATE_FORMAT);
                    Map<String, Object> flex = buildLotteryWaitlistedFlex(
                        dateStr, sessionEntry.getValue(), sessionEntry.getKey(), playerId);
                    String altText = dateStr + "の練習: キャンセル待ち";

                    SendResult r2 = sendFlexToPlayer(playerId, LineNotificationType.LOTTERY_RESULT, altText, flex);
                    if (r2 == SendResult.SUCCESS) anySuccess = true;
                    else if (r2 == SendResult.FAILED) anyFailed = true;
                }

                // 当選分がある場合のみクロージング
                if (hasWon) {
                    SendResult r3 = sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                        "上記以外の申し込みはすべて当選しています");
                    if (r3 == SendResult.SUCCESS) anySuccess = true;
                    else if (r3 == SendResult.FAILED) anyFailed = true;
                }
            }

            // プレイヤー単位でカウント（1通でも成功→sent、全失敗→failed、全スキップ→skipped）
            if (anySuccess) {
                sentPlayers++;
            } else if (anyFailed) {
                failedPlayers++;
            } else {
                skippedPlayers++;
            }
        }

        log.info("Lottery result LINE notifications: sentPlayers={}, failedPlayers={}, skippedPlayers={}",
            sentPlayers, failedPlayers, skippedPlayers);
        return LineSendResultResponse.builder()
            .sentPlayerCount(sentPlayers).failedPlayerCount(failedPlayers).skippedPlayerCount(skippedPlayers).build();
    }

    /**
     * セッション単位のキャンセル待ちFlex Message（Bubble）を構築する
     */
    private Map<String, Object> buildLotteryWaitlistedFlex(String dateStr,
                                                            List<PracticeParticipant> waitlistedInSession,
                                                            Long sessionId, Long playerId) {
        // ヘッダー
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "落選した試合のお知らせ",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#E74C3C",
            "paddingAll", "15px"
        );

        // ボディ
        List<Object> bodyContents = new ArrayList<>();
        bodyContents.add(Map.of("type", "text", "text", dateStr,
            "weight", "bold", "size", "lg", "margin", "none"));

        for (PracticeParticipant p : waitlistedInSession) {
            bodyContents.add(Map.of("type", "text",
                "text", String.format("試合%d: キャンセル待ち%d番", p.getMatchNumber(), p.getWaitlistNumber()),
                "size", "md", "margin", "md", "color", "#333333"));
        }

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        // フッター（辞退ボタンのみ）
        Map<String, Object> declineButton = Map.of(
            "type", "button",
            "action", Map.of(
                "type", "postback",
                "label", "キャンセル待ちを辞退する",
                "data", "action=waitlist_decline_session&sessionId=" + sessionId + "&playerId=" + playerId
            ),
            "style", "secondary",
            "height", "sm"
        );

        Map<String, Object> footer = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(declineButton),
            "paddingAll", "15px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body,
            "footer", footer
        );
    }

    /**
     * 特定ユーザー1人に対して、確定済みの抽選結果をLINE送信する。
     * LINE連携完了時に呼び出される。当月・翌月の確定済み結果があれば送信する。
     */
    @Transactional
    public void sendLotteryResultsForPlayer(Long playerId) {
        var now = JstDateTimeUtil.now();
        int[][] targetMonths = {
            { now.getYear(), now.getMonthValue() },
            { now.getMonthValue() == 12 ? now.getYear() + 1 : now.getYear(),
              now.getMonthValue() == 12 ? 1 : now.getMonthValue() + 1 }
        };

        for (int[] ym : targetMonths) {
            int year = ym[0];
            int month = ym[1];

            if (!lotteryService.isLotteryConfirmed(year, month)) {
                continue;
            }

            List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionDateYearAndMonth(year, month);

            // セッション情報をキャッシュ
            Map<Long, PracticeSession> sessionCache = new HashMap<>();
            for (PracticeParticipant p : participants) {
                sessionCache.computeIfAbsent(p.getSessionId(),
                    id -> practiceSessionRepository.findById(id).orElse(null));
            }

            // 対象ユーザーの WON/WAITLISTED のみ
            List<PracticeParticipant> playerParticipants = participants.stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .filter(p -> p.getStatus() == ParticipantStatus.WON || p.getStatus() == ParticipantStatus.WAITLISTED)
                .collect(Collectors.toList());

            if (playerParticipants.isEmpty()) {
                continue;
            }

            List<PracticeParticipant> waitlisted = playerParticipants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED)
                .collect(Collectors.toList());
            boolean hasWon = playerParticipants.stream()
                .anyMatch(p -> p.getStatus() == ParticipantStatus.WON);

            if (waitlisted.isEmpty() && hasWon) {
                sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                    "申し込んだ練習はすべて当選しました");
            } else if (!waitlisted.isEmpty()) {
                sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                    "落選した試合があります");

                Map<Long, List<PracticeParticipant>> waitlistedBySession = waitlisted.stream()
                    .collect(Collectors.groupingBy(PracticeParticipant::getSessionId));

                for (Map.Entry<Long, List<PracticeParticipant>> sessionEntry : waitlistedBySession.entrySet()) {
                    PracticeSession session = sessionCache.get(sessionEntry.getKey());
                    if (session == null) continue;

                    String dateStr = session.getSessionDate().format(DATE_FORMAT);
                    Map<String, Object> flex = buildLotteryWaitlistedFlex(
                        dateStr, sessionEntry.getValue(), sessionEntry.getKey(), playerId);
                    String altText = dateStr + "の練習: キャンセル待ち";

                    sendFlexToPlayer(playerId, LineNotificationType.LOTTERY_RESULT, altText, flex);
                }

                if (hasWon) {
                    sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                        "上記以外の申し込みはすべて当選しています");
                }
            }

            log.info("Sent lottery results for {}-{} to player {} after LINE linking", year, month, playerId);
        }
    }

    /**
     * 対戦組み合わせをLINE送信する（管理者手動送信）
     */
    @Transactional
    public LineSendResultResponse sendMatchPairings(Long sessionId) {
        int sent = 0, failed = 0, skipped = 0;

        PracticeSession session = practiceSessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return LineSendResultResponse.builder().build();
        }

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String message = String.format("%sの練習の対戦組み合わせが確定しました", dateStr);

        List<PracticeParticipant> participants = practiceParticipantRepository
            .findBySessionIdAndStatus(sessionId, ParticipantStatus.WON);

        // 重複排除（同一プレイヤーに1通だけ送る）
        List<Long> uniquePlayerIds = participants.stream()
            .map(PracticeParticipant::getPlayerId)
            .distinct()
            .toList();

        for (Long playerId : uniquePlayerIds) {
            SendResult result = sendToPlayer(playerId, LineNotificationType.MATCH_PAIRING, message);
            switch (result) {
                case SUCCESS -> sent++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }

        log.info("Match pairing LINE notifications for session {}: sent={}, failed={}, skipped={}",
            sessionId, sent, failed, skipped);
        return LineSendResultResponse.builder()
            .sentPlayerCount(sent).failedPlayerCount(failed).skippedPlayerCount(skipped).build();
    }

    /**
     * 通知設定を取得する
     */
    @Transactional(readOnly = true)
    public List<LineNotificationPreferenceDto> getPreferences(Long playerId) {
        List<LineNotificationPreference> prefs = lineNotificationPreferenceRepository.findByPlayerId(playerId);
        if (prefs.isEmpty()) {
            return List.of();
        }
        return prefs.stream()
                .map(LineNotificationPreferenceDto::fromEntity)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 通知設定を更新する
     */
    @Transactional
    public void updatePreferences(LineNotificationPreferenceDto dto) {
        LineNotificationPreference pref = lineNotificationPreferenceRepository
            .findByPlayerIdAndOrganizationId(dto.getPlayerId(), dto.getOrganizationId())
            .orElse(LineNotificationPreference.builder()
                    .playerId(dto.getPlayerId())
                    .organizationId(dto.getOrganizationId())
                    .build());

        pref.setLotteryResult(dto.isLotteryResult());
        pref.setWaitlistOffer(dto.isWaitlistOffer());
        pref.setOfferExpired(dto.isOfferExpired());
        pref.setMatchPairing(dto.isMatchPairing());
        pref.setPracticeReminder(dto.isPracticeReminder());
        pref.setDeadlineReminder(dto.isDeadlineReminder());

        lineNotificationPreferenceRepository.save(pref);
    }

    /**
     * 送信前の共通チェック結果を保持する内部クラス
     */
    private record ResolvedChannel(LineChannelAssignment assignment, LineChannel channel) {}

    /**
     * 送信前の共通チェック（LINKED状態・通知設定・チャネル取得・月間上限）を実行する。
     * チェックを通過した場合は ResolvedChannel を返し、通過しなかった場合は null を返す。
     */
    private ResolvedChannel resolveChannel(Long playerId, LineNotificationType notificationType, String messageForLog) {
        // LINKED状態の割り当てを取得
        Optional<LineChannelAssignment> assignmentOpt =
            lineChannelAssignmentRepository.findByPlayerIdAndStatus(playerId, AssignmentStatus.LINKED);
        if (assignmentOpt.isEmpty()) {
            return null;
        }

        LineChannelAssignment assignment = assignmentOpt.get();

        // 通知設定チェック
        if (!isNotificationEnabled(playerId, notificationType)) {
            logMessage(assignment.getLineChannelId(), playerId, notificationType, messageForLog,
                MessageStatus.SKIPPED, "通知設定がOFF");
            return null;
        }

        // チャネル取得
        LineChannel channel = lineChannelRepository.findById(assignment.getLineChannelId()).orElse(null);
        if (channel == null || channel.getStatus() != LineChannel.ChannelStatus.LINKED) {
            return null;
        }

        // 月間送信上限チェック
        if (channel.getMonthlyMessageCount() >= MONTHLY_MESSAGE_LIMIT) {
            logMessage(channel.getId(), playerId, notificationType, messageForLog,
                MessageStatus.SKIPPED, "月間送信上限超過");
            return null;
        }

        return new ResolvedChannel(assignment, channel);
    }

    /**
     * 送信成功/失敗の後処理（カウント更新・ログ記録）を実行し、SendResultを返す。
     */
    private SendResult handleSendResult(boolean success, LineChannel channel, Long playerId,
                                         LineNotificationType notificationType, String messageForLog) {
        if (success) {
            channel.setMonthlyMessageCount(channel.getMonthlyMessageCount() + 1);
            lineChannelRepository.save(channel);
            logMessage(channel.getId(), playerId, notificationType, messageForLog, MessageStatus.SUCCESS, null);
            return SendResult.SUCCESS;
        } else {
            logMessage(channel.getId(), playerId, notificationType, messageForLog,
                MessageStatus.FAILED, "LINE API送信失敗");
            return SendResult.FAILED;
        }
    }

    /**
     * プレイヤーにFlex Messageを送信する
     */
    public SendResult sendFlexToPlayer(Long playerId, LineNotificationType notificationType,
                                        String altText, Map<String, Object> flexContents) {
        ResolvedChannel resolved = resolveChannel(playerId, notificationType, altText);
        if (resolved == null) {
            return SendResult.SKIPPED;
        }

        boolean success = lineMessagingService.sendPushFlexMessage(
            resolved.channel().getChannelAccessToken(), resolved.assignment().getLineUserId(), altText, flexContents);

        return handleSendResult(success, resolved.channel(), playerId, notificationType, altText);
    }

    /**
     * プレイヤーにLINE通知を送信する
     */
    public SendResult sendToPlayer(Long playerId, LineNotificationType notificationType, String message) {
        ResolvedChannel resolved = resolveChannel(playerId, notificationType, message);
        if (resolved == null) {
            return SendResult.SKIPPED;
        }

        boolean success = lineMessagingService.sendPushMessage(
            resolved.channel().getChannelAccessToken(), resolved.assignment().getLineUserId(), message);

        return handleSendResult(success, resolved.channel(), playerId, notificationType, message);
    }

    private boolean isNotificationEnabled(Long playerId, LineNotificationType type) {
        List<LineNotificationPreference> prefs = lineNotificationPreferenceRepository.findByPlayerId(playerId);
        if (prefs.isEmpty()) return true; // デフォルト全ON

        // いずれかの団体で該当種別がONならtrue
        return prefs.stream().anyMatch(pref -> isLineTypeEnabled(pref, type));
    }

    private boolean isLineTypeEnabled(LineNotificationPreference pref, LineNotificationType type) {
        return switch (type) {
            case LOTTERY_RESULT -> pref.getLotteryResult();
            case WAITLIST_OFFER -> pref.getWaitlistOffer();
            case OFFER_EXPIRED -> pref.getOfferExpired();
            case MATCH_PAIRING -> pref.getMatchPairing();
            case PRACTICE_REMINDER -> pref.getPracticeReminder();
            case DEADLINE_REMINDER -> pref.getDeadlineReminder();
        };
    }

    private void logMessage(Long channelId, Long playerId, LineNotificationType type,
                           String message, MessageStatus status, String error) {
        try {
            lineMessageLogRepository.save(LineMessageLog.builder()
                .lineChannelId(channelId)
                .playerId(playerId)
                .notificationType(type)
                .messageContent(message)
                .status(status)
                .errorMessage(error)
                .build());
        } catch (Exception e) {
            log.error("Failed to save LINE message log: {}", e.getMessage());
        }
    }

    public enum SendResult {
        SUCCESS, FAILED, SKIPPED
    }
}
