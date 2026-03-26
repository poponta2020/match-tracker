package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LineChannel;
import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineChannelAssignment.AssignmentStatus;
import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineChannelRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 未使用LINEチャネルの自動回収スケジューラ
 *
 * 毎日AM3:00に実行し、長期未ログインユーザーのチャネルを回収する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineChannelReclaimScheduler {

    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineChannelRepository lineChannelRepository;
    private final NotificationRepository notificationRepository;

    private static final int INACTIVE_DAYS = 90;
    private static final int GRACE_PERIOD_DAYS = 7;

    @Scheduled(cron = "0 0 3 * * *") // 毎日AM3:00
    @Transactional
    public void reclaimUnusedChannels() {
        log.info("LINE channel reclaim scheduler started");

        LocalDateTime threshold = JstDateTimeUtil.now().minusDays(INACTIVE_DAYS);
        LocalDateTime graceDeadline = JstDateTimeUtil.now().minusDays(GRACE_PERIOD_DAYS);

        // 1. 猶予期間経過 → 回収
        List<LineChannelAssignment> expired = lineChannelAssignmentRepository
            .findReclaimExpired(threshold, graceDeadline);
        for (LineChannelAssignment assignment : expired) {
            reclaimAssignment(assignment);
        }
        if (!expired.isEmpty()) {
            log.info("Reclaimed {} LINE channel assignments", expired.size());
        }

        // 2. 新規回収候補 → 警告通知
        List<LineChannelAssignment> candidates = lineChannelAssignmentRepository
            .findReclaimCandidates(threshold);
        for (LineChannelAssignment assignment : candidates) {
            warnPlayer(assignment);
        }
        if (!candidates.isEmpty()) {
            log.info("Sent reclaim warnings to {} players", candidates.size());
        }

        log.info("LINE channel reclaim scheduler completed");
    }

    private void reclaimAssignment(LineChannelAssignment assignment) {
        assignment.setStatus(AssignmentStatus.RECLAIMED);
        assignment.setUnlinkedAt(JstDateTimeUtil.now());
        assignment.setLineUserId(null);
        lineChannelAssignmentRepository.save(assignment);

        lineChannelRepository.findById(assignment.getLineChannelId()).ifPresent(channel -> {
            channel.setStatus(LineChannel.ChannelStatus.AVAILABLE);
            lineChannelRepository.save(channel);
        });

        log.info("Reclaimed channel {} from player {}", assignment.getLineChannelId(), assignment.getPlayerId());
    }

    private void warnPlayer(LineChannelAssignment assignment) {
        assignment.setReclaimWarnedAt(JstDateTimeUtil.now());
        lineChannelAssignmentRepository.save(assignment);

        // アプリ内通知で警告
        notificationRepository.save(Notification.builder()
            .playerId(assignment.getPlayerId())
            .type(Notification.NotificationType.OFFER_EXPIRING) // 既存の通知タイプを再利用
            .title("LINE通知の割り当て解除予告")
            .message("LINE通知の割り当てが7日後に解除されます。継続利用する場合はアプリにログインしてください。")
            .build());
    }
}
