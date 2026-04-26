package com.karuta.matchtracker.service.proxy;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.proxy.venue.VenueCompletionStrategy;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会場非依存の申込完了検知コア。
 *
 * <p>会場サイトからの応答ごとに本コンポーネントが呼ばれ、{@link VenueCompletionStrategy} に
 * 判定を委譲する。陽性なら以下を実行する:</p>
 * <ol>
 *   <li>{@link ProxySession#setCompleted(boolean)} を {@code true} に切替 (SessionStore のクリーンアップ対象に)</li>
 *   <li>{@code practice_sessions.reservation_confirmed_at} を JST 現在時刻で更新 (既存の手動報告と同一カラム)</li>
 * </ol>
 *
 * <p>HTTP ヘッダ {@code X-VRP-Completed} のレスポンスへの注入は本コンポーネントの責務外で、
 * Task 7 の {@code VenueReservationProxyService} が本メソッドの戻り値を見て注入する。</p>
 *
 * <p><strong>冪等性:</strong> {@code reservationConfirmedAt} が既にセット済みのセッションに対する
 * 検知ではカラム更新はスキップする (最初の検知時刻で固定)。検知自体は陽性として {@code true} を返し、
 * {@code completed=true} もセットするため、後続のクリーンアップやヘッダ注入は通常通り動作する。</p>
 */
@Component
@Slf4j
public class VenueReservationCompletionDetector {

    private final Map<VenueId, VenueCompletionStrategy> strategies;
    private final PracticeSessionRepository practiceSessionRepository;

    public VenueReservationCompletionDetector(List<VenueCompletionStrategy> strategyList,
                                              PracticeSessionRepository practiceSessionRepository) {
        Map<VenueId, VenueCompletionStrategy> map = new EnumMap<>(VenueId.class);
        for (VenueCompletionStrategy s : strategyList) {
            VenueCompletionStrategy prev = map.put(s.venue(), s);
            if (prev != null) {
                log.warn("Duplicate VenueCompletionStrategy registered for venue={}, kept={}, replaced={}",
                        s.venue(), s.getClass().getName(), prev.getClass().getName());
            }
        }
        this.strategies = map;
        this.practiceSessionRepository = practiceSessionRepository;
    }

    /**
     * レスポンスを評価し、完了画面に到達していたら {@link ProxySession#setCompleted(boolean)} を
     * {@code true} に切替えつつ {@code practice_sessions.reservation_confirmed_at} を更新する。
     *
     * @param session          対象プロキシセッション
     * @param requestUrl       会場サイトに送ったリクエスト URL (null 可)
     * @param responseLocation 会場サイトの応答 {@code Location} ヘッダ値 (null 可)
     * @param responseBody     会場サイトの応答 HTML 本文 (null 可)
     * @return 完了画面に到達したと判定された場合 {@code true} (DB 更新の成否は問わない)
     */
    public boolean detectAndMarkComplete(ProxySession session,
                                         String requestUrl,
                                         String responseLocation,
                                         String responseBody) {
        if (session == null) {
            return false;
        }
        VenueId venue = session.getVenue();
        if (venue == null) {
            log.warn("ProxySession token={} has no venue; skipping completion detection", session.getToken());
            return false;
        }

        VenueCompletionStrategy strategy = strategies.get(venue);
        if (strategy == null) {
            // Phase 1 では HIGASHI strategy 未登録。Phase 2 で登録される想定。
            log.warn("No VenueCompletionStrategy registered for venue={} (token={})",
                    venue, session.getToken());
            return false;
        }

        boolean completed;
        try {
            completed = strategy.isCompletion(requestUrl, responseLocation, responseBody);
        } catch (RuntimeException e) {
            log.error("VenueCompletionStrategy.isCompletion threw for venue={} token={}: {}",
                    venue, session.getToken(), e.getMessage(), e);
            return false;
        }
        if (!completed) {
            return false;
        }

        log.info("Detected reservation completion: venue={} token={} practiceSessionId={} url={}",
                venue, session.getToken(), session.getPracticeSessionId(), requestUrl);

        session.setCompleted(true);
        markReservationConfirmed(session);
        return true;
    }

    private void markReservationConfirmed(ProxySession session) {
        Long practiceSessionId = session.getPracticeSessionId();
        if (practiceSessionId == null) {
            log.warn("ProxySession token={} has no practiceSessionId; skipping DB update",
                    session.getToken());
            return;
        }

        try {
            Optional<PracticeSession> opt = practiceSessionRepository.findById(practiceSessionId);
            if (opt.isEmpty()) {
                log.warn("practice_sessions id={} not found; skipping reservation_confirmed_at update (token={})",
                        practiceSessionId, session.getToken());
                return;
            }
            PracticeSession entity = opt.get();
            if (entity.getReservationConfirmedAt() != null) {
                // 冪等: 既に手動報告 or 過去の自動検知でセット済み。最初の検知時刻を保持するため上書きしない。
                log.info("practice_sessions id={} already has reservation_confirmed_at={}; skipping overwrite (token={})",
                        practiceSessionId, entity.getReservationConfirmedAt(), session.getToken());
                return;
            }
            entity.setReservationConfirmedAt(JstDateTimeUtil.now());
            practiceSessionRepository.save(entity);
            log.info("Updated practice_sessions.reservation_confirmed_at for id={} (token={})",
                    practiceSessionId, session.getToken());
        } catch (RuntimeException e) {
            // DB 更新失敗時もイベントとしての検知は成立。UI 側の再フェッチで結果整合性が取れる。
            log.error("Failed to update reservation_confirmed_at for practice_sessions id={} (token={}): {}",
                    practiceSessionId, session.getToken(), e.getMessage(), e);
        }
    }
}
