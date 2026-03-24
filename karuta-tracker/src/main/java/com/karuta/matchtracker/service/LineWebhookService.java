package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * LINE Webhook処理サービス
 *
 * follow / unfollow / postbackイベントを処理する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineWebhookService {

    private final LineChannelService lineChannelService;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineNotificationService lineNotificationService;
    private final WaitlistPromotionService waitlistPromotionService;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日");

    private static final List<LineAssignmentStatus> ACTIVE_STATUSES =
            List.of(LineAssignmentStatus.PENDING, LineAssignmentStatus.LINKED);

    /**
     * Webhook署名を検証する
     */
    public boolean verifySignature(Long channelDbId, String body, String signature) {
        try {
            String channelSecret = lineChannelService.getDecryptedChannelSecret(channelDbId);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String expected = Base64.getEncoder().encodeToString(hash);
            return expected.equals(signature);
        } catch (Exception e) {
            log.error("Signature verification failed for channel {}: {}", channelDbId, e.getMessage());
            return false;
        }
    }

    /**
     * followイベントを処理する
     */
    @Transactional
    public void handleFollow(Long channelDbId, String lineUserId) {
        Optional<LineChannelAssignment> assignment = lineChannelAssignmentRepository
                .findByLineChannelIdAndStatusIn(channelDbId, ACTIVE_STATUSES);

        if (assignment.isEmpty()) {
            log.warn("Received follow event for channel {} but no active assignment found", channelDbId);
            return;
        }

        LineChannelAssignment a = assignment.get();
        a.setLineUserId(lineUserId);
        a.setStatus(LineAssignmentStatus.LINKED);
        a.setLinkedAt(LocalDateTime.now());
        lineChannelAssignmentRepository.save(a);

        log.info("Follow event: channel {} linked to player {} (lineUserId: {})",
                channelDbId, a.getPlayerId(), lineUserId);
    }

    /**
     * unfollowイベントを処理する
     */
    @Transactional
    public void handleUnfollow(Long channelDbId, String lineUserId) {
        Optional<LineChannelAssignment> assignment = lineChannelAssignmentRepository
                .findByLineChannelIdAndStatus(channelDbId, LineAssignmentStatus.LINKED);

        if (assignment.isEmpty()) {
            log.warn("Received unfollow event for channel {} but no linked assignment found", channelDbId);
            return;
        }

        LineChannelAssignment a = assignment.get();
        a.setStatus(LineAssignmentStatus.UNLINKED);
        a.setUnlinkedAt(LocalDateTime.now());
        lineChannelAssignmentRepository.save(a);

        log.info("Unfollow event: player {} blocked/unfriended channel {}", a.getPlayerId(), channelDbId);
    }

    /**
     * postbackイベントを処理する
     */
    @Transactional
    public void handlePostback(Long channelDbId, String lineUserId, String postbackData) {
        Map<String, String> params = parsePostbackData(postbackData);
        String action = params.get("action");
        String participantIdStr = params.get("participantId");

        if (action == null || participantIdStr == null) {
            log.warn("Invalid postback data: {}", postbackData);
            return;
        }

        Long participantId;
        try {
            participantId = Long.parseLong(participantIdStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid participantId in postback data: {}", participantIdStr);
            return;
        }

        Optional<PracticeParticipant> participantOpt = practiceParticipantRepository.findById(participantId);
        if (participantOpt.isEmpty()) {
            lineNotificationService.sendPostbackResponse(channelDbId, lineUserId,
                    "該当するデータが見つかりませんでした。");
            return;
        }

        PracticeParticipant participant = participantOpt.get();

        // ステータスがOFFERED以外なら既に処理済み
        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            lineNotificationService.sendPostbackResponse(channelDbId, lineUserId,
                    "この繰り上げ参加は既に処理済みです。最新の状況はアプリでご確認ください。");
            return;
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId()).orElse(null);
        String dateStr = session != null ? session.getSessionDate().format(DATE_FORMAT) : "";

        try {
            boolean accept = "accept".equals(action);
            waitlistPromotionService.respondToOffer(participantId, accept);

            String message;
            if (accept) {
                message = String.format("参加登録が完了しました！%sの練習 試合%dでお待ちしています。",
                        dateStr, participant.getMatchNumber());
            } else {
                message = "不参加で登録しました。";
            }

            lineNotificationService.sendPostbackResponse(channelDbId, lineUserId, message);

            log.info("Postback processed: player {} {} offer for session {} match {} via LINE",
                    participant.getPlayerId(), accept ? "accepted" : "declined",
                    participant.getSessionId(), participant.getMatchNumber());
        } catch (IllegalStateException e) {
            lineNotificationService.sendPostbackResponse(channelDbId, lineUserId,
                    "この繰り上げ参加は既に処理済みです。最新の状況はアプリでご確認ください。");
        }
    }

    /**
     * postbackデータをパースする（"key=value&key2=value2"形式）
     */
    private Map<String, String> parsePostbackData(String data) {
        Map<String, String> params = new HashMap<>();
        if (data == null || data.isEmpty()) return params;
        for (String pair : data.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }
}
