package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.service.CardDivisionTextService;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 札分けリマインダースケジューラ。
 *
 * <p>数分ごとに実行し、当日(JST)セッションの<b>1試合目開始3時間前</b>ウィンドウに入ったものを抽出して、
 * その団体の購読者へ札分けテキストを LINE 送信する（{@link LineNotificationService#sendCardDivisionReminder}）。
 * 二重送信は dedupeKey=sessionId で1回に収束するため、3時間幅ウィンドウ内で複数回ポーリングしても
 * プレイヤーごと1通に収まる（＝実質「開始3時間前の最初のポーリング」で送信）。
 *
 * <p>1試合目開始時刻は {@code venue_match_schedules}（match_number=1）を第一情報源とし、無ければ
 * {@link PracticeSession#getStartTime()}、<b>いずれも無ければ送信しない</b>（AC-7）。
 *
 * <p>雛形: {@code OfferExpiryScheduler}（5分ポーリング）。早朝発火は Render の UptimeRobot キープアライブに
 * 依存する（本番ログで 6:00 JST 帯まで無停止発火を確認済み・要件定義書 §6 参照）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardDivisionReminderScheduler {

    private final PracticeSessionRepository practiceSessionRepository;
    private final VenueMatchScheduleRepository venueMatchScheduleRepository;
    private final CardDivisionTextService cardDivisionTextService;
    private final LineNotificationService lineNotificationService;

    /** 何時間前に送信するか（1試合目開始の3時間前）。 */
    private static final int REMINDER_HOURS_BEFORE = 3;

    @Scheduled(fixedDelay = 300000, initialDelay = 90000) // 5分ごと、起動90秒後に初回実行
    public void checkCardDivisionReminders() {
        processReminders(JstDateTimeUtil.today(), JstDateTimeUtil.now());
    }

    /**
     * 当日セッションのうち「1試合目開始の3時間前ウィンドウ {@code [開始-3h, 開始)}」に入るものへ送信する。
     * テスト容易性のため {@code today}/{@code now} を引数で受ける（JST 実時刻の注入は {@link #checkCardDivisionReminders}）。
     */
    void processReminders(LocalDate today, LocalDateTime now) {
        List<PracticeSession> sessions = practiceSessionRepository.findAllBySessionDate(today);
        if (sessions.isEmpty()) {
            return;
        }

        for (PracticeSession session : sessions) {
            try {
                LocalTime startTime = resolveFirstMatchStartTime(session);
                if (startTime == null) {
                    continue; // 開始時刻が特定できない → 送信対象外（AC-7）
                }

                LocalDateTime start = LocalDateTime.of(today, startTime);
                LocalDateTime windowOpen = start.minusHours(REMINDER_HOURS_BEFORE);
                // now ∈ [開始-3h, 開始)
                if (now.isBefore(windowOpen) || !now.isBefore(start)) {
                    continue;
                }

                String text = cardDivisionTextService.buildTextForSession(session);
                lineNotificationService.sendCardDivisionReminder(
                        session.getId(), session.getOrganizationId(), text);
            } catch (Exception e) {
                log.error("Failed card division reminder for session {}: {}",
                        session.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 1試合目の開始時刻を解決する。{@code venue_match_schedules} の match_number=1 を第一情報源とし、
     * 無ければ {@link PracticeSession#getStartTime()}、いずれも無ければ {@code null}（送信スキップ）。
     */
    LocalTime resolveFirstMatchStartTime(PracticeSession session) {
        Long venueId = session.getVenueId();
        if (venueId != null) {
            for (VenueMatchSchedule vms : venueMatchScheduleRepository.findByVenueIdOrderByMatchNumberAsc(venueId)) {
                if (vms.getMatchNumber() != null && vms.getMatchNumber() == 1 && vms.getStartTime() != null) {
                    return vms.getStartTime();
                }
            }
        }
        return session.getStartTime();
    }
}
