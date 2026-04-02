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
    private final LineMessageLogRepository lineMessageLogRepository;
    private final LineMessagingService lineMessagingService;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final PlayerRepository playerRepository;
    private final LotteryQueryService lotteryQueryService;
    private final VenueRepository venueRepository;

    public LineNotificationService(
            LineChannelRepository lineChannelRepository,
            LineChannelAssignmentRepository lineChannelAssignmentRepository,
            LineNotificationPreferenceRepository lineNotificationPreferenceRepository,
            LineMessageLogRepository lineMessageLogRepository,
            LineMessagingService lineMessagingService,
            PracticeSessionRepository practiceSessionRepository,
            PracticeParticipantRepository practiceParticipantRepository,
            PlayerOrganizationRepository playerOrganizationRepository,
            PlayerRepository playerRepository,
            LotteryQueryService lotteryQueryService,
            VenueRepository venueRepository) {
        this.lineChannelRepository = lineChannelRepository;
        this.lineChannelAssignmentRepository = lineChannelAssignmentRepository;
        this.lineNotificationPreferenceRepository = lineNotificationPreferenceRepository;
        this.lineMessageLogRepository = lineMessageLogRepository;
        this.lineMessagingService = lineMessagingService;
        this.practiceSessionRepository = practiceSessionRepository;
        this.practiceParticipantRepository = practiceParticipantRepository;
        this.playerOrganizationRepository = playerOrganizationRepository;
        this.playerRepository = playerRepository;
        this.lotteryQueryService = lotteryQueryService;
        this.venueRepository = venueRepository;
    }

    private static final int MONTHLY_MESSAGE_LIMIT = 200;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

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
            sessionLabel, participant.getMatchNumber(), deadlineStr, participant.getId(), isUrgent);

        sendFlexToPlayer(participant.getPlayerId(), LineNotificationType.WAITLIST_OFFER, altText, flexContents);
    }

    /**
     * キャンセル待ち列に残っているユーザーに順番繰り上がり通知を送信する（管理者と同じFlexメッセージ）
     *
     * @param triggerAction     発生イベント（例: "キャンセル", "オファー辞退"）
     * @param triggerPlayer     イベントを起こしたプレイヤー
     * @param session           対象セッション
     * @param matchNumber       対象試合番号
     * @param offeredPlayer     繰り上げオファーを送った相手（null=繰り上げ対象なし）
     * @param remainingWaitlist 残りのキャンセル待ちリスト（WAITLISTED状態、番号順）
     */
    public void sendWaitlistPositionUpdateNotifications(String triggerAction, Player triggerPlayer,
                                                         PracticeSession session, int matchNumber,
                                                         Player offeredPlayer,
                                                         List<PracticeParticipant> remainingWaitlist) {
        if (remainingWaitlist.isEmpty()) return;

        String sessionLabel = getSessionLabel(session);
        String altText = String.format("【キャンセル待ち状況】%s %d試合目: %sが%s", sessionLabel, matchNumber, triggerPlayer.getName(), triggerAction);

        Map<Long, String> playerNames = new HashMap<>();
        for (PracticeParticipant wp : remainingWaitlist) {
            playerNames.computeIfAbsent(wp.getPlayerId(),
                id -> playerRepository.findById(id).map(Player::getName).orElse("不明"));
        }

        Map<String, Object> flex = buildAdminWaitlistFlex(
            sessionLabel, matchNumber, triggerAction, triggerPlayer.getName(),
            offeredPlayer != null ? offeredPlayer.getName() : null,
            remainingWaitlist, playerNames);

        for (PracticeParticipant wp : remainingWaitlist) {
            sendFlexToPlayer(wp.getPlayerId(), LineNotificationType.ADMIN_WAITLIST_UPDATE, altText, flex);
        }

        log.info("Waitlist position update notifications sent for session {} match {}: {} players",
            session.getId(), matchNumber, remainingWaitlist.size());
    }

    /**
     * キャンセル待ち繰り上げ用Flex Message（Bubble）を構築する
     */
    private Map<String, Object> buildWaitlistOfferFlex(String sessionLabel, int matchNumber,
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

        String sessionLabel = getSessionLabel(session);
        String message = accepted
            ? String.format("%s %d試合目の繰り上げ参加を承諾しました。", sessionLabel, participant.getMatchNumber())
            : String.format("%s %d試合目の繰り上げ参加を辞退しました。", sessionLabel, participant.getMatchNumber());

        sendToPlayer(participant.getPlayerId(), LineNotificationType.WAITLIST_OFFER, message);
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

                // 繰り上げオファー中の練習をFlexメッセージ（参加する/辞退するボタン付き）で通知
                for (PracticeParticipant offeredParticipant : offered) {
                    sendWaitlistOfferNotification(offeredParticipant);
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
     * 管理者向けキャンセル待ち状況通知を送信する
     *
     * SUPER_ADMINのLINE連携済みユーザーに、キャンセル/繰り上げの状況を通知する。
     *
     * @param triggerAction  発生イベント（例: "キャンセル", "オファー期限切れ", "オファー辞退", "降格"）
     * @param triggerPlayer  イベントを起こしたプレイヤー
     * @param session        対象セッション
     * @param matchNumber    対象試合番号
     * @param offeredPlayer  繰り上げオファーを送った相手（null=繰り上げ対象なし）
     * @param remainingWaitlist 残りのキャンセル待ちリスト（WAITLISTED状態、番号順）
     */
    public void sendAdminWaitlistNotification(String triggerAction, Player triggerPlayer,
                                               PracticeSession session, int matchNumber,
                                               Player offeredPlayer,
                                               List<PracticeParticipant> remainingWaitlist) {
        List<Player> superAdmins = playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN);
        if (superAdmins.isEmpty()) return;

        String sessionLabel = getSessionLabel(session);
        String altText = String.format("【管理者通知】%s %d試合目: %sが%s", sessionLabel, matchNumber, triggerPlayer.getName(), triggerAction);

        // 残りの待ち列の名前解決
        Map<Long, String> playerNames = new HashMap<>();
        for (PracticeParticipant wp : remainingWaitlist) {
            playerNames.computeIfAbsent(wp.getPlayerId(),
                id -> playerRepository.findById(id).map(Player::getName).orElse("不明"));
        }

        Map<String, Object> flex = buildAdminWaitlistFlex(
            sessionLabel, matchNumber, triggerAction, triggerPlayer.getName(),
            offeredPlayer != null ? offeredPlayer.getName() : null,
            remainingWaitlist, playerNames);

        for (Player admin : superAdmins) {
            sendFlexToPlayer(admin.getId(), LineNotificationType.ADMIN_WAITLIST_UPDATE, altText, flex);
        }

        log.info("Admin waitlist notification sent for session {} match {}: {} by {}",
            session.getId(), matchNumber, triggerAction, triggerPlayer.getName());
    }

    /**
     * 管理者向けキャンセル待ち状況Flex Message（Bubble）を構築する
     */
    private Map<String, Object> buildAdminWaitlistFlex(String sessionLabel, int matchNumber,
                                                        String triggerAction, String triggerPlayerName,
                                                        String offeredPlayerName,
                                                        List<PracticeParticipant> remainingWaitlist,
                                                        Map<Long, String> playerNames) {
        // ヘッダー
        Map<String, Object> header = Map.of(
            "type", "box",
            "layout", "vertical",
            "contents", List.of(
                Map.of("type", "text", "text", "キャンセル待ち状況通知",
                    "color", "#ffffff", "weight", "bold", "size", "md")
            ),
            "backgroundColor", "#8E44AD",
            "paddingAll", "15px"
        );

        // ボディ
        List<Object> bodyContents = new ArrayList<>();

        // セッション・試合情報
        bodyContents.add(Map.of("type", "text", "text", sessionLabel + " " + matchNumber + "試合目",
            "weight", "bold", "size", "lg", "margin", "none", "wrap", true));

        bodyContents.add(Map.of("type", "separator", "margin", "lg"));

        // ① 誰がどの試合をキャンセルしたか
        bodyContents.add(Map.of("type", "text", "text", "発生イベント",
            "size", "xs", "color", "#888888", "margin", "lg"));
        bodyContents.add(Map.of("type", "text", "text", triggerPlayerName + " が " + triggerAction,
            "size", "md", "color", "#333333", "margin", "sm", "wrap", true));

        bodyContents.add(Map.of("type", "separator", "margin", "lg"));

        // ② 誰にキャンセル待ち連絡を送ったか
        bodyContents.add(Map.of("type", "text", "text", "繰り上げオファー",
            "size", "xs", "color", "#888888", "margin", "lg"));
        if (offeredPlayerName != null) {
            bodyContents.add(Map.of("type", "text", "text", offeredPlayerName + " に送信済み",
                "size", "md", "color", "#27AE60", "weight", "bold", "margin", "sm", "wrap", true));
        } else {
            bodyContents.add(Map.of("type", "text", "text", "繰り上げ対象なし（待ち列が空）",
                "size", "md", "color", "#E74C3C", "margin", "sm", "wrap", true));
        }

        bodyContents.add(Map.of("type", "separator", "margin", "lg"));

        // ③ 今のキャンセル待ち列の状態
        bodyContents.add(Map.of("type", "text", "text", "残りキャンセル待ち列",
            "size", "xs", "color", "#888888", "margin", "lg"));
        if (remainingWaitlist.isEmpty()) {
            bodyContents.add(Map.of("type", "text", "text", "なし",
                "size", "sm", "color", "#999999", "margin", "sm"));
        } else {
            for (PracticeParticipant wp : remainingWaitlist) {
                String name = playerNames.getOrDefault(wp.getPlayerId(), "不明");
                bodyContents.add(Map.of("type", "text",
                    "text", String.format("%d番: %s", wp.getWaitlistNumber(), name),
                    "size", "sm", "color", "#333333", "margin", "sm"));
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

        // SUPER_ADMINにも送信（WON参加者として既に送信済みの場合は除く）
        List<Player> superAdmins = playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN);
        int adminSentCount = 0;
        for (Player admin : superAdmins) {
            if (playerIds.contains(admin.getId())) continue; // WON参加者は送信済み
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

            // 2人ずつ1行に並べる
            for (int i = 0; i < sorted.size(); i += 2) {
                Player p1 = playerMap.get(sorted.get(i).getPlayerId());
                String name1 = p1 != null ? p1.getName() : "不明";

                List<Object> rowContents = new java.util.ArrayList<>();
                rowContents.add(Map.of("type", "text", "text", name1,
                        "flex", 1, "size", "sm", "color", "#555555"));

                if (i + 1 < sorted.size()) {
                    Player p2 = playerMap.get(sorted.get(i + 1).getPlayerId());
                    String name2 = p2 != null ? p2.getName() : "不明";
                    rowContents.add(Map.of("type", "text", "text", name2,
                            "flex", 1, "size", "sm", "color", "#555555"));
                } else {
                    rowContents.add(Map.of("type", "filler"));
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

        log.info("Sent same-day cancel notification to {} players for session {} match {}",
                recipientIds.size(), session.getId(), matchNumber);
    }

    /**
     * 空き募集通知を送信する。
     * 当該セッションの非WON参加者（キャンセル本人除く）にFlex Message。
     */
    public void sendSameDayVacancyNotification(PracticeSession session, int matchNumber, Long cancelledPlayerId) {
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

        // 送信先: 当該セッションの非WON参加者（キャンセル本人除く）
        // CANCELLEDの人も対象（別試合の空き募集は受け取れる）
        List<PracticeParticipant> allParticipants = practiceParticipantRepository
                .findBySessionId(session.getId());
        Set<Long> wonPlayerIds = currentWon.stream()
                .map(PracticeParticipant::getPlayerId).collect(Collectors.toSet());
        // 全試合のWON参加者を取得して除外する必要はない — 同じ練習日の別試合でWONの人も参加可能
        // ただし、この試合でWONの人は除外
        List<Long> recipientIds = allParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .filter(id -> !id.equals(cancelledPlayerId))
                .filter(id -> !wonPlayerIds.contains(id))
                .toList();

        if (recipientIds.isEmpty()) return;

        String altText = String.format("%s %d試合目が%d名分空いています", sessionLabel, matchNumber, vacancies);
        Map<String, Object> flex = buildSameDayVacancyFlex(sessionLabel, matchNumber, vacancies, session.getId());

        for (Long playerId : recipientIds) {
            try {
                sendFlexToPlayer(playerId, LineNotificationType.SAME_DAY_VACANCY, altText, flex);
            } catch (Exception e) {
                log.error("Failed to send vacancy notification to player {}: {}", playerId, e.getMessage());
            }
        }

        log.info("Sent vacancy notification to {} players for session {} match {} ({} vacancies)",
                recipientIds.size(), session.getId(), matchNumber, vacancies);
    }

    /**
     * 空き募集Flex Messageを構築する
     */
    private Map<String, Object> buildSameDayVacancyFlex(String sessionLabel, int matchNumber, int vacancies, Long sessionId) {
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
                String.format("%d試合目が%d名分空いています。参加希望の場合は参加ボタンを押してください", matchNumber, vacancies),
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

        // 送信先: セッションの非WON参加者（参加登録した本人除く）
        List<PracticeParticipant> allParticipants = practiceParticipantRepository
                .findBySessionId(session.getId());
        Set<Long> wonPlayerIds = currentWon.stream()
                .map(PracticeParticipant::getPlayerId).collect(Collectors.toSet());

        List<Long> recipientIds = allParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
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
        // 管理者専用通知は organizationId=0 のレコードのみで判定
        if (type == LineNotificationType.ADMIN_SAME_DAY_CONFIRMATION) {
            return isAdminSameDayConfirmationEnabled(playerId);
        }

        List<LineNotificationPreference> prefs = lineNotificationPreferenceRepository.findByPlayerId(playerId);
        if (prefs.isEmpty()) return true; // デフォルト全ON

        // いずれかの団体で該当種別がONならtrue
        return prefs.stream().anyMatch(pref -> isLineTypeEnabled(pref, type));
    }

    /**
     * SUPER_ADMIN向け参加者確定通知の受信可否を確認する。
     * organizationId=0 のレコードを管理者専用の設定として使用する。
     */
    private boolean isAdminSameDayConfirmationEnabled(Long playerId) {
        return lineNotificationPreferenceRepository
                .findByPlayerIdAndOrganizationId(playerId, 0L)
                .map(LineNotificationPreference::getAdminSameDayConfirmation)
                .orElse(true); // レコードなし＝デフォルトON
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
            case SAME_DAY_CONFIRMATION -> pref.getSameDayConfirmation();
            case SAME_DAY_CANCEL -> pref.getSameDayCancel();
            case SAME_DAY_VACANCY -> pref.getSameDayVacancy();
            case ADMIN_SAME_DAY_CONFIRMATION -> pref.getAdminSameDayConfirmation();
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
