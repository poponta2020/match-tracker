package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.config.LineConfig;
import com.karuta.matchtracker.entity.LineAssignmentStatus;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineChannelStatus;
import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 未使用LINEチャネルの自動回収スケジューラ
 *
 * 毎日AM 3:00に実行。
 * - 90日以上未ログインのユーザーに警告通知
 * - 警告から7日経過後、割り当てを解除
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LineChannelReclaimScheduler {

    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineChannelRepository lineChannelRepository;
    private final NotificationRepository notificationRepository;
    private final LineConfig lineConfig;

    /**
     * 毎日AM 3:00に実行
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void reclaimInactiveChannels() {
        log.info("LINE channel reclaim scheduler started");

        try {
            warnInactiveUsers();
            reclaimExpiredAssignments();
        } catch (Exception e) {
            log.error("Error during channel reclaim: {}", e.getMessage(), e);
        }

        log.info("LINE channel reclaim scheduler completed");
    }

    /**
     * 未ログインユーザーに警告通知を送信
     */
    private void warnInactiveUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(lineConfig.getReclaimInactiveDays());

        List<LineChannelAssignment> candidates = lineChannelAssignmentRepository
                .findReclaimCandidates(threshold);

        for (LineChannelAssignment assignment : candidates) {
            // アプリ内通知で警告
            Notification warning = Notification.builder()
                    .playerId(assignment.getPlayerId())
                    .type(Notification.NotificationType.OFFER_EXPIRING) // 既存の通知種別を流用
                    .title("LINE通知の割り当て解除予告")
                    .message(String.format("LINE通知の割り当てが%d日後に解除されます。継続利用する場合はアプリにログインしてください。",
                            lineConfig.getReclaimGraceDays()))
                    .build();
            notificationRepository.save(warning);

            assignment.setReclaimWarnedAt(LocalDateTime.now());
            lineChannelAssignmentRepository.save(assignment);

            log.info("Sent reclaim warning to player {}", assignment.getPlayerId());
        }

        if (!candidates.isEmpty()) {
            log.info("Sent {} reclaim warnings", candidates.size());
        }
    }

    /**
     * 猶予期間経過後の割り当てを解除
     */
    private void reclaimExpiredAssignments() {
        LocalDateTime graceDeadline = LocalDateTime.now().minusDays(lineConfig.getReclaimGraceDays());

        List<LineChannelAssignment> expired = lineChannelAssignmentRepository
                .findReclaimExpired(graceDeadline);

        for (LineChannelAssignment assignment : expired) {
            assignment.setStatus(LineAssignmentStatus.RECLAIMED);
            assignment.setLineUserId(null);
            assignment.setUnlinkedAt(LocalDateTime.now());
            lineChannelAssignmentRepository.save(assignment);

            // チャネルをAVAILABLEに戻す
            lineChannelRepository.findById(assignment.getLineChannelId()).ifPresent(channel -> {
                channel.setStatus(LineChannelStatus.AVAILABLE);
                lineChannelRepository.save(channel);
            });

            log.info("Reclaimed LINE channel {} from player {}", assignment.getLineChannelId(), assignment.getPlayerId());
        }

        if (!expired.isEmpty()) {
            log.info("Reclaimed {} LINE channel assignments", expired.size());
        }
    }
}
