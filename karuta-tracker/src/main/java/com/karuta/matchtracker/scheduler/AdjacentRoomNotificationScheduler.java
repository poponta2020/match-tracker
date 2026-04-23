package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.config.AdjacentRoomConfig;
import com.karuta.matchtracker.dto.AdjacentRoomStatusDto;
import com.karuta.matchtracker.entity.AdjacentRoomNotification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.AdjacentRoomNotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.service.AdjacentRoomService;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 隣室空き通知スケジューラー
 *
 * 30分間隔で実行し、隣室チェック対象の会場（かでる2・7の和室、東区民センター 東🌸）を
 * 利用する未来のセッションについて、定員接近時（残り4人以下）に隣室の空き状況を
 * 管理者に段階的に通知する。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdjacentRoomNotificationScheduler {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final AdjacentRoomNotificationRepository adjacentRoomNotificationRepository;
    private final AdjacentRoomService adjacentRoomService;
    private final NotificationService notificationService;
    private final PlayerRepository playerRepository;
    private final TransactionTemplate transactionTemplate;

    private static final int THRESHOLD = 4;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d");

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Tokyo")
    public void checkCapacityAndNotify() {
        LocalDate today = JstDateTimeUtil.today();
        // 翌日〜40日先の未来のセッションを対象（当日分は開始済みの可能性があるため除外）
        LocalDate startDate = today.plusDays(1);
        LocalDate endDate = today.plusDays(40);
        List<PracticeSession> sessions = practiceSessionRepository.findByDateRange(startDate, endDate);

        // 隣室チェック対象のセッションのみフィルタ（かでる和室 + 東🌸）
        List<PracticeSession> targetSessions = sessions.stream()
                .filter(s -> AdjacentRoomConfig.isAdjacentCheckTarget(s.getVenueId()))
                .toList();

        if (targetSessions.isEmpty()) {
            log.debug("No adjacent check target sessions found");
            return;
        }

        log.info("Adjacent room check started for {} target session(s)", targetSessions.size());

        int notifiedCount = 0;
        for (PracticeSession session : targetSessions) {
            try {
                Integer result = transactionTemplate.execute(status -> processSession(session));
                notifiedCount += (result != null ? result : 0);
            } catch (Exception e) {
                log.error("Failed to process adjacent room check for session {}: {}",
                        session.getId(), e.getMessage(), e);
            }
        }

        log.info("Adjacent room check completed: {} notification(s) sent", notifiedCount);
    }

    private int processSession(PracticeSession session) {
        Integer capacity = session.getCapacity();
        if (capacity == null || capacity <= 0) return 0;

        int totalMatches = session.getTotalMatches() != null ? session.getTotalMatches() : 1;

        // 全試合のうち最も定員に近い試合の参加者数を計算
        int maxParticipants = 0;
        int closestMatchNumber = 1;
        for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
            long activeCount = practiceParticipantRepository
                    .countBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON)
                    + practiceParticipantRepository
                    .countBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.PENDING);
            if (activeCount > maxParticipants) {
                maxParticipants = (int) activeCount;
                closestMatchNumber = matchNumber;
            }
        }

        int remaining = capacity - maxParticipants;

        // 残り4人以下でない場合はスキップ
        if (remaining > THRESHOLD) return 0;
        // 残りが負の場合は0に補正
        if (remaining < 0) remaining = 0;

        // 隣室の空き状況を取得（DB障害時は "不明"(available=false) が返りリトライ可能な状態を維持）
        AdjacentRoomStatusDto adjacentRoom = adjacentRoomService
                .getAdjacentRoomAvailability(session.getVenueId(), session.getSessionDate());
        if (adjacentRoom == null || !adjacentRoom.getAvailable()) {
            return 0;
        }

        // 通知済みレコードを原子的に確保（一意制約で並列実行時の重複を防止）
        // ※ 隣室確認後に保存することで、DB障害時に通知未送信なのに通知済みになる問題を防ぐ
        try {
            adjacentRoomNotificationRepository.save(AdjacentRoomNotification.builder()
                    .sessionId(session.getId())
                    .remainingCount(remaining)
                    .build());
            adjacentRoomNotificationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // 既に他のインスタンスが通知済み → スキップ
            log.debug("Adjacent room notification already sent for session {} (remaining={})",
                    session.getId(), remaining);
            return 0;
        }

        // 通知メッセージを作成
        String dateStr = session.getSessionDate().format(DATE_FORMAT);
        String venueName = AdjacentRoomConfig.getSiteRoomName(session.getVenueId());
        String adjacentRoomName = adjacentRoom.getAdjacentRoomName();
        String timeLabel = AdjacentRoomConfig.getNightTimeLabel(session.getVenueId());

        String title;
        String message;
        if (remaining == 0) {
            title = "定員到達 — 隣室空きあり";
            message = String.format("%s %sの試合%dが定員に達しました。隣室（%s）は夜間(%s)空きです。",
                    dateStr, venueName, closestMatchNumber, adjacentRoomName, timeLabel);
        } else {
            title = String.format("定員まで残り%d人 — 隣室空きあり", remaining);
            message = String.format("%s %sの試合%dが定員まで残り%d人です。隣室（%s）は夜間(%s)空きです。",
                    dateStr, venueName, closestMatchNumber, remaining, adjacentRoomName, timeLabel);
        }

        // 通知対象: SUPER_ADMIN全員 + 該当セッションの団体のADMIN
        List<Player> recipients = new ArrayList<>();
        recipients.addAll(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN));
        if (session.getOrganizationId() != null) {
            recipients.addAll(playerRepository.findByRoleAndAdminOrganizationIdAndActive(
                    Player.Role.ADMIN, session.getOrganizationId()));
        }

        // 重複排除してアプリ内通知 + Web Push送信
        int sentCount = 0;
        for (Player recipient : recipients.stream().distinct().toList()) {
            notificationService.createAndPush(
                    recipient.getId(),
                    NotificationType.ADJACENT_ROOM_AVAILABLE,
                    title, message,
                    "PRACTICE_SESSION", session.getId(),
                    "/practice",
                    session.getOrganizationId());
            sentCount++;
        }

        log.info("Adjacent room notification sent for session {} (remaining={}, recipients={})",
                session.getId(), remaining, sentCount);

        return sentCount;
    }
}
