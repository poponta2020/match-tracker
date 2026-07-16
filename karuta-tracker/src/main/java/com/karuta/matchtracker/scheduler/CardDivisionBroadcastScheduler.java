package com.karuta.matchtracker.scheduler;

import com.karuta.matchtracker.entity.LineBroadcastGroup;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.LineBroadcastGroupRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.service.CardDivisionBroadcastService;
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
 * 札分けの全体LINE一斉配信スケジューラ。
 *
 * <p>数分ごとに実行し、有効な配信グループ×当日(JST)・当該団体セッションを、
 * <b>1試合目開始の30分前ウィンドウ</b>で1回だけ全体グループへ配信する（{@link CardDivisionBroadcastService}）。
 * 二重送信は (配信グループ, セッション) 単位の原子的送信権確保で1回に収束する。
 *
 * <p>1試合目開始時刻は {@code venue_match_schedules}（match_number=1）を第一情報源とし、無ければ
 * {@link PracticeSession#getStartTime()}、<b>いずれも無ければ朝8:00</b>にフォールバックして配信する
 * （個人通知の「送信しない」とは異なり、全体配信は必ず流す・AC-7）。
 *
 * <p>ウィンドウを過ぎた場合は<b>遅延配信せず未配信のまま残す</b>（古い/場違いな時刻の全体配信を避ける）。
 * 個人通知の {@code CardDivisionReminderScheduler}（3時間前・開始時刻なしはスキップ）とはタイミングが
 * 異なるため別スケジューラに切り出している。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardDivisionBroadcastScheduler {

    private final PracticeSessionRepository practiceSessionRepository;
    private final VenueMatchScheduleRepository venueMatchScheduleRepository;
    private final LineBroadcastGroupRepository lineBroadcastGroupRepository;
    private final CardDivisionBroadcastService cardDivisionBroadcastService;

    /** 1試合目開始の何分前に配信するか。 */
    private static final int MINUTES_BEFORE = 30;

    /** 開始時刻が特定できない場合のフォールバック配信時刻・ウィンドウ。 */
    private static final LocalTime FALLBACK_START = LocalTime.of(8, 0);
    private static final LocalTime FALLBACK_WINDOW_CLOSE = LocalTime.of(12, 0);

    @Scheduled(fixedDelay = 180000, initialDelay = 120000) // 3分ごと、起動120秒後に初回実行
    public void checkBroadcasts() {
        processBroadcasts(JstDateTimeUtil.today(), JstDateTimeUtil.now());
    }

    /**
     * 当日の各配信グループについて、配信ウィンドウに入る当該団体セッションを配信対象にする。
     * テスト容易性のため {@code today}/{@code now} を引数で受ける。
     */
    void processBroadcasts(LocalDate today, LocalDateTime now) {
        List<PracticeSession> todaySessions = practiceSessionRepository.findAllBySessionDate(today);
        if (todaySessions.isEmpty()) {
            return;
        }
        List<LineBroadcastGroup> groups = lineBroadcastGroupRepository.findByEnabledTrue();
        if (groups.isEmpty()) {
            return;
        }

        for (LineBroadcastGroup group : groups) {
            for (PracticeSession session : todaySessions) {
                // 団体分離（AC-8）: 当該グループの団体のセッションのみ
                if (!group.getOrganizationId().equals(session.getOrganizationId())) {
                    continue;
                }
                try {
                    if (isInBroadcastWindow(session, today, now)) {
                        cardDivisionBroadcastService.processGroupBroadcast(group, session, now);
                    }
                } catch (Exception e) {
                    log.error("Failed card division broadcast for group {} session {}: {}",
                            group.getId(), session.getId(), e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 配信ウィンドウ判定（AC-7）。
     * 開始時刻あり → {@code [開始-30分, 開始)}／開始時刻なし → {@code [8:00, 12:00)}。
     */
    boolean isInBroadcastWindow(PracticeSession session, LocalDate today, LocalDateTime now) {
        LocalTime startTime = resolveFirstMatchStartTime(session);
        if (startTime != null) {
            LocalDateTime start = LocalDateTime.of(today, startTime);
            LocalDateTime windowOpen = start.minusMinutes(MINUTES_BEFORE);
            return !now.isBefore(windowOpen) && now.isBefore(start);
        }
        // フォールバック: 8:00 ウィンドウ
        LocalDateTime open = LocalDateTime.of(today, FALLBACK_START);
        LocalDateTime close = LocalDateTime.of(today, FALLBACK_WINDOW_CLOSE);
        return !now.isBefore(open) && now.isBefore(close);
    }

    /**
     * 1試合目の開始時刻を解決する。{@code venue_match_schedules} の match_number=1 を第一情報源とし、
     * 無ければ {@link PracticeSession#getStartTime()}、いずれも無ければ {@code null}（→ 8:00 フォールバック）。
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
