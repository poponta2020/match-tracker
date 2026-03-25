package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.LineNotificationPreferenceDto;
import com.karuta.matchtracker.dto.LineSendResultResponse;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineMessageLog.MessageStatus;
import com.karuta.matchtracker.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

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

    private static final int MONTHLY_MESSAGE_LIMIT = 200;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    /**
     * キャンセル待ち繰り上げ通知を送信する
     */
    public void sendWaitlistOfferNotification(PracticeParticipant participant) {
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        if (session == null) return;

        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String deadlineStr = participant.getOfferDeadline() != null
            ? participant.getOfferDeadline().format(DateTimeFormatter.ofPattern("M/d HH:mm"))
            : "不明";
        String message = String.format("%sの練習 試合%dに空きが出ました。参加しますか？（期限: %s）",
            dateStr, participant.getMatchNumber(), deadlineStr);

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
     * 抽選結果をLINE一括送信する（管理者手動送信）
     */
    @Transactional
    public LineSendResultResponse sendLotteryResults(int year, int month) {
        int sent = 0, failed = 0, skipped = 0;

        List<PracticeParticipant> participants = practiceParticipantRepository
            .findBySessionDateYearAndMonth(year, month);

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

            SendResult result = sendToPlayer(p.getPlayerId(), LineNotificationType.LOTTERY_RESULT, message);
            switch (result) {
                case SUCCESS -> sent++;
                case FAILED -> failed++;
                case SKIPPED -> skipped++;
            }
        }

        log.info("Lottery result LINE notifications: sent={}, failed={}, skipped={}", sent, failed, skipped);
        return LineSendResultResponse.builder()
            .sentCount(sent).failedCount(failed).skippedCount(skipped).build();
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
            .sentCount(sent).failedCount(failed).skippedCount(skipped).build();
    }

    /**
     * 通知設定を取得する
     */
    @Transactional(readOnly = true)
    public LineNotificationPreferenceDto getPreferences(Long playerId) {
        return lineNotificationPreferenceRepository.findByPlayerId(playerId)
            .map(LineNotificationPreferenceDto::fromEntity)
            .orElse(LineNotificationPreferenceDto.builder()
                .playerId(playerId)
                .lotteryResult(true).waitlistOffer(true).offerExpired(true)
                .matchPairing(true).practiceReminder(true).deadlineReminder(true)
                .build());
    }

    /**
     * 通知設定を更新する
     */
    @Transactional
    public void updatePreferences(LineNotificationPreferenceDto dto) {
        LineNotificationPreference pref = lineNotificationPreferenceRepository
            .findByPlayerId(dto.getPlayerId())
            .orElse(LineNotificationPreference.builder().playerId(dto.getPlayerId()).build());

        pref.setLotteryResult(dto.isLotteryResult());
        pref.setWaitlistOffer(dto.isWaitlistOffer());
        pref.setOfferExpired(dto.isOfferExpired());
        pref.setMatchPairing(dto.isMatchPairing());
        pref.setPracticeReminder(dto.isPracticeReminder());
        pref.setDeadlineReminder(dto.isDeadlineReminder());

        lineNotificationPreferenceRepository.save(pref);
    }

    /**
     * プレイヤーにLINE通知を送信する（共通処理）
     */
    public SendResult sendToPlayer(Long playerId, LineNotificationType notificationType, String message) {
        // LINKED状態の割り当てを取得
        Optional<LineChannelAssignment> assignmentOpt =
            lineChannelAssignmentRepository.findByPlayerIdAndStatus(playerId, AssignmentStatus.LINKED);
        if (assignmentOpt.isEmpty()) {
            return SendResult.SKIPPED;
        }

        LineChannelAssignment assignment = assignmentOpt.get();

        // 通知設定チェック
        if (!isNotificationEnabled(playerId, notificationType)) {
            logMessage(assignment.getLineChannelId(), playerId, notificationType, message,
                MessageStatus.SKIPPED, "通知設定がOFF");
            return SendResult.SKIPPED;
        }

        // チャネル取得
        LineChannel channel = lineChannelRepository.findById(assignment.getLineChannelId()).orElse(null);
        if (channel == null || channel.getStatus() != LineChannel.ChannelStatus.LINKED) {
            return SendResult.SKIPPED;
        }

        // 月間送信上限チェック
        if (channel.getMonthlyMessageCount() >= MONTHLY_MESSAGE_LIMIT) {
            logMessage(channel.getId(), playerId, notificationType, message,
                MessageStatus.SKIPPED, "月間送信上限超過");
            return SendResult.SKIPPED;
        }

        // LINE Push API送信
        boolean success = lineMessagingService.sendPushMessage(
            channel.getChannelAccessToken(), assignment.getLineUserId(), message);

        if (success) {
            channel.setMonthlyMessageCount(channel.getMonthlyMessageCount() + 1);
            lineChannelRepository.save(channel);
            logMessage(channel.getId(), playerId, notificationType, message, MessageStatus.SUCCESS, null);
            return SendResult.SUCCESS;
        } else {
            logMessage(channel.getId(), playerId, notificationType, message,
                MessageStatus.FAILED, "LINE API送信失敗");
            return SendResult.FAILED;
        }
    }

    private boolean isNotificationEnabled(Long playerId, LineNotificationType type) {
        Optional<LineNotificationPreference> prefOpt = lineNotificationPreferenceRepository.findByPlayerId(playerId);
        if (prefOpt.isEmpty()) return true; // デフォルト全ON

        LineNotificationPreference pref = prefOpt.get();
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
