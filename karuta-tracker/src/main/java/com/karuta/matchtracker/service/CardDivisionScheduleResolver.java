package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 札分け配信の「1試合目開始時刻」と「送信予定時刻」を解決する共有コンポーネント。
 *
 * <p>全体push配信の {@code CardDivisionBroadcastScheduler}（フォールバック経路）と、チャット予約送信の
 * {@code LineChatReservationService}（予約キュー生成）が<b>同一ロジック</b>で送信時刻を導出できるよう切り出した。
 * ここを唯一の情報源にすることで、予約の送信予定時刻とフォールバックの配信ウィンドウがドリフトしない。
 *
 * <p>解決規則: {@code venue_match_schedules} の match_number=1 を第一情報源とし、無ければ
 * {@link PracticeSession#getStartTime()}、いずれも無ければ {@code null}（→ 送信予定時刻は 8:00 フォールバック）。
 */
@Component
@RequiredArgsConstructor
public class CardDivisionScheduleResolver {

    private final VenueMatchScheduleRepository venueMatchScheduleRepository;

    /** 1試合目開始の何分前に送る／ウィンドウを開くか。 */
    public static final int MINUTES_BEFORE = 30;

    /** 開始時刻が特定できない場合のフォールバック送信時刻。 */
    public static final LocalTime FALLBACK_START = LocalTime.of(8, 0);

    /**
     * 1試合目の開始時刻を解決する。{@code venue_match_schedules} の match_number=1 を第一情報源とし、
     * 無ければ {@link PracticeSession#getStartTime()}、いずれも無ければ {@code null}。
     */
    public LocalTime resolveFirstMatchStartTime(PracticeSession session) {
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

    /**
     * 送信予定時刻（JST）を解決する。1試合目開始の30分前、開始時刻が両情報源とも無ければ当日8:00。
     * 日付はセッション自身の {@code sessionDate} を用いる（呼び出し元で別途日付を渡さず、バッチ・リコンサイルの
     * 双方で完全に同一の計算になるようにする）。
     */
    public LocalDateTime resolveScheduledSendAt(PracticeSession session) {
        LocalTime start = resolveFirstMatchStartTime(session);
        if (start != null) {
            return LocalDateTime.of(session.getSessionDate(), start).minusMinutes(MINUTES_BEFORE);
        }
        return LocalDateTime.of(session.getSessionDate(), FALLBACK_START);
    }
}
