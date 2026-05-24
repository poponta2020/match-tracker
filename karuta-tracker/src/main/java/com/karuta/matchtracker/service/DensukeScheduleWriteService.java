package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * アプリ→伝助 スケジュール push サービス。
 *
 * <p>アプリ側 {@code practice_sessions} の日付集合と伝助ページ上の日程集合の差分を計算し、
 * アプリ側にしかない日程を伝助の {@code POST /update} で末尾追記する。
 * 既存日程・既存参加者データ・既存 {@code densuke_row_ids} のインデックスは保護される。
 *
 * <p>呼び出し経路は 2 系統:
 * <ul>
 *   <li>{@link PracticeSessionService#createSession} の {@code afterCommit} フックから
 *       {@link #pushNewSchedulesToDensukeAsync} で fire-and-forget</li>
 *   <li>{@link DensukeSyncService#syncAll} の 5 分スケジューラから
 *       {@link #pushAllForCurrentAndNextMonth} でフォロー同期（即時 push 失敗の自動回復）</li>
 * </ul>
 *
 * <p>失敗通知:
 * <ul>
 *   <li>{@code createSession} 経路の失敗は管理者へ LINE 通知（{@link LineNotificationService#sendDensukeScheduleSyncFailedNotification}）</li>
 *   <li>スケジューラ経路の失敗は WARN ログのみ（通知フラッディング防止）</li>
 * </ul>
 *
 * <p>並行制御は {@link DensukeUrlRepository#findByYearAndMonthAndOrganizationIdForUpdate}
 * の行ロックでシリアライズする（伝助 read → write の間の他リクエスト割り込みによる
 * 差分計算ズレを防ぐ）。
 */
@Service
@Slf4j
public class DensukeScheduleWriteService {

    private final DensukeUrlRepository densukeUrlRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final VenueRepository venueRepository;
    private final VenueMatchScheduleRepository venueMatchScheduleRepository;
    private final DensukePageCreateService densukePageCreateService;
    private final DensukeWriteService densukeWriteService;
    private final DensukeScraper densukeScraper;
    private final LineNotificationService lineNotificationService;
    /**
     * 自己参照。{@code @Async}/{@code @Transactional} の AOP プロキシを通すために必要。
     * 同一 bean 内のメソッド呼び出しは Spring プロキシを経由しないため、{@code @Transactional}
     * の {@code SELECT FOR UPDATE} や {@code @Async} のスレッド分離が効かない。
     */
    private final DensukeScheduleWriteService self;

    public DensukeScheduleWriteService(
            DensukeUrlRepository densukeUrlRepository,
            PracticeSessionRepository practiceSessionRepository,
            VenueRepository venueRepository,
            VenueMatchScheduleRepository venueMatchScheduleRepository,
            DensukePageCreateService densukePageCreateService,
            DensukeWriteService densukeWriteService,
            DensukeScraper densukeScraper,
            LineNotificationService lineNotificationService,
            @Lazy DensukeScheduleWriteService self) {
        this.densukeUrlRepository = densukeUrlRepository;
        this.practiceSessionRepository = practiceSessionRepository;
        this.venueRepository = venueRepository;
        this.venueMatchScheduleRepository = venueMatchScheduleRepository;
        this.densukePageCreateService = densukePageCreateService;
        this.densukeWriteService = densukeWriteService;
        this.densukeScraper = densukeScraper;
        this.lineNotificationService = lineNotificationService;
        this.self = self;
    }

    // ========================================================================
    // 公開エントリポイント
    // ========================================================================

    /**
     * createSession の afterCommit から非同期 fire-and-forget で呼ばれる。
     * 失敗時は管理者 LINE 通知が発火する（{@code notifyOnFailure=true}）。
     */
    @Async
    public void pushNewSchedulesToDensukeAsync(int year, int month, Long organizationId) {
        try {
            self.pushNewSchedulesToDensuke(year, month, organizationId);
        } catch (Exception e) {
            log.warn("Async push schedule failed for {}/{} (orgId={}): {}",
                    year, month, organizationId, e.getMessage(), e);
        }
    }

    /**
     * 単一 (year, month, organizationId) の差分 push。
     * 失敗時は管理者 LINE 通知が発火する。
     */
    @Transactional
    public void pushNewSchedulesToDensuke(int year, int month, Long organizationId) {
        pushNewSchedulesInternal(year, month, organizationId, true);
    }

    /**
     * 5 分スケジューラから呼ばれるフォロー同期。
     * 当月・翌月の全 {@code densuke_urls} に対して差分 push を順次実行する。
     * 失敗は WARN ログのみで管理者通知は発火しない（フラッディング防止）。
     */
    public void pushAllForCurrentAndNextMonth() {
        LocalDate today = JstDateTimeUtil.today();
        pushAllForMonth(today.getYear(), today.getMonthValue());
        LocalDate next = today.plusMonths(1);
        pushAllForMonth(next.getYear(), next.getMonthValue());
    }

    private void pushAllForMonth(int year, int month) {
        List<DensukeUrl> urls = densukeUrlRepository.findByYearAndMonth(year, month);
        for (DensukeUrl url : urls) {
            try {
                self.pushSilently(year, month, url.getOrganizationId());
            } catch (Exception e) {
                log.warn("Schedule push (scheduler) failed for {}/{} (orgId={}): {}",
                        year, month, url.getOrganizationId(), e.getMessage());
            }
        }
    }

    /**
     * スケジューラ経路用の通知抑制版。public は AOP プロキシ経由（{@code self.}）で呼ぶため必須。
     */
    @Transactional
    public void pushSilently(int year, int month, Long organizationId) {
        pushNewSchedulesInternal(year, month, organizationId, false);
    }

    // ========================================================================
    // コア実装
    // ========================================================================

    private void pushNewSchedulesInternal(int year, int month, Long organizationId, boolean notifyOnFailure) {
        // 1. 行ロック付きで densuke_urls を取得（並行 push の差分計算ズレを防ぐ）
        Optional<DensukeUrl> urlOpt = densukeUrlRepository
                .findByYearAndMonthAndOrganizationIdForUpdate(year, month, organizationId);
        if (urlOpt.isEmpty()) {
            log.debug("No densuke URL for {}/{} (orgId={}), skip push", year, month, organizationId);
            return;
        }
        DensukeUrl densukeUrl = urlOpt.get();

        // 2. アプリ側 sessions
        List<PracticeSession> sessions = practiceSessionRepository
                .findByYearAndMonthAndOrganizationId(year, month, organizationId);
        if (sessions.isEmpty()) {
            log.debug("No practice sessions for {}/{} (orgId={}), skip push", year, month, organizationId);
            return;
        }

        // 3. 伝助の現スケジュールを取得 → 既存日付集合
        Set<LocalDate> existingDensukeDates;
        try {
            existingDensukeDates = densukeScraper.scrape(densukeUrl.getUrl(), year).getEntries().stream()
                    .map(e -> e.getDate())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            String msg = String.format("伝助スケジュール取得失敗 (%d年%d月, url=%s): %s",
                    year, month, densukeUrl.getUrl(), e.getMessage());
            log.warn(msg);
            if (notifyOnFailure) {
                lineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, msg);
            }
            return;
        }

        // 4. 差分セッション計算と「過去日 vs 末尾追記可能日」の分割。
        //    伝助の POST /update は候補日程を末尾追記しかできないため、伝助の既存最大日付より前の日付を
        //    push すると伝助DOM上の出現順 [既存..., 過去日] とアプリ側の日付昇順 [過去日, 既存...] が
        //    ずれる。後続の DensukeWriteService.parseAndSaveRowIds は両者をインデックスで対応付けるため、
        //    新規の過去日に他日付の join-* (row id) が紐づき、参加者出欠が別日に書き込まれるデータ破壊が
        //    発生する（Codex レビュー Round 2 CRITICAL）。よって伝助の既存最大日付以前の新規セッションは
        //    push せず、即時 push 経路のみ管理者へ通知して手動対応に回す。
        LocalDate maxExistingDate = existingDensukeDates.stream().max(LocalDate::compareTo).orElse(null);
        List<PracticeSession> diffSessions = sessions.stream()
                .filter(s -> !existingDensukeDates.contains(s.getSessionDate()))
                .toList();
        List<PracticeSession> newSessions;
        List<PracticeSession> skippedPastSessions;
        if (maxExistingDate == null) {
            // 伝助に既存日程が無いなら row id ずれの問題は発生しない（初回 push 相当）
            newSessions = diffSessions;
            skippedPastSessions = List.of();
        } else {
            newSessions = diffSessions.stream()
                    .filter(s -> s.getSessionDate().isAfter(maxExistingDate))
                    .toList();
            skippedPastSessions = diffSessions.stream()
                    .filter(s -> !s.getSessionDate().isAfter(maxExistingDate))
                    .toList();
        }

        // 過去日がスキップされたら管理者へ通知（即時 push 経路のみ。スケジューラ経路はフラッディング防止で抑制）
        if (!skippedPastSessions.isEmpty() && notifyOnFailure) {
            String pastDates = skippedPastSessions.stream()
                    .map(s -> s.getSessionDate().toString())
                    .collect(Collectors.joining(", "));
            String msg = String.format(
                    "伝助スケジュール push スキップ (%d年%d月): 過去日 [%s] は伝助の既存最大日付 %s より前のため自動追加できません。"
                            + "伝助ページの管理画面から手動で追加してください（row id 整合性保護のため）。",
                    year, month, pastDates, maxExistingDate);
            log.warn(msg);
            lineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, msg);
        }

        if (newSessions.isEmpty()) {
            log.debug("No pushable schedule diff for {}/{} (orgId={}), nothing to push", year, month, organizationId);
            return;
        }

        // 5. push 用 schedule 文字列を組み立て（buildScheduleText を再利用）
        List<Long> venueIds = newSessions.stream()
                .map(PracticeSession::getVenueId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Venue> venueMap = venueIds.isEmpty()
                ? Collections.emptyMap()
                : venueRepository.findAllById(venueIds).stream()
                        .collect(Collectors.toMap(Venue::getId, v -> v));
        Map<Long, Map<Integer, VenueMatchSchedule>> scheduleMap = venueIds.isEmpty()
                ? Collections.emptyMap()
                : venueMatchScheduleRepository.findByVenueIdIn(venueIds).stream()
                        .collect(Collectors.groupingBy(
                                VenueMatchSchedule::getVenueId,
                                Collectors.toMap(VenueMatchSchedule::getMatchNumber, s -> s)));

        String scheduleText;
        try {
            scheduleText = densukePageCreateService
                    .buildScheduleText(newSessions, venueMap, scheduleMap)
                    .scheduleText();
        } catch (IllegalStateException e) {
            String msg = String.format("伝助スケジュール組み立て失敗 (%d年%d月): %s",
                    year, month, e.getMessage());
            log.warn(msg);
            if (notifyOnFailure) {
                lineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, msg);
            }
            return;
        }

        // 6. cd/base 抽出
        String urlStr = densukeUrl.getUrl();
        String cd = DensukeWriteService.extractCd(urlStr);
        String base = DensukeWriteService.extractBase(urlStr);
        if (cd == null || base == null) {
            String msg = String.format("伝助URL解析失敗 (%d年%d月, url=%s)", year, month, urlStr);
            log.warn(msg);
            if (notifyOnFailure) {
                lineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, msg);
            }
            return;
        }

        // 7. GET /list で Cookie と pageId を取得 → POST /update
        try {
            Connection.Response listResponse = Jsoup.connect(base + "list?cd=" + cd)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .execute();
            Map<String, String> cookies = listResponse.cookies();
            Document listDoc = listResponse.parse();
            String pageId = densukeWriteService.extractPageId(listDoc);
            if (pageId == null) {
                String msg = String.format("伝助ページID取得失敗 (cd=%s)", cd);
                log.warn(msg);
                if (notifyOnFailure) {
                    lineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, msg);
                }
                return;
            }

            Connection.Response updateResponse = Jsoup.connect(base + "update")
                    .data("cd", cd)
                    .data("id", pageId)
                    .data("postfix", "")
                    .data("schedule", scheduleText)
                    .cookies(cookies)
                    .method(Connection.Method.POST)
                    .userAgent("Mozilla/5.0")
                    .referrer(base + "edit2?cd=" + cd)
                    .header("Origin", "https://densuke.biz")
                    .followRedirects(false)
                    .ignoreHttpErrors(true)
                    .timeout(10000)
                    .execute();

            int status = updateResponse.statusCode();
            if (status != 302) {
                String msg = String.format("伝助 POST /update が失敗 (cd=%s, HTTP %d, added=%d days)",
                        cd, status, newSessions.size());
                log.warn(msg);
                if (notifyOnFailure) {
                    lineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, msg);
                }
                return;
            }

            log.info("Densuke schedule pushed: cd={}, {}/{}, addedDates={}",
                    cd, year, month, newSessions.size());
        } catch (IOException e) {
            String msg = String.format("伝助への接続失敗 (cd=%s, %d年%d月): %s",
                    cd, year, month, e.getMessage());
            log.warn(msg);
            if (notifyOnFailure) {
                lineNotificationService.sendDensukeScheduleSyncFailedNotification(organizationId, msg);
            }
        }
    }
}
