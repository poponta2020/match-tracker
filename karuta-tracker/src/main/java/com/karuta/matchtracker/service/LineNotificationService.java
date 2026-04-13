package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LineNotificationPreferenceDto;
import com.karuta.matchtracker.dto.LineSendResultResponse;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineMessageLog.MessageStatus;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
@Slf4j
public class LineNotificationService {

    private final LineChannelRepository lineChannelRepository;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineNotificationPreferenceRepository lineNotificationPreferenceRepository;
    private final LineMessageLogService lineMessageLogService;
    private final LineMessagingService lineMessagingService;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final PlayerRepository playerRepository;
    private final LotteryQueryService lotteryQueryService;
    private final VenueRepository venueRepository;
    private final MentorRelationshipRepository mentorRelationshipRepository;

    public LineNotificationService(
            LineChannelRepository lineChannelRepository,
            LineChannelAssignmentRepository lineChannelAssignmentRepository,
            LineNotificationPreferenceRepository lineNotificationPreferenceRepository,
            LineMessageLogService lineMessageLogService,
            LineMessagingService lineMessagingService,
            PracticeSessionRepository practiceSessionRepository,
            PracticeParticipantRepository practiceParticipantRepository,
            PlayerOrganizationRepository playerOrganizationRepository,
            PlayerRepository playerRepository,
            LotteryQueryService lotteryQueryService,
            VenueRepository venueRepository,
            MentorRelationshipRepository mentorRelationshipRepository) {
        this.lineChannelRepository = lineChannelRepository;
        this.lineChannelAssignmentRepository = lineChannelAssignmentRepository;
        this.lineNotificationPreferenceRepository = lineNotificationPreferenceRepository;
        this.lineMessageLogService = lineMessageLogService;
        this.lineMessagingService = lineMessagingService;
        this.practiceSessionRepository = practiceSessionRepository;
        this.practiceParticipantRepository = practiceParticipantRepository;
        this.playerOrganizationRepository = playerOrganizationRepository;
        this.playerRepository = playerRepository;
        this.lotteryQueryService = lotteryQueryService;
        this.venueRepository = venueRepository;
        this.mentorRelationshipRepository = mentorRelationshipRepository;
    }

