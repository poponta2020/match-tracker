package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.NotificationDto;
import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    /**
     * 指定プレイヤーの通知一覧を取得
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(Long playerId) {
        return notificationRepository.findByPlayerIdOrderByCreatedAtDesc(playerId)
                .stream()
                .map(NotificationDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 未読通知数を取得
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long playerId) {
        return notificationRepository.countByPlayerIdAndIsReadFalse(playerId);
    }

    /**
     * 通知を既読にする
     */
    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    /**
     * 抽選結果通知を一括作成する
     */
    @Transactional
    public void createLotteryResultNotifications(List<PracticeParticipant> participants) {
        List<Notification> notifications = new ArrayList<>();

        for (PracticeParticipant p : participants) {
            PracticeSession session = practiceSessionRepository.findById(p.getSessionId()).orElse(null);
            if (session == null) continue;

            String dateStr = session.getSessionDate().format(DATE_FORMAT);

            if (p.getStatus() == ParticipantStatus.WON) {
                notifications.add(Notification.builder()
                        .playerId(p.getPlayerId())
                        .type(NotificationType.LOTTERY_WON)
                        .title("抽選結果: 当選")
                        .message(String.format("%sの練習 試合%dに当選しました", dateStr, p.getMatchNumber()))
                        .referenceType("PRACTICE_PARTICIPANT")
                        .referenceId(p.getId())
                        .build());
            } else if (p.getStatus() == ParticipantStatus.WAITLISTED) {
                notifications.add(Notification.builder()
                        .playerId(p.getPlayerId())
                        .type(NotificationType.LOTTERY_WAITLISTED)
                        .title("抽選結果: キャンセル待ち")
                        .message(String.format("%sの練習 試合%d: キャンセル待ち%d番です",
                                dateStr, p.getMatchNumber(), p.getWaitlistNumber()))
                        .referenceType("PRACTICE_PARTICIPANT")
                        .referenceId(p.getId())
                        .build());
            }
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
            log.info("Created {} lottery result notifications", notifications.size());
        }
    }

    /**
     * 繰り上げ通知を作成する
     */
    @Transactional
    public void createOfferNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);

        Notification notification = Notification.builder()
                .playerId(participant.getPlayerId())
                .type(NotificationType.WAITLIST_OFFER)
                .title("繰り上げ参加のご連絡")
                .message(String.format("%sの練習 試合%dに空きが出ました。参加しますか？（期限: %s）",
                        dateStr, participant.getMatchNumber(),
                        participant.getOfferDeadline() != null
                                ? participant.getOfferDeadline().format(DateTimeFormatter.ofPattern("M/d HH:mm"))
                                : "不明"))
                .referenceType("PRACTICE_PARTICIPANT")
                .referenceId(participant.getId())
                .build();

        notificationRepository.save(notification);
        log.info("Created offer notification for player {} (session {} match {})",
                participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());

        // Web Push送信
        pushNotificationService.sendPush(
                participant.getPlayerId(),
                notification.getTitle(),
                notification.getMessage(),
                "/lottery/offer-response?id=" + participant.getId());
    }

    /**
     * 期限切れ通知を作成する
     */
    @Transactional
    public void createOfferExpiredNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);

        Notification notification = Notification.builder()
                .playerId(participant.getPlayerId())
                .type(NotificationType.OFFER_EXPIRED)
                .title("繰り上げ参加の期限切れ")
                .message(String.format("%sの練習 試合%dの繰り上げ参加の期限が切れました",
                        dateStr, participant.getMatchNumber()))
                .referenceType("PRACTICE_PARTICIPANT")
                .referenceId(participant.getId())
                .build();

        notificationRepository.save(notification);
    }
}
