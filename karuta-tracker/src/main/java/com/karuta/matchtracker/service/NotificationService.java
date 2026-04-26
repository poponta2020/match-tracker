package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.NotificationDto;
import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.PushNotificationPreference;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PushNotificationPreferenceRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * アプリ内通知サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PushNotificationService pushNotificationService;
    private final PushNotificationPreferenceRepository pushNotificationPreferenceRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    /**
     * 指定プレイヤーの通知一覧を取得
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long playerId) {
        return notificationRepository.findByPlayerIdAndDeletedAtIsNullOrderByCreatedAtDesc(playerId)
                .stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 未読通知数を取得
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long playerId) {
        return notificationRepository.countByPlayerIdAndIsReadFalseAndDeletedAtIsNull(playerId);
    }

    /**
     * 通知を既読にする（所有者チェック付き）
     */
    @Transactional
    public void markAsRead(Long notificationId, Long currentUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        if (currentUserId != null && !notification.getPlayerId().equals(currentUserId)) {
            throw new com.karuta.matchtracker.exception.ForbiddenException("他のプレイヤーの通知は既読にできません");
        }
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    /**
     * 指定プレイヤーの全通知を一括論理削除する
     */
    @Transactional
    public int deleteAllByPlayerId(Long playerId) {
        int count = notificationRepository.softDeleteAllByPlayerId(playerId, JstDateTimeUtil.now());
        log.info("Soft-deleted {} notifications for player {}", count, playerId);
        return count;
    }

    /**
     * 抽選結果通知をまとめて作成する（プレイヤーごとにグルーピング）
     *
     * - 全当選 → LOTTERY_ALL_WON 1レコード
     * - 一部落選 → セッション別 LOTTERY_WAITLISTED + LOTTERY_REMAINING_WON 1レコード
     * - 全落選 → セッション別 LOTTERY_WAITLISTED のみ
     */
    @Transactional
    public int createLotteryResultNotifications(List<PracticeParticipant> participants) {
        List<Notification> notifications = new ArrayList<>();

        // セッション情報をキャッシュ
        Map<Long, PracticeSession> sessionCache = new HashMap<>();
        for (PracticeParticipant p : participants) {
            sessionCache.computeIfAbsent(p.getSessionId(),
                    id -> practiceSessionRepository.findById(id).orElse(null));
        }

        // プレイヤーごとにグルーピング
        Map<Long, List<PracticeParticipant>> byPlayer = participants.stream()
                .collect(Collectors.groupingBy(PracticeParticipant::getPlayerId));

        for (Map.Entry<Long, List<PracticeParticipant>> entry : byPlayer.entrySet()) {
            Long playerId = entry.getKey();
            List<PracticeParticipant> playerParticipants = entry.getValue();

            List<PracticeParticipant> won = playerParticipants.stream()
                    .filter(p -> p.getStatus() == ParticipantStatus.WON)
                    .collect(Collectors.toList());
            List<PracticeParticipant> waitlisted = playerParticipants.stream()
                    .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED)
                    .collect(Collectors.toList());

            if (waitlisted.isEmpty() && !won.isEmpty()) {
                // 全当選（referenceId は当選セッションのうち1つ。送信済み判定で対象月のセッション群と突合できるようにする）
                notifications.add(Notification.builder()
                        .playerId(playerId)
                        .type(NotificationType.LOTTERY_ALL_WON)
                        .title("抽選結果: 全当選")
                        .message("申し込んだ練習はすべて当選しました")
                        .referenceType("PRACTICE_SESSION")
                        .referenceId(won.get(0).getSessionId())
                        .build());
            } else if (!waitlisted.isEmpty()) {
                // セッション別にキャンセル待ち通知を作成
                Map<Long, List<PracticeParticipant>> waitlistedBySession = waitlisted.stream()
                        .collect(Collectors.groupingBy(PracticeParticipant::getSessionId));

                for (Map.Entry<Long, List<PracticeParticipant>> sessionEntry : waitlistedBySession.entrySet()) {
                    PracticeSession session = sessionCache.get(sessionEntry.getKey());
                    if (session == null) continue;

                    String dateStr = session.getSessionDate().format(DATE_FORMAT);
                    List<PracticeParticipant> sessionWaitlisted = sessionEntry.getValue();

                    StringBuilder message = new StringBuilder(dateStr);
                    for (PracticeParticipant p : sessionWaitlisted) {
                        message.append(String.format("\n試合%d: キャンセル待ち%d番",
                                p.getMatchNumber(), p.getWaitlistNumber()));
                    }

                    // referenceId にはセッションIDを設定（セッション単位の辞退操作と連携）
                    notifications.add(Notification.builder()
                            .playerId(playerId)
                            .type(NotificationType.LOTTERY_WAITLISTED)
                            .title("抽選結果: キャンセル待ち")
                            .message(message.toString())
                            .referenceType("PRACTICE_SESSION")
                            .referenceId(sessionEntry.getKey())
                            .build());
                }

                // 当選分がある場合のみ、まとめ通知を追加（referenceId は当選セッションのうち1つ）
                if (!won.isEmpty()) {
                    notifications.add(Notification.builder()
                            .playerId(playerId)
                            .type(NotificationType.LOTTERY_REMAINING_WON)
                            .title("抽選結果: その他は当選")
                            .message("上記以外の申し込みはすべて当選しています")
                            .referenceType("PRACTICE_SESSION")
                            .referenceId(won.get(0).getSessionId())
                            .build());
                }
            }
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
            log.info("Created {} consolidated lottery notifications for {} players",
                    notifications.size(), byPlayer.size());

            // Web Push送信（プレイヤーごとにまとめて1通、セッションのorganizationIdを使用）
            for (Map.Entry<Long, List<PracticeParticipant>> entry2 : byPlayer.entrySet()) {
                Long pid = entry2.getKey();
                // セッションのorganizationIdを取得
                Long orgId = entry2.getValue().stream()
                        .map(p -> sessionCache.get(p.getSessionId()))
                        .filter(java.util.Objects::nonNull)
                        .map(PracticeSession::getOrganizationId)
                        .findFirst().orElse(null);
                // 該当プレイヤーの最初の通知のタイトル・メッセージで送信
                notifications.stream()
                        .filter(n -> n.getPlayerId().equals(pid))
                        .findFirst()
                        .ifPresent(n -> sendPushIfEnabled(pid, n.getType(), n.getTitle(), n.getMessage(), "/practice", orgId));
            }
        }

        return notifications.size();
    }

    /**
     * 繰り上げ通知を作成する
     */
    @Transactional
    public void createOfferNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);

        String deadlineStr = participant.getOfferDeadline() != null
                ? participant.getOfferDeadline().format(DateTimeFormatter.ofPattern("M/d HH:mm"))
                : "不明";
        String message = String.format("%sの練習 試合%dに空きが出ました。参加しますか？（期限: %s）",
                dateStr, participant.getMatchNumber(), deadlineStr);

        // 応答期限まで12時間未満の場合は注意文言を追加
        if (participant.getOfferDeadline() != null
                && java.time.Duration.between(JstDateTimeUtil.now(), participant.getOfferDeadline()).toHours() < 12) {
            message += "\n※ 応答期限まで残りわずかです。お早めにご回答ください。";
        }

        createAndPush(
                participant.getPlayerId(),
                NotificationType.WAITLIST_OFFER,
                "繰り上げ参加のご連絡",
                message,
                "PRACTICE_PARTICIPANT",
                participant.getId(),
                "/notifications",
                session.getOrganizationId());

        log.info("Created offer notification for player {} (session {} match {})",
                participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());
    }

    /**
     * 繰り上げ期限切れ警告通知を作成する
     */
    @Transactional
    public void createOfferExpiringNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String deadlineStr = participant.getOfferDeadline() != null
                ? participant.getOfferDeadline().format(DateTimeFormatter.ofPattern("M/d HH:mm"))
                : "不明";

        createAndPush(
                participant.getPlayerId(),
                NotificationType.OFFER_EXPIRING,
                "繰り上げ参加の期限が迫っています",
                String.format("%sの練習 試合%dの繰り上げ参加の応答期限が%sです。お早めにご回答ください。",
                        dateStr, participant.getMatchNumber(), deadlineStr),
                "PRACTICE_PARTICIPANT",
                participant.getId(),
                "/notifications",
                session.getOrganizationId());

        log.info("Created offer expiring notification for player {} (session {} match {}, deadline: {})",
                participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber(), deadlineStr);
    }

    /**
     * 期限切れ通知を作成する
     */
    @Transactional
    public void createOfferExpiredNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);

        createAndPush(
                participant.getPlayerId(),
                NotificationType.OFFER_EXPIRED,
                "繰り上げ参加の期限切れ",
                String.format("%sの練習 試合%dの繰り上げ参加の期限が切れました",
                        dateStr, participant.getMatchNumber()),
                "PRACTICE_PARTICIPANT",
                participant.getId(),
                "/notifications",
                session.getOrganizationId());
    }

    /**
     * アプリ内通知を作成し、Web Push設定がONなら送信も行う共通メソッド
     */
    @Transactional
    public Notification createAndPush(Long playerId, NotificationType type,
                                       String title, String message,
                                       String referenceType, Long referenceId, String pushUrl) {
        return createAndPush(playerId, type, title, message, referenceType, referenceId, pushUrl, null);
    }

    /**
     * アプリ内通知を作成し、Web Push設定がONなら送信も行う共通メソッド（団体指定）
     */
    @Transactional
    public Notification createAndPush(Long playerId, NotificationType type,
                                       String title, String message,
                                       String referenceType, Long referenceId, String pushUrl,
                                       Long organizationId) {
        Notification notification = Notification.builder()
                .playerId(playerId)
                .type(type)
                .title(title)
                .message(message)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();

        notificationRepository.save(notification);

        sendPushIfEnabled(playerId, type, title, message, pushUrl, organizationId);

        return notification;
    }

    /**
     * Web Push設定をチェックし、該当種別がONなら送信する（団体指定）
     */
    public void sendPushIfEnabled(Long playerId, NotificationType type, String title, String message, String url, Long organizationId) {
        // 団体指定がある場合はその団体の設定を参照
        PushNotificationPreference pref;
        if (organizationId != null) {
            pref = pushNotificationPreferenceRepository
                    .findByPlayerIdAndOrganizationId(playerId, organizationId).orElse(null);
        } else {
            // 団体指定なしの場合はいずれかの団体でenabledかつ種別ONなら送信
            List<PushNotificationPreference> prefs = pushNotificationPreferenceRepository.findByPlayerId(playerId);
            pref = prefs.stream()
                    .filter(p -> p.getEnabled() && isTypeEnabled(p, type))
                    .findFirst().orElse(null);
            if (pref != null) {
                pushNotificationService.sendPush(playerId, title, message, url);
                return;
            }
            return;
        }

        if (pref == null || !pref.getEnabled()) {
            return;
        }

        if (!isTypeEnabled(pref, type)) {
            return;
        }

        pushNotificationService.sendPush(playerId, title, message, url);
    }

    /**
     * 後方互換用: organizationId なしの sendPushIfEnabled
     */
    public void sendPushIfEnabled(Long playerId, NotificationType type, String title, String message, String url) {
        sendPushIfEnabled(playerId, type, title, message, url, null);
    }

    /**
     * NotificationType に対応する設定カラムがONかどうかを判定する
     */
    private boolean isTypeEnabled(PushNotificationPreference pref, NotificationType type) {
        return switch (type) {
            case LOTTERY_ALL_WON, LOTTERY_REMAINING_WON, LOTTERY_WAITLISTED -> pref.getLotteryResult();
            case WAITLIST_OFFER -> pref.getWaitlistOffer();
            case OFFER_EXPIRING -> pref.getOfferExpiring();
            case OFFER_EXPIRED -> pref.getOfferExpired();
            case CHANNEL_RECLAIM_WARNING -> pref.getChannelReclaimWarning();
            case DENSUKE_UNMATCHED_NAMES -> pref.getDensukeUnmatched();
            case ADJACENT_ROOM_AVAILABLE -> pref.getAdjacentRoom();
            default -> false;
        };
    }
}