    private static final int MONTHLY_MESSAGE_LIMIT = 200;
    private static final int RESERVED_TIMEOUT_MINUTES = 10;
    private static final int MARK_SUCCEEDED_MAX_RETRIES = 2;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    /**
     * 送信成功後のステータス更新をリトライ付きで実行する。
     * markReservationSucceeded が一時的なDB障害で失敗した場合、RESERVED が残留し
     * releaseStaleReservations により FAILED に変更されると重複送信の原因になるため、
     * リトライで成功率を高める。
     */
    private void markSucceededWithRetry(Long playerId, LineMessageLog.LineNotificationType type,
                                        String dedupeKey) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= MARK_SUCCEEDED_MAX_RETRIES; attempt++) {
            try {
                int updated = lineMessageLogService.markReservationSucceeded(playerId, type, dedupeKey);
                if (updated == 0) {
                    log.warn("markReservationSucceeded updated 0 rows: player={}, dedupeKey={}", playerId, dedupeKey);
                }
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt < MARK_SUCCEEDED_MAX_RETRIES) {
                    log.warn("markReservationSucceeded リトライ {}/{}: player={}, dedupeKey={}, error={}",
                            attempt + 1, MARK_SUCCEEDED_MAX_RETRIES, playerId, dedupeKey, e.getMessage());
                }
            }
        }
        log.error("送信成功後のステータス更新が全リトライで失敗しました（重複送信防止のためFAILEDには変更しません）: player={}, dedupeKey={}, error={}",
                playerId, dedupeKey, lastException != null ? lastException.getMessage() : "unknown");
    }

    /**
     * セッションのラベルを生成する（例: "4月5日（中央公民館）"）
     * 会場が未設定の場合は日付のみ返す。
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
     * キャンセル待ち繰り上げ通知をFlex Messageで送信する
     */
    public void sendWaitlistOfferNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String sessionLabel = getSessionLabel(session);
        String deadlineStr = participant.getOfferDeadline() != null
            ? participant.getOfferDeadline().format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            : "不明";
        String altText = String.format("%s %d試合目に空きが出ました。参加しますか？（期限: %s）",
            sessionLabel, participant.getMatchNumber(), deadlineStr);

        // 応答期限まで12時間未満の場合は緊急フラグ
        boolean isUrgent = participant.getOfferDeadline() != null
            && java.time.Duration.between(JstDateTimeUtil.now(), participant.getOfferDeadline()).toHours() < 12;

        Map<String, Object> flexContents = buildWaitlistOfferFlex(
            sessionLabel, participant.getMatchNumber(), deadlineStr, participant.getId(),
            participant.getSessionId(), participant.getPlayerId(), isUrgent);

        sendFlexToPlayer(participant.getPlayerId(), LineNotificationType.WAITLIST_OFFER, altText, flexContents);
    }

    /**
     * キャンセル待ち列に残っているユーザーに順番繰り上がり通知を送信する（管理者と同じFlexメッセージ）
     *
     * @param triggerAction          発生イベント
     * @param triggerPlayer          イベントを起こしたプレイヤー
     * @param session                対象セッション
     * @param matchNumbers           対象試合番号のリスト
     * @param waitlistByMatch        試合番号→キャンセル待ち列のマップ
     * @param offeredPlayerByMatch   試合番号→オファー先プレイヤーのマップ
     */
    public void sendWaitlistPositionUpdateNotifications(String triggerAction, Player triggerPlayer,
                                                         PracticeSession session,
                                                         List<Integer> matchNumbers,
                                                         Map<Integer, List<PracticeParticipant>> waitlistByMatch,
                                                         Map<Integer, Player> offeredPlayerByMatch) {
        // 全試合のWAITLISTEDユーザーを収集（OFFERED は除外、重複排除）
        Set<Long> allWaitlistedPlayerIds = new LinkedHashSet<>();
        for (List<PracticeParticipant> wl : waitlistByMatch.values()) {
            for (PracticeParticipant wp : wl) {
                if (wp.getStatus() == ParticipantStatus.WAITLISTED) {
                    allWaitlistedPlayerIds.add(wp.getPlayerId());
                }
            }
        }
        if (allWaitlistedPlayerIds.isEmpty()) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String eventText = getEventText(triggerAction);
        String altText = String.format("【キャンセル待ち状況】%s: %sが%s", dateStr, triggerPlayer.getName(), eventText);

        Map<Long, String> playerNames = resolvePlayerNames(waitlistByMatch);

        Map<String, Object> flex = buildAdminWaitlistFlex(
            triggerAction, triggerPlayer.getName(), session, matchNumbers,
            waitlistByMatch, offeredPlayerByMatch, playerNames);

        for (Long playerId : allWaitlistedPlayerIds) {
            sendFlexToPlayer(playerId, LineNotificationType.WAITLIST_POSITION_UPDATE, altText, flex);
        }

        log.info("Waitlist position update notifications sent for session {} matches {}: {} players",
            session.getId(), matchNumbers, allWaitlistedPlayerIds.size());
    }

    /**
     * 操作確認用Flex Message（Bubble）を構築する。
     *
     * @param action 元のアクション名（waitlist_accept等）
     * @param sessionLabel セッションラベル（例: "4月5日（中央公民館）"）
     * @param matchNumber 試合番号（null可：キャンセル待ち一括辞退の場合）
     * @param confirmAction 確定ボタンのpostbackデータ
     * @param cancelAction キャンセルボタンのpostbackデータ
     * @return Flex Message（Bubble）のMap
     */
    public Map<String, Object> buildConfirmationFlex(String action, String sessionLabel,
                                                      Integer matchNumber,
                                                      String confirmAction, String cancelAction) {
        String confirmMessage = buildConfirmMessage(action, sessionLabel, matchNumber);

        // ヘッダー
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "操作の確認",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#F39C12",
            "paddingAll", "15px"
        );

        // ボディ
        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", confirmMessage,
                    "weight", "bold", "size", "md", "wrap", true)
            ),
            "paddingAll", "20px"
        );

        // フッター（確定・キャンセルボタン）
        Map<String, Object> confirmButton = Map.of(
            "type", "button",
            "action", Map.of(
                "type", "postback",
                "label", "確定",
                "data", confirmAction
            ),
            "style", "primary",
            "color", "#27AE60",
            "height", "sm"
        );

        Map<String, Object> cancelButton = Map.of(
            "type", "button",
            "action", Map.of(
                "type", "postback",
                "label", "キャンセル",
                "data", cancelAction
            ),
            "style", "secondary",
            "height", "sm"
        );

        Map<String, Object> footer = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(confirmButton, cancelButton),
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
     * 操作種別に応じた確認文言を生成する
     */
    private String buildConfirmMessage(String action, String sessionLabel, Integer matchNumber) {
        String matchPart = matchNumber != null ? matchNumber + "試合目" : "";
        return switch (action) {
            case "waitlist_accept" -> sessionLabel + matchPart + "の繰り上げ参加を承諾します。よろしいですか？";
            case "waitlist_decline" -> sessionLabel + matchPart + "の繰り上げ参加を辞退します。よろしいですか？";
            case "waitlist_accept_all" -> sessionLabel + "のすべての試合に参加します。よろしいですか？";
            case "waitlist_decline_all" -> sessionLabel + "のすべてのオファーを辞退します。よろしいですか？";
            case "waitlist_decline_session" -> sessionLabel + "のキャンセル待ちをすべて辞退します。よろしいですか？";
            case "same_day_join" -> sessionLabel + matchPart + "に当日参加します。よろしいですか？";
            case "same_day_join_all" -> sessionLabel + "のすべての空き試合に当日参加します。よろしいですか？";
            default -> "この操作を実行します。よろしいですか？";
        };
    }

    /**
     * キャンセル待ち繰り上げ用Flex Message（Bubble）を構築する
     */
    private Map<String, Object> buildWaitlistOfferFlex(String sessionLabel, int matchNumber,
                                                        String deadlineStr, Long participantId,
                                                        Long sessionId, Long playerId, boolean isUrgent) {
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
            Map.of("type", "text", "text", sessionLabel + "の練習",
                "weight", "bold", "size", "lg", "margin", "none", "wrap", true),
            Map.of("type", "text", "text", matchNumber + "試合目に空きが出ました",
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
                "data", "action=waitlist_decline_all&sessionId=" + sessionId + "&playerId=" + playerId
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

        String sessionLabel = getSessionLabel(session);
        String message = accepted
            ? String.format("%s %d試合目の繰り上げ参加を承諾しました。", sessionLabel, participant.getMatchNumber())
            : String.format("%s %d試合目の繰り上げ参加を辞退しました。", sessionLabel, participant.getMatchNumber());

        sendToPlayer(participant.getPlayerId(), LineNotificationType.WAITLIST_OFFER, message);
    }

    /**
     * 一括オファー応答の確認通知を送信する
     */
    public void sendBatchOfferResponseConfirmation(Long sessionId, Long playerId, boolean accepted, int count) {
        PracticeSession session = practiceSessionRepository.findById(sessionId).orElse(null);
        if (session == null) return;

        String sessionLabel = getSessionLabel(session);
        String message = accepted
            ? String.format("%sの繰り上げ参加を%d件一括承諾しました。", sessionLabel, count)
            : String.format("%sの繰り上げ参加を%d件一括辞退しました。", sessionLabel, count);

        sendToPlayer(playerId, LineNotificationType.WAITLIST_OFFER, message);
    }

    /**
     * オファー期限切れ通知を送信する
     */
    public void sendOfferExpiredNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String sessionLabel = getSessionLabel(session);
        String message = String.format("%s %d試合目の繰り上げ参加の期限が切れました",
            sessionLabel, participant.getMatchNumber());

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
                    "落選した試合があります...");
                if (r1 == SendResult.SUCCESS) anySuccess = true;
                else if (r1 == SendResult.FAILED) anyFailed = true;

                // セッション別Flex Message
                Map<Long, List<PracticeParticipant>> waitlistedBySession = waitlisted.stream()
                    .collect(Collectors.groupingBy(PracticeParticipant::getSessionId));

                for (Map.Entry<Long, List<PracticeParticipant>> sessionEntry : waitlistedBySession.entrySet()) {
                    PracticeSession session = sessionCache.get(sessionEntry.getKey());
                    if (session == null) continue;

                    String sessionLabel2 = getSessionLabel(session);
                    Map<String, Object> flex = buildLotteryWaitlistedFlex(
                        sessionLabel2, sessionEntry.getValue(), sessionEntry.getKey(), playerId);
                    String altText = sessionLabel2 + "の練習: キャンセル待ち";

                    SendResult r2 = sendFlexToPlayer(playerId, LineNotificationType.LOTTERY_RESULT, altText, flex);
                    if (r2 == SendResult.SUCCESS) anySuccess = true;
                    else if (r2 == SendResult.FAILED) anyFailed = true;
                }

                // 当選分がある場合のみクロージング
                if (hasWon) {
                    SendResult r3 = sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                        "これ以外の参加登録はすべて通っています");
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
     * キャンセル待ちの参加者のみに抽選結果をLINE送信する
     */
    @Transactional
    public LineSendResultResponse sendLotteryResultsWaitlistedOnly(int year, int month) {
        int sentPlayers = 0, failedPlayers = 0, skippedPlayers = 0;

        List<PracticeParticipant> participants = practiceParticipantRepository
            .findBySessionDateYearAndMonth(year, month);

        // セッション情報をキャッシュ
        Map<Long, PracticeSession> sessionCache = new HashMap<>();
        for (PracticeParticipant p : participants) {
            sessionCache.computeIfAbsent(p.getSessionId(),
                id -> practiceSessionRepository.findById(id).orElse(null));
        }

        // WAITLISTED のみ対象、プレイヤーごとにグルーピング
        Map<Long, List<PracticeParticipant>> byPlayer = participants.stream()
            .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED)
            .collect(Collectors.groupingBy(PracticeParticipant::getPlayerId));

        for (Map.Entry<Long, List<PracticeParticipant>> entry : byPlayer.entrySet()) {
            Long playerId = entry.getKey();

            // 団体フィルタ
            Long sessionOrgId = entry.getValue().stream()
                    .map(p -> sessionCache.get(p.getSessionId()))
                    .filter(java.util.Objects::nonNull)
                    .map(PracticeSession::getOrganizationId)
                    .findFirst().orElse(null);
            if (sessionOrgId != null && !playerOrganizationRepository.existsByPlayerIdAndOrganizationId(playerId, sessionOrgId)) {
                skippedPlayers++;
                continue;
            }

            List<PracticeParticipant> waitlisted = entry.getValue();
            boolean anySuccess = false;
            boolean anyFailed = false;

            // イントロメッセージ
            SendResult r1 = sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                "落選した試合があります...");
            if (r1 == SendResult.SUCCESS) anySuccess = true;
            else if (r1 == SendResult.FAILED) anyFailed = true;

            // セッション別Flex Message
            Map<Long, List<PracticeParticipant>> waitlistedBySession = waitlisted.stream()
                .collect(Collectors.groupingBy(PracticeParticipant::getSessionId));

            for (Map.Entry<Long, List<PracticeParticipant>> sessionEntry : waitlistedBySession.entrySet()) {
                PracticeSession session = sessionCache.get(sessionEntry.getKey());
                if (session == null) continue;

                String sessionLabel2 = getSessionLabel(session);
                Map<String, Object> flex = buildLotteryWaitlistedFlex(
                    sessionLabel2, sessionEntry.getValue(), sessionEntry.getKey(), playerId);
                String altText = sessionLabel2 + "の練習: キャンセル待ち";

                SendResult r2 = sendFlexToPlayer(playerId, LineNotificationType.LOTTERY_RESULT, altText, flex);
                if (r2 == SendResult.SUCCESS) anySuccess = true;
                else if (r2 == SendResult.FAILED) anyFailed = true;
            }

            if (anySuccess) sentPlayers++;
            else if (anyFailed) failedPlayers++;
            else skippedPlayers++;
        }

        log.info("Waitlisted-only LINE notifications: sentPlayers={}, failedPlayers={}, skippedPlayers={}",
            sentPlayers, failedPlayers, skippedPlayers);
        return LineSendResultResponse.builder()
            .sentPlayerCount(sentPlayers).failedPlayerCount(failedPlayers).skippedPlayerCount(skippedPlayers).build();
    }

    /**
     * セッション単位のキャンセル待ちFlex Message（Bubble）を構築する
     */
    private Map<String, Object> buildLotteryWaitlistedFlex(String sessionLabel,
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
        bodyContents.add(Map.of("type", "text", "text", sessionLabel,
            "weight", "bold", "size", "lg", "margin", "none", "wrap", true));

        for (PracticeParticipant p : waitlistedInSession) {
            bodyContents.add(Map.of("type", "text",
                "text", String.format("%d試合目: キャンセル待ち%d番", p.getMatchNumber(), p.getWaitlistNumber()),
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

            List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionDateYearAndMonth(year, month);

            // セッション情報をキャッシュ
            Map<Long, PracticeSession> sessionCache = new HashMap<>();
            for (PracticeParticipant p : participants) {
                sessionCache.computeIfAbsent(p.getSessionId(),
                    id -> practiceSessionRepository.findById(id).orElse(null));
            }

            // 団体ごとの確定状態をキャッシュ
            Map<Long, Boolean> confirmedByOrg = new HashMap<>();

            // 対象ユーザーの WON/WAITLISTED/OFFERED かつ所属団体の抽選が確定済みのもののみ
            List<PracticeParticipant> playerParticipants = participants.stream()
                .filter(p -> p.getPlayerId().equals(playerId))
                .filter(p -> p.getStatus() == ParticipantStatus.WON
                          || p.getStatus() == ParticipantStatus.WAITLISTED
                          || p.getStatus() == ParticipantStatus.OFFERED)
                .filter(p -> {
                    PracticeSession session = sessionCache.get(p.getSessionId());
                    if (session == null) return false;
                    Long orgId = session.getOrganizationId();
                    return confirmedByOrg.computeIfAbsent(orgId,
                            id -> lotteryQueryService.isLotteryConfirmed(year, month, id));
                })
                .collect(Collectors.toList());

            if (playerParticipants.isEmpty()) {
                continue;
            }

            List<PracticeParticipant> waitlisted = playerParticipants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED)
                .collect(Collectors.toList());
            List<PracticeParticipant> offered = playerParticipants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.OFFERED)
                .collect(Collectors.toList());
            boolean hasWon = playerParticipants.stream()
                .anyMatch(p -> p.getStatus() == ParticipantStatus.WON);
            boolean hasNonWon = !waitlisted.isEmpty() || !offered.isEmpty();

            if (!hasNonWon && hasWon) {
                sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                    "申し込んだ練習はすべて当選しました");
            } else if (hasNonWon) {
                sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                    "落選した試合があります...");

                // キャンセル待ちの練習を通知
                Map<Long, List<PracticeParticipant>> waitlistedBySession = waitlisted.stream()
                    .collect(Collectors.groupingBy(PracticeParticipant::getSessionId));

                for (Map.Entry<Long, List<PracticeParticipant>> sessionEntry : waitlistedBySession.entrySet()) {
                    PracticeSession session = sessionCache.get(sessionEntry.getKey());
                    if (session == null) continue;

                    String sessionLabel3 = getSessionLabel(session);
                    Map<String, Object> flex = buildLotteryWaitlistedFlex(
                        sessionLabel3, sessionEntry.getValue(), sessionEntry.getKey(), playerId);
                    String altText = sessionLabel3 + "の練習: キャンセル待ち";

                    sendFlexToPlayer(playerId, LineNotificationType.LOTTERY_RESULT, altText, flex);
                }

                // 繰り上げオファー中の練習をセッション単位の統合Flexメッセージで通知
                Map<Long, List<PracticeParticipant>> offeredBySession = offered.stream()
                    .collect(Collectors.groupingBy(PracticeParticipant::getSessionId));
                for (Map.Entry<Long, List<PracticeParticipant>> offeredEntry : offeredBySession.entrySet()) {
                    PracticeSession offeredSession = sessionCache.get(offeredEntry.getKey());
                    if (offeredSession == null) continue;
                    sendConsolidatedWaitlistOfferNotification(
                            offeredEntry.getValue(), offeredSession, null, (Player) null);
                }

                if (hasWon) {
                    sendToPlayer(playerId, LineNotificationType.LOTTERY_RESULT,
                        "これ以外の参加登録はすべて通っています");
                }
            }

            log.info("Sent lottery results for {}-{} to player {} after LINE linking", year, month, playerId);
        }
    }

    /**
     * セッションに対応する管理者受信者（該当団体ADMIN + 全SUPER_ADMIN）を取得する。
     */
    private List<Player> getAdminRecipientsForSession(PracticeSession session) {
        List<Player> recipients = new ArrayList<>(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN));
        recipients.addAll(playerRepository.findByRoleAndAdminOrganizationIdAndActive(
            Player.Role.ADMIN, session.getOrganizationId()));
        return recipients;
    }

    /**
     * 管理者向けキャンセル待ち状況通知を送信する（バッチ対応版）
     */
    public void sendAdminWaitlistNotification(String triggerAction, Player triggerPlayer,
                                               PracticeSession session,
                                               List<Integer> matchNumbers,
                                               Map<Integer, List<PracticeParticipant>> waitlistByMatch,
                                               Map<Integer, Player> offeredPlayerByMatch) {
        List<Player> adminRecipients = getAdminRecipientsForSession(session);
        if (adminRecipients.isEmpty()) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String eventText = getEventText(triggerAction);
        String altText = String.format("【管理者通知】%s: %sが%s", dateStr, triggerPlayer.getName(), eventText);

        Map<Long, String> playerNames = resolvePlayerNames(waitlistByMatch);

        Map<String, Object> flex = buildAdminWaitlistFlex(
            triggerAction, triggerPlayer.getName(), session, matchNumbers,
            waitlistByMatch, offeredPlayerByMatch, playerNames);

        for (Player admin : adminRecipients) {
            sendFlexToPlayer(admin.getId(), LineNotificationType.ADMIN_WAITLIST_UPDATE, altText, flex);
        }

        log.info("Admin waitlist notification sent to {} admins for session {} matches {}: {} by {}",
            adminRecipients.size(), session.getId(), matchNumbers, triggerAction, triggerPlayer.getName());
    }

    /** triggerAction に応じたヘッダーテキストを返す */
    private String getHeaderText(String triggerAction) {
        return switch (triggerAction) {
            case "キャンセル" -> "キャンセル発生通知";
            case "キャンセル（当日補充）" -> "当日キャンセル発生通知";
            case "降格" -> "練習参加者手動入替通知";
            case "オファー辞退" -> "オファー辞退通知";
            case "オファー承諾" -> "オファー承諾通知";
            case "キャンセル待ち辞退" -> "キャンセル待ち辞退通知";
            case "オファー期限切れ" -> "オファー辞退通知（期限切れ）";
            default -> "キャンセル待ち状況通知";
        };
    }

    /** triggerAction に応じたイベント文言を返す */
    private String getEventText(String triggerAction) {
        if (triggerAction == null) return "";
        return switch (triggerAction) {
            case "キャンセル" -> "キャンセル";
            case "キャンセル（当日補充）" -> "当日キャンセル";
            case "降格" -> "管理者操作";
            case "オファー辞退" -> "オファー辞退";
            case "オファー承諾" -> "オファー承諾";
            case "キャンセル待ち辞退" -> "キャンセル待ち辞退";
            case "オファー期限切れ" -> "オファー辞退（期限切れ）";
            default -> triggerAction;
        };
    }

    /** キャンセル待ち列マップからプレイヤー名を解決する */
    private Map<Long, String> resolvePlayerNames(Map<Integer, List<PracticeParticipant>> waitlistByMatch) {
        Map<Long, String> playerNames = new HashMap<>();
        for (List<PracticeParticipant> wl : waitlistByMatch.values()) {
            for (PracticeParticipant wp : wl) {
                playerNames.computeIfAbsent(wp.getPlayerId(),
                    id -> playerRepository.findById(id).map(Player::getName).orElse("不明"));
            }
        }
        return playerNames;
    }

    /** 全試合のキャンセル待ち列が同一かどうか判定する */
    private boolean isWaitlistSameAcrossMatches(Map<Integer, List<PracticeParticipant>> waitlistByMatch) {
        if (waitlistByMatch.size() <= 1) return true;
        List<List<Long>> playerIdLists = waitlistByMatch.values().stream()
            .map(wl -> wl.stream().map(PracticeParticipant::getPlayerId).collect(Collectors.toList()))
            .collect(Collectors.toList());
        List<Long> first = playerIdLists.get(0);
        for (int i = 1; i < playerIdLists.size(); i++) {
            if (!first.equals(playerIdLists.get(i))) return false;
        }
        return true;
    }

    /**
     * 管理者向けキャンセル待ち状況Flex Message（Bubble）を構築する（バッチ対応版）
     */
    private Map<String, Object> buildAdminWaitlistFlex(String triggerAction, String triggerPlayerName,
                                                        PracticeSession session,
                                                        List<Integer> matchNumbers,
                                                        Map<Integer, List<PracticeParticipant>> waitlistByMatch,
                                                        Map<Integer, Player> offeredPlayerByMatch,
                                                        Map<Long, String> playerNames) {
        String headerText = getHeaderText(triggerAction);
        String eventText = getEventText(triggerAction);

        // ヘッダー
        Map<String, Object> header = Map.of(
            "type", "box", "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", headerText,
                    "color", "#ffffff", "weight", "bold", "size", "md")),
            "backgroundColor", "#8E44AD", "paddingAll", "15px");

        List<Object> bodyContents = new ArrayList<>();

        // ① セッション情報
        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String sessionInfo = dateStr;
        if (session.getVenueId() != null) {
            Venue venue = venueRepository.findById(session.getVenueId()).orElse(null);
            String venueName = venue != null ? venue.getName() : "不明";
            int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
            sessionInfo = dateStr + "（" + venueName + "：定員" + capacity + "名）";
        } else if (session.getCapacity() != null && session.getCapacity() > 0) {
            sessionInfo = dateStr + "（定員" + session.getCapacity() + "名）";
        }
        bodyContents.add(Map.of("type", "text", "text", sessionInfo,
            "weight", "bold", "size", "lg", "margin", "none", "wrap", true));

        // ② 該当試合
        String matchText = matchNumbers.stream().sorted().map(String::valueOf)
            .collect(Collectors.joining(", ")) + "試合目";
        bodyContents.add(Map.of("type", "text", "text", matchText,
            "size", "md", "color", "#333333", "margin", "sm", "wrap", true));

        bodyContents.add(Map.of("type", "separator", "margin", "lg"));

        // ③ イベント内容
        bodyContents.add(Map.of("type", "text", "text", triggerPlayerName + " が " + eventText,
            "size", "md", "color", "#333333", "margin", "lg", "wrap", true));

        bodyContents.add(Map.of("type", "separator", "margin", "lg"));

        // ④ キャンセル待ち列
        boolean allSame = isWaitlistSameAcrossMatches(waitlistByMatch);
        boolean allEmpty = waitlistByMatch.values().stream().allMatch(List::isEmpty);

        if (allEmpty) {
            bodyContents.add(Map.of("type", "text", "text", "キャンセル待ち列",
                "size", "xs", "color", "#888888", "margin", "lg"));
            bodyContents.add(Map.of("type", "text", "text", "なし",
                "size", "sm", "color", "#999999", "margin", "sm"));
        } else if (allSame) {
            String label = matchNumbers.size() > 1
                ? "キャンセル待ち列（※キャンセル待ちのメンバーは全試合で同一）"
                : "キャンセル待ち列";
            bodyContents.add(Map.of("type", "text", "text", label,
                "size", "xs", "color", "#888888", "margin", "lg", "wrap", true));
            int anyMatch = matchNumbers.get(0);
            addWaitlistEntries(bodyContents, waitlistByMatch.get(anyMatch),
                offeredPlayerByMatch.get(anyMatch), playerNames);
        } else {
            for (int mn : matchNumbers.stream().sorted().collect(Collectors.toList())) {
                List<PracticeParticipant> wl = waitlistByMatch.getOrDefault(mn, List.of());
                bodyContents.add(Map.of("type", "text", "text", "キャンセル待ち列（" + mn + "試合目）",
                    "size", "xs", "color", "#888888", "margin", "lg", "wrap", true));
                if (wl.isEmpty()) {
                    bodyContents.add(Map.of("type", "text", "text", "なし",
                        "size", "sm", "color", "#999999", "margin", "sm"));
                } else {
                    addWaitlistEntries(bodyContents, wl, offeredPlayerByMatch.get(mn), playerNames);
                }
            }
        }

        Map<String, Object> body = Map.of("type", "box", "layout", "vertical",
            "contents", bodyContents, "paddingAll", "20px");

        return Map.of("type", "bubble", "header", header, "body", body);
    }

    /** キャンセル待ち列のエントリーをボディに追加する */
    private void addWaitlistEntries(List<Object> bodyContents, List<PracticeParticipant> waitlist,
                                     Player offeredPlayer, Map<Long, String> playerNames) {
        for (PracticeParticipant wp : waitlist) {
            String name = playerNames.getOrDefault(wp.getPlayerId(), "不明");
            boolean isOffered = wp.getStatus() == ParticipantStatus.OFFERED;
            if (isOffered) {
                bodyContents.add(Map.of("type", "text",
                    "text", String.format("%d番: %s（オファー応答待ち）", wp.getWaitlistNumber(), name),
                    "size", "sm", "color", "#27AE60", "weight", "bold", "margin", "sm", "wrap", true));
            } else {
                bodyContents.add(Map.of("type", "text",
                    "text", String.format("%d番: %s", wp.getWaitlistNumber(), name),
                    "size", "sm", "color", "#333333", "margin", "sm"));
            }
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

        String message = "今日の練習の対戦組み合わせはこちらです";

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
        pref.setAdminWaitlistUpdate(dto.isAdminWaitlistUpdate());
        pref.setSameDayConfirmation(dto.isSameDayConfirmation());
        pref.setSameDayCancel(dto.isSameDayCancel());
        pref.setSameDayVacancy(dto.isSameDayVacancy());
        pref.setAdminSameDayConfirmation(dto.isAdminSameDayConfirmation());
        pref.setAdminSameDayCancel(dto.isAdminSameDayCancel());
        pref.setMentorComment(dto.isMentorComment());

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
        // 通知種別に応じたチャネル用途を判定し、該当アサインメントを取得
        ChannelType requiredChannelType = notificationType.getRequiredChannelType();
        Optional<LineChannelAssignment> assignmentOpt =
            lineChannelAssignmentRepository.findByPlayerIdAndChannelTypeAndStatusIn(
                playerId, requiredChannelType, List.of(AssignmentStatus.LINKED));
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
        return handleSendResult(success, channel, playerId, notificationType, messageForLog, null);
    }

    private SendResult handleSendResult(boolean success, LineChannel channel, Long playerId,
                                         LineNotificationType notificationType, String messageForLog,
                                         String dedupeKey) {
        if (success) {
            logMessage(channel.getId(), playerId, notificationType, messageForLog, MessageStatus.SUCCESS, null, dedupeKey);
            return SendResult.SUCCESS;
        } else {
            logMessage(channel.getId(), playerId, notificationType, messageForLog,
                MessageStatus.FAILED, "LINE API送信失敗", dedupeKey);
            return SendResult.FAILED;
        }
    }

    /**
     * 当日12:00の参加者確定通知を送信する。
     * WON参加者に、試合ごとのメンバーリストをFlex Messageで通知。
     */
    public void sendSameDayConfirmationNotification(PracticeSession session) {
        List<PracticeParticipant> allParticipants = practiceParticipantRepository
                .findBySessionIdAndStatus(session.getId(), ParticipantStatus.WON);

        if (allParticipants.isEmpty()) {
            log.debug("No WON participants for session {} on {}", session.getId(), session.getSessionDate());
            return;
        }

        // 試合番号ごとにグループ化
        Map<Integer, List<PracticeParticipant>> byMatch = allParticipants.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber,
                        java.util.TreeMap::new, Collectors.toList()));

        if (byMatch.isEmpty()) return;

        // プレイヤー情報を一括取得（段位ソートのため）
        List<Long> playerIds = allParticipants.stream()
                .map(PracticeParticipant::getPlayerId).distinct().toList();
        Map<Long, Player> playerMap = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, p -> p));

        String sessionLabel = getSessionLabel(session);

        // Flex Messageの構築
        Map<String, Object> flexContents = buildSameDayConfirmationFlex(sessionLabel, byMatch, playerMap);
        String altText = sessionLabel + "の練習メンバーが確定しました";

        // WON参加者（重複除去）に送信
        for (Long playerId : playerIds) {
            try {
                sendFlexToPlayer(playerId, LineNotificationType.SAME_DAY_CONFIRMATION, altText, flexContents);
            } catch (Exception e) {
                log.error("Failed to send confirmation to player {}: {}", playerId, e.getMessage());
            }
        }

        // 管理者（該当団体ADMIN + 全SUPER_ADMIN）にも送信（WON参加者でも管理者通知は別チャネルで送信）
        List<Player> adminRecipients = getAdminRecipientsForSession(session);
        int adminSentCount = 0;
        for (Player admin : adminRecipients) {
            try {
                sendFlexToPlayer(admin.getId(), LineNotificationType.ADMIN_SAME_DAY_CONFIRMATION, altText, flexContents);
                adminSentCount++;
            } catch (Exception e) {
                log.error("Failed to send admin confirmation to player {}: {}", admin.getId(), e.getMessage());
            }
        }

        log.info("Sent same-day confirmation to {} players and {} admins for session {}",
                playerIds.size(), adminSentCount, session.getId());
    }

    // ===== リッチメニュー照会用 Flex Message ビルダー =====

    /**
     * キャンセル待ち状況一覧のFlex Messageを構築する
     */
    public Map<String, Object> buildWaitlistStatusFlex(List<Map<String, Object>> entries) {
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "キャンセル待ち状況",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#9C27B0",
            "paddingAll", "15px"
        );

        // セッション単位でグループ化（sessionIdがあればそれを優先、なければsessionLabelでフォールバック）
        java.util.LinkedHashMap<String, List<Map<String, Object>>> bySession = new java.util.LinkedHashMap<>();
        for (Map<String, Object> entry : entries) {
            Object sessionId = entry.get("sessionId");
            String groupKey = sessionId != null ? String.valueOf(sessionId) : (String) entry.get("sessionLabel");
            bySession.computeIfAbsent(groupKey, k -> new java.util.ArrayList<>()).add(entry);
        }

        List<Object> bodyContents = new java.util.ArrayList<>();
        boolean first = true;

        for (Map.Entry<String, List<Map<String, Object>>> sessionEntry : bySession.entrySet()) {
            if (!first) {
                bodyContents.add(Map.of("type", "separator", "margin", "lg"));
            }

            // 表示用ラベルはグループ先頭要素のsessionLabelを使用
            String displayLabel = (String) sessionEntry.getValue().get(0).get("sessionLabel");
            bodyContents.add(Map.of("type", "text", "text", displayLabel,
                    "weight", "bold", "size", "md", "margin", first ? "none" : "lg",
                    "wrap", true));

            for (Map<String, Object> entry : sessionEntry.getValue()) {
                Integer matchNumber = (Integer) entry.get("matchNumber");
                Integer waitlistNumber = (Integer) entry.get("waitlistNumber");
                String status = (String) entry.get("status");
                Object offerDeadline = entry.get("offerDeadline");

                String line;
                if ("OFFERED".equals(status)) {
                    line = matchNumber + "試合目 繰り上げオファー中";
                    if (offerDeadline != null) {
                        java.time.LocalDateTime deadline = (java.time.LocalDateTime) offerDeadline;
                        String deadlineStr = deadline.format(java.time.format.DateTimeFormatter.ofPattern("M/d H:mm"));
                        line += " 期限：" + deadlineStr;
                    }
                    bodyContents.add(Map.of("type", "text", "text", line,
                            "size", "sm", "color", "#E65100", "weight", "bold", "margin", "sm",
                            "wrap", true));
                } else {
                    line = matchNumber + "試合目 キャンセル待ち" + waitlistNumber + "番";
                    bodyContents.add(Map.of("type", "text", "text", line,
                            "size", "sm", "color", "#333333", "margin", "sm"));
                }
            }

            first = false;
        }

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body
        );
    }

    /**
     * 今日の参加者一覧のFlex Messageを構築する（リッチメニュー照会用）
     * ヘッダーに「〇月〇日（会場名：定員〇名）」、本文に「〇試合目：〇名」形式で表示
     */
    public Map<String, Object> buildTodayParticipantsFlex(
            String sessionLabel, Map<Integer, List<PracticeParticipant>> byMatch, Map<Long, Player> playerMap,
            Integer capacity) {

        String headerText = capacity != null
                ? sessionLabel.replaceAll("）$", "：定員" + capacity + "名）")
                : sessionLabel;

        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", headerText,
                    "color", "#ffffff", "weight", "bold", "size", "md", "wrap", true)
            ),
            "backgroundColor", "#2196F3",
            "paddingAll", "15px"
        );

        List<Object> bodyContents = new java.util.ArrayList<>();

        for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
            if (!bodyContents.isEmpty()) {
                bodyContents.add(Map.of("type", "separator", "margin", "lg"));
            }
            bodyContents.add(Map.of("type", "text", "text",
                    entry.getKey() + "試合目：" + entry.getValue().size() + "名",
                    "weight", "bold", "size", "md", "margin", "lg", "color", "#333333"));

            // 段位順（高段位→低段位）でソート
            List<PracticeParticipant> sorted = entry.getValue().stream()
                    .sorted((a, b) -> Integer.compare(
                            danRankOrdinal(playerMap.get(b.getPlayerId())),
                            danRankOrdinal(playerMap.get(a.getPlayerId()))))
                    .toList();

            // 3人ずつ1行に並べる
            for (int i = 0; i < sorted.size(); i += 3) {
                List<Object> rowContents = new java.util.ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    if (i + j < sorted.size()) {
                        Player p = playerMap.get(sorted.get(i + j).getPlayerId());
                        String name = p != null ? p.getName() : "不明";
                        rowContents.add(Map.of("type", "text", "text", name,
                                "flex", 1, "size", "sm", "color", "#555555"));
                    } else {
                        rowContents.add(Map.of("type", "text", "text", " ",
                                "flex", 1, "size", "sm"));
                    }
                }

                bodyContents.add(Map.of("type", "box", "layout", "horizontal",
                        "contents", rowContents, "margin", "sm"));
            }
        }

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body
        );
    }

    /**
     * 当日参加申込可能試合一覧のFlex Messageを構築する
     */
    public Map<String, Object> buildSameDayJoinFlex(
            String sessionLabel, List<Map<String, Object>> availableMatches, Long sessionId) {
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "当日参加申込",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#FF9800",
            "paddingAll", "15px"
        );

        List<Object> bodyContents = new java.util.ArrayList<>();
        bodyContents.add(Map.of("type", "text", "text", sessionLabel + "の練習",
                "weight", "bold", "size", "lg", "margin", "none", "wrap", true));
        bodyContents.add(Map.of("type", "text", "text",
                "以下の試合に空きがあります。参加希望の試合の「参加する」ボタンを押してください。",
                "size", "sm", "color", "#555555", "margin", "md", "wrap", true));

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        List<Object> footerContents = new java.util.ArrayList<>();
        for (Map<String, Object> match : availableMatches) {
            int matchNumber = (int) match.get("matchNumber");
            int vacancy = (int) match.get("vacancy");

            footerContents.add(Map.of(
                "type", "button",
                "action", Map.of(
                    "type", "postback",
                    "label", matchNumber + "試合目（空き" + vacancy + "名）",
                    "data", "action=same_day_join&sessionId=" + sessionId + "&matchNumber=" + matchNumber
                ),
                "style", "primary",
                "color", "#FF9800",
                "height", "sm",
                "margin", "sm"
            ));
        }

        Map<String, Object> footer = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", footerContents,
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
     * 段位の比較用序数を返す（高段位ほど大きい値、null/無段は-1）
     */
    private int danRankOrdinal(Player player) {
        if (player == null || player.getDanRank() == null) return -1;
        return player.getDanRank().ordinal();
    }

    /**
     * 参加者確定通知のFlex Messageを構築する（2列レイアウト・段位順）
     */
    private Map<String, Object> buildSameDayConfirmationFlex(
            String sessionLabel, Map<Integer, List<PracticeParticipant>> byMatch, Map<Long, Player> playerMap) {

        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "本日の練習メンバー",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#2196F3",
            "paddingAll", "15px"
        );

        List<Object> bodyContents = new java.util.ArrayList<>();
        bodyContents.add(Map.of("type", "text", "text", sessionLabel + "の練習",
                "weight", "bold", "size", "lg", "margin", "none", "wrap", true));

        for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
            bodyContents.add(Map.of("type", "separator", "margin", "lg"));
            bodyContents.add(Map.of("type", "text", "text", entry.getKey() + "試合目",
                    "weight", "bold", "size", "md", "margin", "lg", "color", "#333333"));

            // 段位順（高段位→低段位）でソート
            List<PracticeParticipant> sorted = entry.getValue().stream()
                    .sorted((a, b) -> Integer.compare(
                            danRankOrdinal(playerMap.get(b.getPlayerId())),
                            danRankOrdinal(playerMap.get(a.getPlayerId()))))
                    .toList();

            // 3人ずつ1行に並べる
            for (int i = 0; i < sorted.size(); i += 3) {
                List<Object> rowContents = new java.util.ArrayList<>();
                for (int j = 0; j < 3; j++) {
                    if (i + j < sorted.size()) {
                        Player p = playerMap.get(sorted.get(i + j).getPlayerId());
                        String name = p != null ? p.getName() : "不明";
                        rowContents.add(Map.of("type", "text", "text", name,
                                "flex", 1, "size", "sm", "color", "#555555"));
                    } else {
                        rowContents.add(Map.of("type", "text", "text", " ",
                                "flex", 1, "size", "sm"));
                    }
                }

                bodyContents.add(Map.of("type", "box", "layout", "horizontal",
                        "contents", rowContents, "margin", "sm"));
            }
        }

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body
        );
    }

    /**
     * 当日キャンセル通知を送信する。
     * 当該セッションの全WON参加者（キャンセル本人除く）にテキスト通知。
     */
    public void sendSameDayCancelNotification(PracticeSession session, int matchNumber,
                                               String cancelledPlayerName, Long cancelledPlayerId) {
        String message = String.format("%sさんが今日の%d試合目をキャンセルしました", cancelledPlayerName, matchNumber);

        List<PracticeParticipant> wonParticipants = practiceParticipantRepository
                .findBySessionIdAndStatus(session.getId(), ParticipantStatus.WON);

        List<Long> recipientIds = wonParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .filter(id -> !id.equals(cancelledPlayerId))
                .toList();

        for (Long playerId : recipientIds) {
            try {
                sendToPlayer(playerId, LineNotificationType.SAME_DAY_CANCEL, message);
            } catch (Exception e) {
                log.error("Failed to send same-day cancel notification to player {}: {}", playerId, e.getMessage());
            }
        }

        // 管理者（該当団体ADMIN + 全SUPER_ADMIN）にも送信
        List<Player> adminRecipients = getAdminRecipientsForSession(session);
        for (Player admin : adminRecipients) {
            try {
                sendToPlayer(admin.getId(), LineNotificationType.ADMIN_SAME_DAY_CANCEL, message);
            } catch (Exception e) {
                log.error("Failed to send admin cancel notification to player {}: {}", admin.getId(), e.getMessage());
            }
        }

        log.info("Sent same-day cancel notification to {} players and {} admins for session {} match {}",
                recipientIds.size(), adminRecipients.size(), session.getId(), matchNumber);
    }

    /**
     * 空き募集通知を送信する。
     * 当該セッションの非WON参加者（キャンセル本人除く）にFlex Message。
     */
    public void sendSameDayVacancyNotification(PracticeSession session, int matchNumber, Long cancelledPlayerId) {
        // RESERVED残留の回復: タイムアウトしたRESERVEDをFAILEDに解放
        int released = lineMessageLogService.releaseStaleReservations(
                JstDateTimeUtil.now().minusMinutes(RESERVED_TIMEOUT_MINUTES));
        if (released > 0) {
            log.warn("Released {} stale RESERVED log entries (timeout={}min)", released, RESERVED_TIMEOUT_MINUTES);
        }

        String sessionLabel = getSessionLabel(session);

        // 現在のWON数と定員から空き枠数を計算
        List<PracticeParticipant> currentWon = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);
        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        int vacancies = Math.max(0, capacity - currentWon.size());

        if (vacancies <= 0) {
            log.debug("No vacancies for session {} match {} (capacity={}, won={})",
                    session.getId(), matchNumber, capacity, currentWon.size());
            return;
        }

        // 送信先: 団体全メンバー（該当試合のWON参加者を除く、キャンセル本人も除く）
        List<PlayerOrganization> orgMembers = playerOrganizationRepository
                .findByOrganizationId(session.getOrganizationId());
        Set<Long> wonPlayerIds = currentWon.stream()
                .map(PracticeParticipant::getPlayerId).collect(Collectors.toSet());
        List<Long> recipientIds = orgMembers.stream()
                .map(PlayerOrganization::getPlayerId)
                .distinct()
                .filter(id -> cancelledPlayerId == null || !id.equals(cancelledPlayerId))
                .filter(id -> !wonPlayerIds.contains(id))
                .toList();

        if (recipientIds.isEmpty()) return;

        String altText = String.format("%s %d試合目が%d名分空いています", sessionLabel, matchNumber, vacancies);
        Map<String, Object> flex = buildSameDayVacancyFlex(sessionLabel, matchNumber, vacancies, session.getId(), true);

        int sentCount = 0;
        int alreadyNotifiedCount = 0;
        int failedCount = 0;
        int channelSkippedCount = 0;
        String dedupeKey = session.getId() + ":" + matchNumber;

        for (Long playerId : recipientIds) {
            // チャネル解決（通知設定OFF・チャネル未リンク等のチェック含む）
            ResolvedChannel resolved = resolveChannel(playerId, LineNotificationType.SAME_DAY_VACANCY, altText);
            if (resolved == null) {
                channelSkippedCount++;
                continue;
            }

            // 原子的に送信権を確保（INSERT ... ON CONFLICT DO NOTHING）
            if (!lineMessageLogService.tryAcquireSendRight(
                    resolved.channel().getId(), playerId, LineNotificationType.SAME_DAY_VACANCY, altText, dedupeKey)) {
                alreadyNotifiedCount++;
                continue;
            }

            // 送信権を確保できた場合のみ実際に送信
            boolean sent = false;
            try {
                boolean success = lineMessagingService.sendPushFlexMessage(
                        resolved.channel().getChannelAccessToken(), resolved.assignment().getLineUserId(), altText, flex);
                if (success) {
                    sent = true;
                    sentCount++;
                } else {
                    int updated = lineMessageLogService.markReservationFailed(
                            playerId, LineNotificationType.SAME_DAY_VACANCY, dedupeKey, "LINE API送信失敗");
                    if (updated == 0) {
                        log.warn("markReservationFailed updated 0 rows: player={}, dedupeKey={}", playerId, dedupeKey);
                    }
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to send vacancy notification to player {}: {}", playerId, e.getMessage());
                int updated = lineMessageLogService.markReservationFailed(
                        playerId, LineNotificationType.SAME_DAY_VACANCY, dedupeKey, e.getMessage());
                if (updated == 0) {
                    log.warn("markReservationFailed updated 0 rows: player={}, dedupeKey={}", playerId, dedupeKey);
                }
                failedCount++;
            }

            // 送信成功後のステータス更新（リトライ付き）
            // 送信済みなのにFAILEDに変更すると重複送信の原因になるため、
            // markReservationSucceededが例外を投げてもmarkReservationFailedは呼ばない
            if (sent) {
                markSucceededWithRetry(playerId, LineNotificationType.SAME_DAY_VACANCY, dedupeKey);
            }
        }

        log.info("Sent vacancy notification for session {} match {} ({} vacancies): sent={}, alreadyNotified={}, failed={}, channelSkipped={}",
                session.getId(), matchNumber, vacancies, sentCount, alreadyNotifiedCount, failedCount, channelSkippedCount);
    }

    /**
     * 管理者向け空き枠通知を送信する。
     * 0:00スケジューラから呼び出され、該当団体ADMIN + 全SUPER_ADMINにADMIN_SAME_DAY_CANCELで送信。
     */
    public void sendAdminVacancyNotification(PracticeSession session, int matchNumber) {
        String sessionLabel = getSessionLabel(session);

        List<PracticeParticipant> currentWon = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);
        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        int vacancies = Math.max(0, capacity - currentWon.size());

        if (vacancies <= 0) return;

        String altText = String.format("【管理者通知】%s %d試合目が%d名分空いています", sessionLabel, matchNumber, vacancies);
        Map<String, Object> flex = buildSameDayVacancyFlex(sessionLabel, matchNumber, vacancies, session.getId(), false);

        List<Player> adminRecipients = getAdminRecipientsForSession(session);
        for (Player admin : adminRecipients) {
            try {
                sendFlexToPlayer(admin.getId(), LineNotificationType.ADMIN_SAME_DAY_CANCEL, altText, flex);
            } catch (Exception e) {
                log.error("Failed to send admin vacancy notification to player {}: {}", admin.getId(), e.getMessage());
            }
        }

        log.info("Sent admin vacancy notification to {} admins for session {} match {}",
                adminRecipients.size(), session.getId(), matchNumber);
    }

    /**
     * セッション単位で空き枠通知を統合して送信する（選手向け）。
     * 複数試合の空き枠情報を1通のFlex Messageにまとめる。
     *
     * @param session           対象セッション
     * @param vacanciesByMatch  試合番号 → 空き枠数のマップ（空き枠 > 0 のもののみ）
     * @param cancelledPlayerId キャンセルしたプレイヤーのID（除外対象、nullの場合は除外なし）
     */
    public void sendConsolidatedSameDayVacancyNotification(PracticeSession session,
                                                            Map<Integer, Integer> vacanciesByMatch,
                                                            Long cancelledPlayerId) {
        if (vacanciesByMatch.isEmpty()) return;

        // RESERVED残留の回復: タイムアウトしたRESERVEDをFAILEDに解放
        int released = lineMessageLogService.releaseStaleReservations(
                JstDateTimeUtil.now().minusMinutes(RESERVED_TIMEOUT_MINUTES));
        if (released > 0) {
            log.warn("Released {} stale RESERVED log entries (timeout={}min)", released, RESERVED_TIMEOUT_MINUTES);
        }

        String sessionLabel = getSessionLabel(session);

        // 試合ごとのWONプレイヤーを収集
        Map<Integer, Set<Long>> wonPlayersByMatch = new HashMap<>();
        for (Integer matchNumber : vacanciesByMatch.keySet()) {
            List<PracticeParticipant> currentWon = practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);
            Set<Long> wonIds = currentWon.stream()
                    .map(PracticeParticipant::getPlayerId)
                    .collect(Collectors.toSet());
            wonPlayersByMatch.put(matchNumber, wonIds);
        }

        // 送信先: 団体全メンバーのうち、空きのある試合の少なくとも1試合で未WONなら送信
        List<PlayerOrganization> orgMembers = playerOrganizationRepository
                .findByOrganizationId(session.getOrganizationId());
        List<Long> recipientIds = orgMembers.stream()
                .map(PlayerOrganization::getPlayerId)
                .distinct()
                .filter(id -> cancelledPlayerId == null || !id.equals(cancelledPlayerId))
                .filter(id -> wonPlayersByMatch.values().stream()
                        .anyMatch(wonIds -> !wonIds.contains(id)))
                .toList();

        if (recipientIds.isEmpty()) return;

        int sentCount = 0;
        int alreadyNotifiedCount = 0;
        int failedCount = 0;
        int channelSkippedCount = 0;
        String dedupeKey = String.valueOf(session.getId());

        for (Long playerId : recipientIds) {
            // プレイヤーごとに参加可能な試合のみを抽出
            Map<Integer, Integer> playerVacancies = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> entry : vacanciesByMatch.entrySet()) {
                Set<Long> wonIds = wonPlayersByMatch.get(entry.getKey());
                if (wonIds == null || !wonIds.contains(playerId)) {
                    playerVacancies.put(entry.getKey(), entry.getValue());
                }
            }
            if (playerVacancies.isEmpty()) continue;

            String matchSummary = playerVacancies.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getValue() > 0
                            ? e.getKey() + "試合目:" + e.getValue() + "名"
                            : e.getKey() + "試合目:満枠")
                    .collect(Collectors.joining(", "));
            String altText = String.format("%s 空き枠のお知らせ（%s）", sessionLabel, matchSummary);

            // チャネル解決（通知設定OFF・チャネル未リンク等のチェック含む）
            ResolvedChannel resolved = resolveChannel(playerId, LineNotificationType.SAME_DAY_VACANCY, altText);
            if (resolved == null) {
                channelSkippedCount++;
                continue;
            }

            // 原子的に送信権を確保（INSERT ... ON CONFLICT DO NOTHING）
            if (!lineMessageLogService.tryAcquireSendRight(
                    resolved.channel().getId(), playerId, LineNotificationType.SAME_DAY_VACANCY, altText, dedupeKey)) {
                alreadyNotifiedCount++;
                continue;
            }

            Map<String, Object> flex = buildConsolidatedSameDayVacancyFlex(
                    sessionLabel, playerVacancies, session.getId(), true);

            // 送信権を確保できた場合のみ実際に送信
            boolean sent = false;
            try {
                boolean success = lineMessagingService.sendPushFlexMessage(
                        resolved.channel().getChannelAccessToken(), resolved.assignment().getLineUserId(), altText, flex);
                if (success) {
                    sent = true;
                    sentCount++;
                } else {
                    int updated = lineMessageLogService.markReservationFailed(
                            playerId, LineNotificationType.SAME_DAY_VACANCY, dedupeKey, "LINE API送信失敗");
                    if (updated == 0) {
                        log.warn("markReservationFailed updated 0 rows: player={}, dedupeKey={}", playerId, dedupeKey);
                    }
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to send consolidated vacancy notification to player {}: {}", playerId, e.getMessage());
                int updated = lineMessageLogService.markReservationFailed(
                        playerId, LineNotificationType.SAME_DAY_VACANCY, dedupeKey, e.getMessage());
                if (updated == 0) {
                    log.warn("markReservationFailed updated 0 rows: player={}, dedupeKey={}", playerId, dedupeKey);
                }
                failedCount++;
            }

            // 送信成功後のステータス更新（リトライ付き）
            // 送信済みなのにFAILEDに変更すると重複送信の原因になるため、
            // markReservationSucceededが例外を投げてもmarkReservationFailedは呼ばない
            if (sent) {
                markSucceededWithRetry(playerId, LineNotificationType.SAME_DAY_VACANCY, dedupeKey);
            }
        }

        log.info("Sent consolidated vacancy notification for session {} ({} matches): sent={}, alreadyNotified={}, failed={}, channelSkipped={}",
                session.getId(), vacanciesByMatch.size(), sentCount, alreadyNotifiedCount, failedCount, channelSkippedCount);
    }

    /**
     * セッション単位で管理者向け空き枠通知を統合して送信する。
     *
     * @param session          対象セッション
     * @param vacanciesByMatch 試合番号 → 空き枠数のマップ
     */
    public void sendConsolidatedAdminVacancyNotification(PracticeSession session,
                                                          Map<Integer, Integer> vacanciesByMatch) {
        if (vacanciesByMatch.isEmpty()) return;

        String sessionLabel = getSessionLabel(session);

        String matchSummary = vacanciesByMatch.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue() > 0
                        ? e.getKey() + "試合目:" + e.getValue() + "名"
                        : e.getKey() + "試合目:満枠")
                .collect(Collectors.joining(", "));
        String altText = String.format("【管理者通知】%s 空き枠のお知らせ（%s）", sessionLabel, matchSummary);

        Map<String, Object> flex = buildConsolidatedSameDayVacancyFlex(
                sessionLabel, vacanciesByMatch, session.getId(), false);

        List<Player> adminRecipients = getAdminRecipientsForSession(session);
        for (Player admin : adminRecipients) {
            try {
                sendFlexToPlayer(admin.getId(), LineNotificationType.ADMIN_SAME_DAY_CANCEL, altText, flex);
            } catch (Exception e) {
                log.error("Failed to send consolidated admin vacancy notification to player {}: {}", admin.getId(), e.getMessage());
            }
        }

        log.info("Sent consolidated admin vacancy notification to {} admins for session {} ({} matches)",
                adminRecipients.size(), session.getId(), vacanciesByMatch.size());
    }

    /**
     * セッション単位統合版の空き枠Flex Messageを構築する。
     * オファー用Flex Messageと同じ構造（ヘッダー + ボディ + フッター）。
     *
     * @param includeButtons trueの場合、試合ごとの「参加する」ボタンと「全試合参加」ボタンを含める
     */
    private Map<String, Object> buildConsolidatedSameDayVacancyFlex(String sessionLabel,
                                                                      Map<Integer, Integer> vacanciesByMatch,
                                                                      Long sessionId, boolean includeButtons) {
        // ヘッダー
        Map<String, Object> header = Map.of(
                "type", "box",
                "layout", "vertical",
                "contents", List.of(
                        Map.of("type", "text", "text", "空き枠のお知らせ",
                                "color", "#ffffff", "weight", "bold", "size", "md")
                ),
                "backgroundColor", "#FF9800",
                "paddingAll", "15px"
        );

        // ボディ
        List<Object> bodyContents = new ArrayList<>(List.of(
                Map.of("type", "text", "text", sessionLabel + "の練習",
                        "weight", "bold", "size", "lg", "margin", "none", "wrap", true)
        ));

        // 各試合の空き枠情報
        List<Map.Entry<Integer, Integer>> sortedEntries = vacanciesByMatch.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        for (Map.Entry<Integer, Integer> entry : sortedEntries) {
            String matchText;
            String textColor;
            if (entry.getValue() > 0) {
                matchText = entry.getKey() + "試合目: " + entry.getValue() + "名分空き";
                textColor = "#333333";
            } else {
                matchText = entry.getKey() + "試合目: 定員に達しました";
                textColor = "#999999";
            }
            bodyContents.add(Map.of("type", "text",
                    "text", matchText,
                    "size", "md", "margin", "sm", "color", textColor, "wrap", true));
        }

        if (!includeButtons) {
            Map<String, Object> body = Map.of(
                    "type", "box",
                    "layout", "vertical",
                    "contents", bodyContents,
                    "paddingAll", "20px"
            );
            return Map.of(
                    "type", "bubble",
                    "header", header,
                    "body", body
            );
        }

        // フッター（ボタン群）— 空きのある試合がない場合はボタンなし
        List<Object> footerContents = buildVacancyJoinButtons(vacanciesByMatch, sessionId);

        // CTA文言はボタンがある場合のみ表示（満枠時の表示不整合を防止）
        if (!footerContents.isEmpty()) {
            bodyContents.add(Map.of("type", "text",
                    "text", "参加希望の場合はボタンを押してください",
                    "size", "sm", "margin", "lg", "color", "#888888", "wrap", true));
        }

        Map<String, Object> body = Map.of(
                "type", "box",
                "layout", "vertical",
                "contents", bodyContents,
                "paddingAll", "20px"
        );

        if (footerContents.isEmpty()) {
            return Map.of(
                    "type", "bubble",
                    "header", header,
                    "body", body
            );
        }
        Map<String, Object> footer = Map.of(
                "type", "box",
                "layout", "vertical",
                "contents", footerContents,
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
     * 空き枠通知用ボタン群を構築する。
     * - 個別参加ボタン（オレンジ） × N
     * - 全試合参加（青）※2試合以上の場合のみ
     */
    private List<Object> buildVacancyJoinButtons(Map<Integer, Integer> vacanciesByMatch, Long sessionId) {
        List<Object> buttons = new ArrayList<>();

        // 空きのある試合のみボタン対象
        Map<Integer, Integer> availableMatches = vacanciesByMatch.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        // 個別参加ボタン（オレンジ）
        List<Integer> sortedMatchNumbers = availableMatches.keySet().stream().sorted().toList();
        for (Integer matchNumber : sortedMatchNumbers) {
            buttons.add(Map.of(
                    "type", "button",
                    "action", Map.of(
                            "type", "postback",
                            "label", matchNumber + "試合目に参加",
                            "data", "action=same_day_join&sessionId=" + sessionId + "&matchNumber=" + matchNumber
                    ),
                    "style", "primary",
                    "color", "#FF9800",
                    "height", "sm"
            ));
        }

        // 全試合参加（青）※空きのある試合が2試合以上の場合のみ
        if (availableMatches.size() >= 2) {
            buttons.add(Map.of(
                    "type", "button",
                    "action", Map.of(
                            "type", "postback",
                            "label", "すべての試合に参加",
                            "data", "action=same_day_join_all&sessionId=" + sessionId
                                    + "&matchNumbers=" + availableMatches.keySet().stream()
                                    .sorted().map(String::valueOf).collect(Collectors.joining(","))
                    ),
                    "style", "primary",
                    "color", "#2E86C1",
                    "height", "sm"
            ));
        }

        return buttons;
    }

    /**
     * 空き募集Flex Messageを構築する（単一試合版）
     * @param includeJoinButton trueの場合「参加する」ボタンを含める。管理者向けはfalse。
     */
    private Map<String, Object> buildSameDayVacancyFlex(String sessionLabel, int matchNumber, int vacancies, Long sessionId, boolean includeJoinButton) {
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "空き枠のお知らせ",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#FF9800",
            "paddingAll", "15px"
        );

        String bodyText = includeJoinButton
            ? String.format("%d試合目が%d名分空いています。参加希望の場合は参加ボタンを押してください", matchNumber, vacancies)
            : String.format("%d試合目が%d名分空いています", matchNumber, vacancies);

        List<Object> bodyContents = List.of(
            Map.of("type", "text", "text", sessionLabel + "の練習",
                "weight", "bold", "size", "lg", "margin", "none", "wrap", true),
            Map.of("type", "text", "text", bodyText,
                "size", "md", "margin", "md", "color", "#333333", "wrap", true)
        );

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        if (!includeJoinButton) {
            return Map.of(
                "type", "bubble",
                "header", header,
                "body", body
            );
        }

        Map<String, Object> joinButton = Map.of(
            "type", "button",
            "action", Map.of(
                "type", "postback",
                "label", "参加する",
                "data", "action=same_day_join&sessionId=" + sessionId + "&matchNumber=" + matchNumber
            ),
            "style", "primary",
            "color", "#FF9800",
            "height", "sm"
        );

        Map<String, Object> footer = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(joinButton),
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
     * 複数試合の参加通知をセッション単位でまとめて送信する。
     * 参加した試合のWONメンバー（参加した本人除く）に「〇〇さんがX,Y,Z試合目に参加します」通知。
     *
     * @param session          対象セッション
     * @param joinedMatches    参加した試合番号リスト
     * @param joinedPlayerName 参加したプレイヤー名
     * @param joinedPlayerId   参加したプレイヤーID
     */
    public void sendConsolidatedSameDayJoinNotification(PracticeSession session, List<Integer> joinedMatches,
                                                         String joinedPlayerName, Long joinedPlayerId) {
        if (joinedMatches.isEmpty()) return;

        String matchList = joinedMatches.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String message = String.format("%sさんが今日の%s試合目に参加します", joinedPlayerName, matchList);

        // 送信先: 参加した試合のいずれかでWONのメンバー（本人除く）
        Set<Long> recipientSet = new java.util.LinkedHashSet<>();
        for (int matchNumber : joinedMatches) {
            List<PracticeParticipant> wonParticipants = practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);
            wonParticipants.stream()
                    .map(PracticeParticipant::getPlayerId)
                    .filter(id -> !id.equals(joinedPlayerId))
                    .forEach(recipientSet::add);
        }

        for (Long playerId : recipientSet) {
            try {
                sendToPlayer(playerId, LineNotificationType.SAME_DAY_CANCEL, message);
            } catch (Exception e) {
                log.error("Failed to send consolidated join notification to player {}: {}", playerId, e.getMessage());
            }
        }

        // 管理者にも送信
        List<Player> adminRecipients = getAdminRecipientsForSession(session);
        for (Player admin : adminRecipients) {
            try {
                sendToPlayer(admin.getId(), LineNotificationType.ADMIN_SAME_DAY_CANCEL, message);
            } catch (Exception e) {
                log.error("Failed to send admin consolidated join notification to player {}: {}", admin.getId(), e.getMessage());
            }
        }

        log.info("Sent consolidated join notification for session {} matches {} player {}",
                session.getId(), matchList, joinedPlayerId);
    }

    /**
     * 当日補充参加通知を送信する。
     * その試合のWONメンバー（参加した本人除く）に「〇〇さんが参加します」通知。
     */
    public void sendSameDayJoinNotification(PracticeSession session, int matchNumber,
                                             String joinedPlayerName, Long joinedPlayerId) {
        String message = String.format("%sさんが今日の%d試合目に参加します", joinedPlayerName, matchNumber);

        List<PracticeParticipant> wonParticipants = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);

        List<Long> recipientIds = wonParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .filter(id -> !id.equals(joinedPlayerId))
                .toList();

        for (Long playerId : recipientIds) {
            try {
                sendToPlayer(playerId, LineNotificationType.SAME_DAY_CANCEL, message);
            } catch (Exception e) {
                log.error("Failed to send join notification to player {}: {}", playerId, e.getMessage());
            }
        }

        // 管理者（該当団体ADMIN + 全SUPER_ADMIN）にも送信
        List<Player> adminRecipients = getAdminRecipientsForSession(session);
        for (Player admin : adminRecipients) {
            try {
                sendToPlayer(admin.getId(), LineNotificationType.ADMIN_SAME_DAY_CANCEL, message);
            } catch (Exception e) {
                log.error("Failed to send admin join notification to player {}: {}", admin.getId(), e.getMessage());
            }
        }
    }

    /**
     * 枠状況通知を送信する。
     * 参加登録後に、非WON参加者に残り枠数または枠埋まりを通知。
     */
    public void sendSameDayVacancyUpdateNotification(PracticeSession session, int matchNumber,
                                                      String joinedPlayerName, Long joinedPlayerId) {
        String sessionLabel = getSessionLabel(session);

        List<PracticeParticipant> currentWon = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);
        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        int vacancies = Math.max(0, capacity - currentWon.size());

        // 送信先: 団体全メンバー（該当試合のWON参加者を除く、参加登録した本人も除く）
        List<PlayerOrganization> orgMembers = playerOrganizationRepository
                .findByOrganizationId(session.getOrganizationId());
        Set<Long> wonPlayerIds = currentWon.stream()
                .map(PracticeParticipant::getPlayerId).collect(Collectors.toSet());

        List<Long> recipientIds = orgMembers.stream()
                .map(PlayerOrganization::getPlayerId)
                .distinct()
                .filter(id -> !id.equals(joinedPlayerId))
                .filter(id -> !wonPlayerIds.contains(id))
                .toList();

        if (recipientIds.isEmpty()) return;

        Map<String, Object> flex;
        String altText;

        if (vacancies > 0) {
            altText = String.format("%d試合目に%sさんが参加登録しました。空きは残り%d名分です",
                    matchNumber, joinedPlayerName, vacancies);
            flex = buildSameDayVacancyUpdateFlex(sessionLabel, matchNumber, joinedPlayerName, vacancies, session.getId());
        } else {
            altText = String.format("%d試合目は定員に達しました！", matchNumber);
            flex = buildSameDayVacancyFilledFlex(sessionLabel, matchNumber);
        }

        for (Long playerId : recipientIds) {
            try {
                sendFlexToPlayer(playerId, LineNotificationType.SAME_DAY_VACANCY, altText, flex);
            } catch (Exception e) {
                log.error("Failed to send vacancy update to player {}: {}", playerId, e.getMessage());
            }
        }
    }

    /**
     * 枠埋まり通知のFlex Message（ボタンなし）
     */
    /**
     * 枠状況更新（参加者あり・空きあり）のFlex Messageを構築する
     */
    private Map<String, Object> buildSameDayVacancyUpdateFlex(String sessionLabel, int matchNumber,
                                                               String joinedPlayerName, int vacancies, Long sessionId) {
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "空き枠のお知らせ",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#FF9800",
            "paddingAll", "15px"
        );

        List<Object> bodyContents = List.of(
            Map.of("type", "text", "text", sessionLabel + "の練習",
                "weight", "bold", "size", "lg", "margin", "none", "wrap", true),
            Map.of("type", "text", "text",
                String.format("%d試合目に%sさんが参加登録しました。空きは残り%d名分です。参加希望の場合は参加ボタンを押してください",
                    matchNumber, joinedPlayerName, vacancies),
                "size", "md", "margin", "md", "color", "#333333", "wrap", true)
        );

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        Map<String, Object> joinButton = Map.of(
            "type", "button",
            "action", Map.of(
                "type", "postback",
                "label", "参加する",
                "data", "action=same_day_join&sessionId=" + sessionId + "&matchNumber=" + matchNumber
            ),
            "style", "primary",
            "color", "#FF9800",
            "height", "sm"
        );

        Map<String, Object> footer = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(joinButton),
            "paddingAll", "15px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body,
            "footer", footer
        );
    }

    private Map<String, Object> buildSameDayVacancyFilledFlex(String sessionLabel, int matchNumber) {
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "空き枠のお知らせ",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#9E9E9E",
            "paddingAll", "15px"
        );

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", sessionLabel + "の練習",
                    "weight", "bold", "size", "lg", "margin", "none", "wrap", true),
                Map.of("type", "text", "text",
                    String.format("%d試合目は定員に達しました！", matchNumber),
                    "size", "md", "margin", "md", "color", "#333333", "wrap", true)
            ),
            "paddingAll", "20px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body
        );
    }

    /**
     * プレイヤーにFlex Messageを送信する
     */
    public SendResult sendFlexToPlayer(Long playerId, LineNotificationType notificationType,
                                        String altText, Map<String, Object> flexContents) {
        return sendFlexToPlayer(playerId, notificationType, altText, flexContents, null);
    }

    public SendResult sendFlexToPlayer(Long playerId, LineNotificationType notificationType,
                                        String altText, Map<String, Object> flexContents,
                                        String dedupeKey) {
        ResolvedChannel resolved = resolveChannel(playerId, notificationType, altText);
        if (resolved == null) {
            return SendResult.SKIPPED;
        }

        boolean success = lineMessagingService.sendPushFlexMessage(
            resolved.channel().getChannelAccessToken(), resolved.assignment().getLineUserId(), altText, flexContents);

        return handleSendResult(success, resolved.channel(), playerId, notificationType, altText, dedupeKey);
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
        // 管理者専用通知（ADMIN_プレフィクス）は organizationId=0 のレコードのみで判定
        if (type.name().startsWith("ADMIN_")) {
            return lineNotificationPreferenceRepository
                .findByPlayerIdAndOrganizationId(playerId, 0L)
                .map(pref -> isLineTypeEnabled(pref, type))
                .orElse(true); // レコードなし＝デフォルトON
        }

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
            case ADMIN_WAITLIST_UPDATE -> pref.getAdminWaitlistUpdate();
            case WAITLIST_POSITION_UPDATE -> pref.getWaitlistOffer();
            case SAME_DAY_CONFIRMATION -> pref.getSameDayConfirmation();
            case SAME_DAY_CANCEL -> pref.getSameDayCancel();
            case ADMIN_SAME_DAY_CANCEL -> pref.getAdminSameDayCancel();
            case SAME_DAY_VACANCY -> pref.getSameDayVacancy();
            case ADMIN_SAME_DAY_CONFIRMATION -> pref.getAdminSameDayConfirmation();
            case MENTOR_COMMENT -> pref.getMentorComment();
        };
    }

    private void logMessage(Long channelId, Long playerId, LineNotificationType type,
                           String message, MessageStatus status, String error) {
        logMessage(channelId, playerId, type, message, status, error, null);
    }

    private void logMessage(Long channelId, Long playerId, LineNotificationType type,
                           String message, MessageStatus status, String error, String dedupeKey) {
        try {
            lineMessageLogService.save(channelId, playerId, type, message, status, error, dedupeKey);
        } catch (Exception e) {
            log.error("Failed to save LINE message log: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 統合オファー通知
    // ========================================================================

    /**
     * 同一セッション×同一プレイヤーの複数オファーを1通の統合Flexメッセージとして送信する。
     * トリガープレイヤーIDから内部でプレイヤー名を解決するオーバーロード。
     */
    public void sendConsolidatedWaitlistOfferNotification(List<PracticeParticipant> offeredParticipants,
                                                           PracticeSession session,
                                                           String triggerAction,
                                                           Long triggerPlayerId) {
        Player triggerPlayer = triggerPlayerId != null
                ? playerRepository.findById(triggerPlayerId).orElse(null)
                : null;
        sendConsolidatedWaitlistOfferNotification(offeredParticipants, session, triggerAction, triggerPlayer);
    }

    /**
     * 同一セッション×同一プレイヤーの複数オファーを1通の統合Flexメッセージとして送信する。
     *
     * @param offeredParticipants 同一セッション×同一プレイヤーのOFFERED参加者リスト
     * @param session             対象セッション
     * @param triggerAction       トリガーアクション（"キャンセル"等）
     * @param triggerPlayer       トリガーを起こしたプレイヤー
     */
    public void sendConsolidatedWaitlistOfferNotification(List<PracticeParticipant> offeredParticipants,
                                                           PracticeSession session,
                                                           String triggerAction,
                                                           Player triggerPlayer) {
        if (offeredParticipants == null || offeredParticipants.isEmpty()) return;

        Long playerId = offeredParticipants.get(0).getPlayerId();
        String sessionLabel = getSessionLabelWithCapacity(session);
        String eventText = getEventText(triggerAction);
        String triggerName = triggerPlayer != null ? triggerPlayer.getName() : "不明";

        // 応答期限は最も遅い期限に統一
        LocalDateTime latestDeadline = offeredParticipants.stream()
                .map(PracticeParticipant::getOfferDeadline)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        String deadlineStr = latestDeadline != null
                ? latestDeadline.format(DateTimeFormatter.ofPattern("M/d HH:mm"))
                : "不明";
        boolean isUrgent = latestDeadline != null
                && java.time.Duration.between(JstDateTimeUtil.now(), latestDeadline).toHours() < 12;

        List<Integer> matchNumbers = offeredParticipants.stream()
                .map(PracticeParticipant::getMatchNumber)
                .sorted()
                .collect(Collectors.toList());

        String matchStr = matchNumbers.stream()
                .map(n -> n + "試合目")
                .collect(Collectors.joining("と"));
        String altText = String.format("%s %sに空きが出ました。参加しますか？（期限: %s）",
                sessionLabel, matchStr, deadlineStr);

        Map<String, Object> flex = buildConsolidatedOfferFlex(
                sessionLabel, triggerName, eventText, deadlineStr, isUrgent,
                offeredParticipants, session.getId(), playerId);

        sendFlexToPlayer(playerId, LineNotificationType.WAITLIST_OFFER, altText, flex);
    }

    /**
     * 部分参加後に残りのOFFEREDオファーを通知する。
     * ヘッダーのみ（ボディのセッション情報は省略）。
     *
     * @param remainingOffered 残りのOFFERED参加者リスト（同一セッション×同一プレイヤー）
     */
    public void sendRemainingOfferNotification(List<PracticeParticipant> remainingOffered) {
        if (remainingOffered == null || remainingOffered.isEmpty()) return;

        Long playerId = remainingOffered.get(0).getPlayerId();
        Long sessionId = remainingOffered.get(0).getSessionId();

        List<Integer> matchNumbers = remainingOffered.stream()
                .map(PracticeParticipant::getMatchNumber)
                .sorted()
                .collect(Collectors.toList());

        String matchStr = matchNumbers.stream()
                .map(n -> n + "試合目")
                .collect(Collectors.joining("と"));
        String altText = String.format("残りの試合のオファー: %s", matchStr);

        Map<String, Object> flex = buildRemainingOfferFlex(remainingOffered, sessionId, playerId);

        sendFlexToPlayer(playerId, LineNotificationType.WAITLIST_OFFER, altText, flex);
    }

    /**
     * セッションラベルに定員情報を含めて生成する（例: "4月5日（中央公民館：定員20名）"）
     */
    private String getSessionLabelWithCapacity(PracticeSession session) {
        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String venueName = null;
        if (session.getVenueId() != null) {
            Venue venue = venueRepository.findById(session.getVenueId()).orElse(null);
            if (venue != null) {
                venueName = venue.getName();
            }
        }
        Integer capacity = session.getCapacity();
        if (venueName != null && capacity != null && capacity > 0) {
            return dateStr + "（" + venueName + "：定員" + capacity + "名）";
        } else if (venueName != null) {
            return dateStr + "（" + venueName + "）";
        } else if (capacity != null && capacity > 0) {
            return dateStr + "（定員" + capacity + "名）";
        }
        return dateStr;
    }

    /**
     * 統合オファーFlex Message（パターン1/2）を構築する。
     */
    private Map<String, Object> buildConsolidatedOfferFlex(String sessionLabel, String triggerName,
                                                            String eventText, String deadlineStr,
                                                            boolean isUrgent,
                                                            List<PracticeParticipant> offeredParticipants,
                                                            Long sessionId, Long playerId) {
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
        List<Object> bodyContents = new ArrayList<>(List.of(
                Map.of("type", "text", "text", sessionLabel + "の練習",
                        "weight", "bold", "size", "lg", "margin", "none", "wrap", true),
                Map.of("type", "text", "text",
                        (triggerName != null && !triggerName.equals("不明") && !eventText.isEmpty())
                                ? triggerName + "が" + eventText
                                : "空きが出ました",
                        "size", "md", "margin", "md", "color", "#333333"),
                Map.of("type", "separator", "margin", "lg"),
                Map.<String, Object>of("type", "box", "layout", "horizontal", "margin", "lg",
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

        // フッター（ボタン群）
        List<Object> footerContents = buildOfferButtons(offeredParticipants, sessionId, playerId);
        Map<String, Object> footer = Map.of(
                "type", "box",
                "layout", "vertical",
                "contents", footerContents,
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
     * 残りオファーFlex Message（パターン3/4）を構築する。
     * ヘッダー + フッター（ボタン）のみ。ボディは省略。
     */
    private Map<String, Object> buildRemainingOfferFlex(List<PracticeParticipant> remainingOffered,
                                                         Long sessionId, Long playerId) {
        // ヘッダー
        Map<String, Object> header = Map.of(
                "type", "box",
                "layout", "vertical",
                "contents", List.of(
                        Map.of("type", "text", "text", "残りの試合のオファー",
                                "color", "#ffffff", "weight", "bold", "size", "md")
                ),
                "backgroundColor", "#27AE60",
                "paddingAll", "15px"
        );

        // フッター（ボタン群）
        List<Object> footerContents = buildOfferButtons(remainingOffered, sessionId, playerId);
        Map<String, Object> footer = Map.of(
                "type", "box",
                "layout", "vertical",
                "contents", footerContents,
                "spacing", "sm",
                "paddingAll", "15px"
        );

        return Map.of(
                "type", "bubble",
                "header", header,
                "footer", footer
        );
    }

    /**
     * オファー用ボタン群を構築する。
     * - 個別参加ボタン（緑） × N
     * - すべての試合に参加（青）※2試合以上の場合のみ
     * - 辞退する（赤）※常に一括辞退
     */
    private List<Object> buildOfferButtons(List<PracticeParticipant> offeredParticipants,
                                            Long sessionId, Long playerId) {
        List<Object> buttons = new ArrayList<>();

        // 個別参加ボタン（緑）
        List<PracticeParticipant> sorted = offeredParticipants.stream()
                .sorted(Comparator.comparingInt(PracticeParticipant::getMatchNumber))
                .collect(Collectors.toList());
        for (PracticeParticipant p : sorted) {
            buttons.add(Map.of(
                    "type", "button",
                    "action", Map.of(
                            "type", "postback",
                            "label", p.getMatchNumber() + "試合目に参加",
                            "data", "action=waitlist_accept&participantId=" + p.getId()
                    ),
                    "style", "primary",
                    "color", "#27AE60",
                    "height", "sm"
            ));
        }

        // すべての試合に参加（青）※2試合以上の場合のみ
        if (offeredParticipants.size() >= 2) {
            buttons.add(Map.of(
                    "type", "button",
                    "action", Map.of(
                            "type", "postback",
                            "label", "すべての試合に参加",
                            "data", "action=waitlist_accept_all&sessionId=" + sessionId + "&playerId=" + playerId
                    ),
                    "style", "primary",
                    "color", "#2E86C1",
                    "height", "sm"
            ));
        }

        // 辞退する（赤）※常に一括辞退
        buttons.add(Map.of(
                "type", "button",
                "action", Map.of(
                        "type", "postback",
                        "label", "辞退する",
                        "data", "action=waitlist_decline_all&sessionId=" + sessionId + "&playerId=" + playerId
                ),
                "style", "primary",
                "color", "#E74C3C",
                "height", "sm"
        ));

        return buttons;
    }

    /**
     * メンターコメント投稿時の通知（旧・即時通知）。
     * 現在は sendMentorCommentFlexNotification() によるバッチ送信に置き換え済み。
     */
    public void sendMentorCommentNotification(Long authorId, Long menteeId, Long matchId, String commentContent) {
        Player author = playerRepository.findById(authorId).orElse(null);
        if (author == null) return;

        String authorName = author.getName();
        String preview = commentContent.length() > 50 ? commentContent.substring(0, 50) + "..." : commentContent;

        if (authorId.equals(menteeId)) {
            List<MentorRelationship> relationships = mentorRelationshipRepository
                    .findByMenteeIdAndStatus(menteeId, MentorRelationship.Status.ACTIVE);
            for (MentorRelationship rel : relationships) {
                String message = String.format("%sさんが試合メモにコメントしました:\n%s", authorName, preview);
                sendToPlayer(rel.getMentorId(), LineNotificationType.MENTOR_COMMENT, message);
            }
        } else {
            String message = String.format("%sさんがフィードバックコメントを投稿しました:\n%s", authorName, preview);
            sendToPlayer(menteeId, LineNotificationType.MENTOR_COMMENT, message);
        }
    }

    /**
     * メンターコメントをまとめてFlex Messageで送信する。
     * isMenteeAuthor=true: メンティーがコメント → 全ACTIVEメンターに送信
     * isMenteeAuthor=false: メンターがコメント → メンティーに送信
     */
    public SendResult sendMentorCommentFlexNotification(Long authorId, Long menteeId,
                                                         Match match, List<MatchComment> comments,
                                                         boolean isMenteeAuthor) {
        Player author = playerRepository.findById(authorId).orElse(null);
        if (author == null) return SendResult.SKIPPED;

        String authorName = author.getName();
        String altText = String.format("%sさんがフィードバックコメントを%d件投稿しました", authorName, comments.size());

        // 対戦相手名を解決
        String opponentName = resolveOpponentName(match, menteeId);

        // Flex Message構築
        Map<String, Object> flex = buildMentorCommentFlex(authorName, match, opponentName, comments);

        if (isMenteeAuthor) {
            // メンティーがコメント → 全ACTIVEメンターに通知
            List<MentorRelationship> relationships = mentorRelationshipRepository
                    .findByMenteeIdAndStatus(menteeId, MentorRelationship.Status.ACTIVE);
            if (relationships.isEmpty()) return SendResult.SKIPPED;

            boolean anySuccess = false;
            boolean anyFailed = false;
            boolean anySkipped = false;
            for (MentorRelationship rel : relationships) {
                SendResult r = sendFlexToPlayer(rel.getMentorId(), LineNotificationType.MENTOR_COMMENT, altText, flex);
                if (r == SendResult.SUCCESS) {
                    anySuccess = true;
                } else if (r == SendResult.FAILED) {
                    anyFailed = true;
                    log.warn("メンターコメント通知 部分失敗: mentorId={}", rel.getMentorId());
                } else {
                    anySkipped = true;
                }
            }

            // FAILED優先: 1件でも失敗があれば FAILED（再送可能にするため lineNotified=true にしない）
            // SUCCESS+SKIPPED混在もSKIPPED扱い（スキップ受信者への再送を可能にする）
            // 全員成功のときのみ SUCCESS
            if (anyFailed) return SendResult.FAILED;
            if (!anySuccess) return SendResult.SKIPPED;
            if (anySkipped) return SendResult.SKIPPED;
            return SendResult.SUCCESS;
        } else {
            // メンターがコメント → メンティーに通知
            return sendFlexToPlayer(menteeId, LineNotificationType.MENTOR_COMMENT, altText, flex);
        }
    }

    private String resolveOpponentName(Match match, Long menteeId) {
        Long opponentId;
        if (menteeId.equals(match.getPlayer1Id())) {
            opponentId = match.getPlayer2Id();
        } else {
            opponentId = match.getPlayer1Id();
        }

        // opponentName が設定されている場合はそちらを優先
        if (match.getOpponentName() != null && !match.getOpponentName().isEmpty()) {
            return match.getOpponentName();
        }

        return playerRepository.findById(opponentId)
                .map(Player::getName)
                .orElse("不明");
    }

    private Map<String, Object> buildMentorCommentFlex(String authorName, Match match,
                                                        String opponentName, List<MatchComment> comments) {
        // ヘッダー
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "フィードバックコメント",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#4a6b5a",
            "paddingAll", "15px"
        );

        // ボディ
        String matchDateStr = String.format("%d/%d", match.getMatchDate().getMonthValue(), match.getMatchDate().getDayOfMonth());
        String matchInfo = String.format("%s 第%d試合 vs %s", matchDateStr, match.getMatchNumber(), opponentName);

        List<Object> bodyContents = new java.util.ArrayList<>(List.of(
            Map.of("type", "text", "text", authorName + "さんからのフィードバック",
                "weight", "bold", "size", "md", "wrap", true),
            Map.of("type", "text", "text", matchInfo,
                "size", "sm", "color", "#888888", "margin", "sm"),
            Map.of("type", "separator", "margin", "lg")
        ));

        for (MatchComment comment : comments) {
            bodyContents.add(Map.of("type", "text", "text", comment.getContent(),
                "size", "sm", "color", "#333333", "margin", "md", "wrap", true));
            bodyContents.add(Map.of("type", "separator", "margin", "md"));
        }

        // 最後の separator を除去
        if (!bodyContents.isEmpty()) {
            bodyContents.remove(bodyContents.size() - 1);
        }

        Map<String, Object> body = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", bodyContents,
            "paddingAll", "20px"
        );

        return Map.of(
            "type", "bubble",
            "header", header,
            "body", body
        );
    }

    public enum SendResult {
        SUCCESS, FAILED, SKIPPED
    }
}
