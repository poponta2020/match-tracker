package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.DensukeWriteResult;
import com.karuta.matchtracker.dto.DensukeWriteStatusDto;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.repository.*;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * アプリ→伝助への書き込みサービス
 *
 * dirty=true の参加者を対象に、伝助へ出欠を書き込む。
 * 書き込み成功後に dirty=false に更新する。
 *
 * 伝助の書き込みフロー（3段階）:
 * ① メンバー追加: POST insert?cd={code} (id, membername)
 * ② 編集画面取得: POST list?cd={code} (id, mi) → join-{dateId} を取得
 * ③ 出欠登録:    POST regist?cd={code} (id, mi, ai=u2, membername, join-{dateId}, membercomment)
 *
 * Cookie管理が必要: GET list 時に発行される {cd}LST Cookie を後続リクエストに付与する。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DensukeWriteService {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final DensukeUrlRepository densukeUrlRepository;
    private final DensukeMemberMappingRepository densukeMemberMappingRepository;
    private final DensukeRowIdRepository densukeRowIdRepository;
    private final PlayerRepository playerRepository;
    private final DensukeScraper densukeScraper;

    private static final Pattern MEMBERDATA_PATTERN = Pattern.compile("memberdata\\((\\d+)\\)");

    // 団体別ステータス管理（メモリ上）
    // 注意: シングルインスタンス運用を前提としている。
    private final Map<Long, LocalDateTime> lastAttemptAtByOrg = new ConcurrentHashMap<>();
    private final Map<Long, LocalDateTime> lastSuccessAtByOrg = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> lastErrorsByOrg = new ConcurrentHashMap<>();
    private final Map<Long, Integer> lastPendingCountByOrg = new ConcurrentHashMap<>();

    public DensukeWriteStatusDto getStatus(Long organizationId) {
        return DensukeWriteStatusDto.builder()
                .lastAttemptAt(lastAttemptAtByOrg.get(organizationId))
                .lastSuccessAt(lastSuccessAtByOrg.get(organizationId))
                .errors(new ArrayList<>(lastErrorsByOrg.getOrDefault(organizationId, List.of())))
                .pendingCount(lastPendingCountByOrg.getOrDefault(organizationId, 0))
                .build();
    }

    /**
     * dirty=true の参加者を伝助に書き込む（スケジューラーから呼ばれる）
     */
    @Transactional
    public void writeToDensuke() {
        // 当月・翌月の全団体の DensukeUrl を取得
        LocalDate now = JstDateTimeUtil.today();
        List<DensukeUrl> urls = new ArrayList<>();
        urls.addAll(densukeUrlRepository.findByYearAndMonth(now.getYear(), now.getMonthValue()));
        LocalDate next = now.plusMonths(1);
        urls.addAll(densukeUrlRepository.findByYearAndMonth(next.getYear(), next.getMonthValue()));
        writeToDensukeInternal(urls);
    }

    /**
     * 指定団体・指定年月のdirty参加者のみを伝助へ書き込む（手動同期用）。
     */
    @Transactional
    public void writeToDensukeForOrganization(int year, int month, Long organizationId) {
        List<DensukeUrl> urls = densukeUrlRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                .map(List::of)
                .orElseGet(List::of);

        LocalDateTime now = JstDateTimeUtil.now();
        if (urls.isEmpty()) {
            lastAttemptAtByOrg.put(organizationId, now);
            lastPendingCountByOrg.put(organizationId, 0);
            lastErrorsByOrg.put(organizationId, List.of("対象年月の伝助URLが未登録のため書き込みをスキップしました"));
            return;
        }

        writeToDensukeInternal(urls);
    }

    /**
     * 指定の伝助URL（団体・年月特定済み）に対してdirty参加者を書き込む。
     */
    @Transactional
    public void writeToDensukeForOrganization(DensukeUrl densukeUrl) {
        if (densukeUrl == null) {
            return;
        }
        writeToDensukeInternal(List.of(densukeUrl));
    }

    private void writeToDensukeInternal(List<DensukeUrl> urls) {
        // 団体IDごとにグループ化してステータスを管理
        Map<Long, List<DensukeUrl>> urlsByOrg = urls.stream()
                .collect(Collectors.groupingBy(DensukeUrl::getOrganizationId));

        // 登録されている全団体のステータスを更新
        for (Long orgId : urlsByOrg.keySet()) {
            lastAttemptAtByOrg.put(orgId, JstDateTimeUtil.now());
        }

        if (urls.isEmpty()) {
            return;
        }

        // 団体別エラー
        Map<Long, List<String>> errorsByOrg = new HashMap<>();
        // URL ごとに、その団体のセッションを取得
        Map<Long, DensukeUrl> urlById = urls.stream()
                .collect(Collectors.toMap(DensukeUrl::getId, u -> u));
        Map<Long, List<PracticeSession>> sessionsByUrlId = new LinkedHashMap<>();
        for (DensukeUrl url : urls) {
            List<PracticeSession> sessions = practiceSessionRepository
                    .findByYearAndMonthAndOrganizationId(url.getYear(), url.getMonth(), url.getOrganizationId());
            sessionsByUrlId.put(url.getId(), sessions);
        }

        // 対象セッション全体の ID セット
        List<Long> allSessionIds = sessionsByUrlId.values().stream()
                .flatMap(List::stream)
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        if (allSessionIds.isEmpty()) {
            for (Long orgId : urlsByOrg.keySet()) {
                lastPendingCountByOrg.put(orgId, 0);
                lastErrorsByOrg.put(orgId, List.of());
            }
            return;
        }

        // dirty=true かつ matchNumber!=null の参加者を取得（BYE除外・未入力保護）
        List<PracticeParticipant> dirtyParticipants =
                practiceParticipantRepository.findDirtyForDensukeSync(allSessionIds);

        if (dirtyParticipants.isEmpty()) {
            for (Long orgId : urlsByOrg.keySet()) {
                lastPendingCountByOrg.put(orgId, 0);
                lastErrorsByOrg.put(orgId, List.of());
                lastSuccessAtByOrg.put(orgId, JstDateTimeUtil.now());
            }
            return;
        }

        // セッションID → DensukeUrlId のマップ
        Map<Long, Long> sessionToUrlId = new HashMap<>();
        for (Map.Entry<Long, List<PracticeSession>> entry : sessionsByUrlId.entrySet()) {
            for (PracticeSession s : entry.getValue()) {
                sessionToUrlId.put(s.getId(), entry.getKey());
            }
        }

        // セッションID → PracticeSession のマップ
        Map<Long, PracticeSession> sessionMap = sessionsByUrlId.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(PracticeSession::getId, s -> s));

        // dirty 参加者をプレイヤー×URLでグループ化
        Map<Long, Map<Long, List<PracticeParticipant>>> grouped = new LinkedHashMap<>();
        for (PracticeParticipant p : dirtyParticipants) {
            Long urlId = sessionToUrlId.get(p.getSessionId());
            if (urlId == null) continue;
            grouped.computeIfAbsent(urlId, k -> new LinkedHashMap<>())
                    .computeIfAbsent(p.getPlayerId(), k -> new ArrayList<>())
                    .add(p);
        }

        // プレイヤー名マップ
        Set<Long> playerIds = dirtyParticipants.stream()
                .map(PracticeParticipant::getPlayerId).collect(Collectors.toSet());
        Map<Long, String> playerNames = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));

        // A-4: DB側の正規化名衝突（複数 playerId が同じ正規化名）を書き込み側でもスキップする（読取側と整合）。
        Set<String> dbCollisionNames = findDbNameCollisions(playerNames);

        // 各 URL の書き込み
        for (var urlEntry : grouped.entrySet()) {
            Long urlId = urlEntry.getKey();
            DensukeUrl densukeUrl = urlById.get(urlId);
            if (densukeUrl == null) {
                continue;
            }
            Long orgId = densukeUrl.getOrganizationId();
            List<String> orgErrors = errorsByOrg.computeIfAbsent(orgId, k -> new ArrayList<>());
            List<PracticeSession> urlSessions = sessionsByUrlId.get(urlId);
            String urlStr = densukeUrl.getUrl();
            String cd = extractCd(urlStr);
            String base = extractBase(urlStr);
            if (cd == null || base == null) {
                orgErrors.add("URL解析エラー: " + urlStr);
                continue;
            }

            // ① リストページを1回取得: Cookie・ページID・メンバーマップを得る
            Map<String, String> cookies;
            String pageId;
            Map<String, String> memberNameToMi;
            try {
                Connection.Response listResponse = Jsoup.connect(base + "list?cd=" + cd)
                        .userAgent("Mozilla/5.0")
                        .timeout(10000)
                        .execute();
                cookies = listResponse.cookies();
                Document listDoc = listResponse.parse();
                pageId = extractPageId(listDoc);
                Set<String> nameCollisions = new LinkedHashSet<>();
                memberNameToMi = extractAllMemberMappings(listDoc, nameCollisions);
                if (!nameCollisions.isEmpty()) {
                    orgErrors.add("名寄せ衝突（伝助列で正規化後同名）: " + String.join("、", nameCollisions)
                            + "（該当名は書き込みスキップ。重複選手を統合してください）");
                }
                log.info("Densuke list fetched: cd={}, pageId={}, {} members found", cd, pageId, memberNameToMi.size());
            } catch (IOException e) {
                log.warn("Failed to fetch densuke list page for cd={}: {}", cd, e.getMessage());
                orgErrors.add("伝助リストページ取得失敗: " + e.getMessage());
                continue;
            }

            if (pageId == null) {
                orgErrors.add("伝助ページID取得失敗: cd=" + cd);
                continue;
            }

            // ② 各プレイヤーの書き込み（B-3: row_id 整合検証は1URL1回に集約・usable を共有）
            Map<Long, Boolean> urlRowIdStatus = new HashMap<>();
            for (var playerEntry : urlEntry.getValue().entrySet()) {
                Long playerId = playerEntry.getKey();
                List<PracticeParticipant> participants = playerEntry.getValue();
                String playerName = playerNames.getOrDefault(playerId, "ID=" + playerId);

                if (dbCollisionNames.contains(DensukeScraper.normalizeMemberName(playerName))) {
                    log.warn("A-4: skip densuke write for DB name-collision player {} (urlId={})", playerName, urlId);
                    orgErrors.add("名寄せ衝突（DB上で正規化後同名の複数選手）: " + playerName
                            + "（書き込みスキップ。重複選手を統合してください）");
                    continue;
                }

                try {
                    writePlayerToDensuke(urlId, playerId, playerName,
                            participants, urlSessions, sessionMap,
                            base, cd, cookies, pageId, memberNameToMi, orgErrors, false, urlRowIdStatus);
                } catch (Exception e) {
                    log.warn("Failed to write player {} to densuke {}: {}", playerName, urlStr, e.getMessage());
                    orgErrors.add("選手[" + playerName + "]: " + e.getMessage());
                }
            }
        }

        // 団体別にステータスを更新
        // URL → 団体のマッピングを使って、団体別のpending countを集計
        Map<Long, Integer> pendingByOrg = new HashMap<>();
        for (var urlEntry : grouped.entrySet()) {
            Long urlId = urlEntry.getKey();
            DensukeUrl densukeUrl = urlById.get(urlId);
            if (densukeUrl == null) {
                continue;
            }
            Long orgId = densukeUrl.getOrganizationId();
            pendingByOrg.merge(orgId, urlEntry.getValue().values().stream()
                    .mapToInt(List::size).sum(), Integer::sum);
        }
        for (Long orgId : urlsByOrg.keySet()) {
            List<String> orgErrors = new ArrayList<>(errorsByOrg.getOrDefault(orgId, List.of()));
            lastErrorsByOrg.put(orgId, orgErrors);
            lastPendingCountByOrg.put(orgId, pendingByOrg.getOrDefault(orgId, 0));
            if (orgErrors.isEmpty()) {
                lastSuccessAtByOrg.put(orgId, JstDateTimeUtil.now());
            }
        }
    }

    /**
     * 抽選確定時の一括書き戻し。
     * 伝助マッピングがある全プレイヤーについて、WON/WAITLISTED/OFFERED/PENDING のみ書き戻す。
     * CANCELLED/DECLINED/WAITLIST_DECLINED/未登録は伝助の既存値を維持（join-{id} を省略）。
     * 書き戻したレコードのみ dirty=false に更新する。
     *
     * <p>外側の {@code @Transactional}（{@link LotteryService#executeAndConfirmLottery}）から
     * 完全に独立したトランザクションで実行する（{@link Propagation#REQUIRES_NEW}）。
     * 書き戻し中に未捕捉の RuntimeException が発生した場合でも、Spring プロキシが外側
     * トランザクションを rollback-only にマークしないようにし、抽選確定の DB 更新
     * （{@code confirmed_at} 等）が巻き戻らないようにする。部分失敗は引き続き
     * {@link DensukeWriteResult} で呼び出し元に返す。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DensukeWriteResult writeAllForLotteryConfirmation(Long organizationId, int year, int month) {
        log.info("Starting bulk write-back for lottery confirmation: orgId={}, {}-{}", organizationId, year, month);
        lastAttemptAtByOrg.put(organizationId, JstDateTimeUtil.now());

        List<DensukeUrl> urls = densukeUrlRepository.findByYearAndMonth(year, month).stream()
                .filter(u -> u.getOrganizationId().equals(organizationId))
                .collect(Collectors.toList());

        if (urls.isEmpty()) {
            log.info("No densuke URLs found for orgId={}, {}-{}", organizationId, year, month);
            lastErrorsByOrg.put(organizationId, List.of());
            lastSuccessAtByOrg.put(organizationId, JstDateTimeUtil.now());
            return DensukeWriteResult.success();
        }

        List<String> errors = new ArrayList<>();
        // A-3: ○書き戻し予定なのに伝助側×の反転リスク差分（確定はブロックしない・通知/可視化用）
        List<String> densukeDiffs = new ArrayList<>();

        for (DensukeUrl densukeUrl : urls) {
            Long urlId = densukeUrl.getId();
            String urlStr = densukeUrl.getUrl();
            String cd = extractCd(urlStr);
            String base = extractBase(urlStr);
            if (cd == null || base == null) {
                log.warn("URL parse error for bulk write-back: {}", urlStr);
                errors.add("伝助URL解析エラー: " + urlStr);
                continue;
            }

            List<PracticeSession> sessions = practiceSessionRepository
                    .findByYearAndMonthAndOrganizationId(year, month, organizationId);
            if (sessions.isEmpty()) continue;

            Map<Long, PracticeSession> sessionMap = sessions.stream()
                    .collect(Collectors.toMap(PracticeSession::getId, s -> s));
            List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());

            // 全マッピング済みプレイヤーを取得
            List<DensukeMemberMapping> mappings = densukeMemberMappingRepository.findByDensukeUrlId(urlId);
            if (mappings.isEmpty()) {
                log.info("No member mappings for urlId={}", urlId);
                continue;
            }

            // リストページを取得
            Map<String, String> cookies;
            String pageId;
            Map<String, String> memberNameToMi;
            try {
                Connection.Response listResponse = Jsoup.connect(base + "list?cd=" + cd)
                        .userAgent("Mozilla/5.0").timeout(10000).execute();
                cookies = listResponse.cookies();
                Document listDoc = listResponse.parse();
                pageId = extractPageId(listDoc);
                Set<String> nameCollisions = new LinkedHashSet<>();
                memberNameToMi = extractAllMemberMappings(listDoc, nameCollisions);
                if (!nameCollisions.isEmpty()) {
                    errors.add("名寄せ衝突（伝助列で正規化後同名）: " + String.join("、", nameCollisions)
                            + "（該当名は書き込みスキップ。重複選手を統合してください）");
                }
            } catch (IOException e) {
                log.warn("Failed to fetch densuke list page for bulk write-back: {}", e.getMessage());
                errors.add("伝助リストページ取得失敗(cd=" + cd + "): " + e.getMessage());
                continue;
            }

            if (pageId == null) {
                errors.add("伝助ページID取得失敗: cd=" + cd);
                continue;
            }

            // プレイヤー名マップ
            Set<Long> playerIds = mappings.stream()
                    .map(DensukeMemberMapping::getPlayerId).collect(Collectors.toSet());
            Map<Long, String> playerNames = playerRepository.findAllById(playerIds).stream()
                    .collect(Collectors.toMap(Player::getId, Player::getName));

            // A-4: DB側の正規化名衝突（複数 playerId が同じ正規化名）を書き込み側でもスキップする（読取側と整合）。
            Set<String> dbCollisionNames = findDbNameCollisions(playerNames);

            // A-3: 書き戻し直前に伝助を1回読み、伝助側×（明示的不参加）の (日付, 試合番号, 正規化名) を収集。
            // scrape 失敗は WARN のみで確定/書き戻しをブロックしない（差分検知はベストエフォート）。
            Set<String> densukeDeclinedKeys = new HashSet<>();
            try {
                DensukeScraper.DensukeData scraped = densukeScraper.scrape(urlStr, year);
                for (DensukeScraper.ScheduleEntry se : scraped.getEntries()) {
                    for (String n : se.getDeclinedParticipants()) {
                        // 照合側（normalizeMemberName(playerName)）と揃えるため正規化してキー化する。
                        // 伝助側 "田中 "（末尾空白）×とアプリ側 "田中" WON の空白差分でも検知できるようにする。
                        densukeDeclinedKeys.add(se.getDate() + "|" + se.getMatchNumber() + "|"
                                + DensukeScraper.normalizeMemberName(n));
                    }
                }
            } catch (Exception e) {
                log.warn("A-3 pre-confirm diff scrape failed for cd={}: {} (diff detection skipped, write continues)",
                        cd, e.getMessage());
            }

            // 各プレイヤーを書き込み（WON/WAITLISTED/OFFERED/PENDING のみ、dirty フィルタなし）
            // B-3: row_id 整合検証は1URL1回に集約・usable を共有
            Map<Long, Boolean> urlRowIdStatus = new HashMap<>();
            for (DensukeMemberMapping mapping : mappings) {
                Long playerId = mapping.getPlayerId();
                String playerName = playerNames.getOrDefault(playerId, "ID=" + playerId);

                if (dbCollisionNames.contains(DensukeScraper.normalizeMemberName(playerName))) {
                    log.warn("A-4: skip densuke write-back for DB name-collision player {} (urlId={})", playerName, urlId);
                    errors.add("名寄せ衝突（DB上で正規化後同名の複数選手）: " + playerName
                            + "（書き込みスキップ。重複選手を統合してください）");
                    continue;
                }

                List<PracticeParticipant> allParticipants =
                        practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds);

                // A-3: アプリ側○書き戻し予定（WON/OFFERED/PENDING）なのに伝助側×の反転リスクを検知
                if (!densukeDeclinedKeys.isEmpty()) {
                    String normName = DensukeScraper.normalizeMemberName(playerName);
                    for (PracticeParticipant pp : allParticipants) {
                        if (pp.getMatchNumber() == null || pp.getStatus() == null) continue;
                        ParticipantStatus s = pp.getStatus();
                        if (s != ParticipantStatus.WON && s != ParticipantStatus.OFFERED
                                && s != ParticipantStatus.PENDING) continue;
                        PracticeSession sess = sessionMap.get(pp.getSessionId());
                        if (sess == null) continue;
                        String key = sess.getSessionDate() + "|" + pp.getMatchNumber() + "|" + normName;
                        if (densukeDeclinedKeys.contains(key)) {
                            densukeDiffs.add(playerName + ": " + sess.getSessionDate()
                                    + " 第" + pp.getMatchNumber() + "試合（アプリ" + s + "→○書き戻し予定・伝助×）");
                        }
                    }
                }

                try {
                    writePlayerToDensuke(urlId, playerId, playerName,
                            allParticipants, sessions, sessionMap,
                            base, cd, cookies, pageId, memberNameToMi, errors, true, urlRowIdStatus);
                } catch (Exception e) {
                    log.warn("Bulk write-back failed for player {}: {}", playerName, e.getMessage());
                    errors.add("一括書き戻し[" + playerName + "]: " + e.getMessage());
                }
            }
            // dirty=false 更新は writePlayerToDensuke 内で書き戻したレコードのみに適用される
        }

        if (!densukeDiffs.isEmpty()) {
            log.warn("A-3: pre-confirm densuke reversal-risk diffs detected ({}): {}",
                    densukeDiffs.size(), densukeDiffs);
        }

        if (!errors.isEmpty()) {
            log.warn("Bulk write-back completed with {} errors", errors.size());
            lastErrorsByOrg.put(organizationId, new ArrayList<>(errors));
            return DensukeWriteResult.failure(errors).withDensukeDiffs(densukeDiffs);
        }

        log.info("Bulk write-back completed successfully for orgId={}, {}-{}", organizationId, year, month);
        lastErrorsByOrg.put(organizationId, List.of());
        lastSuccessAtByOrg.put(organizationId, JstDateTimeUtil.now());
        return DensukeWriteResult.success().withDensukeDiffs(densukeDiffs);
    }

    private void writePlayerToDensuke(
            Long urlId, Long playerId, String playerName,
            List<PracticeParticipant> dirtyParticipants,
            List<PracticeSession> urlSessions,
            Map<Long, PracticeSession> sessionMap,
            String base, String cd,
            Map<String, String> cookies, String pageId,
            Map<String, String> memberNameToMi,
            List<String> errors,
            boolean lotteryConfirmation,
            Map<Long, Boolean> urlRowIdStatus) throws IOException {

        String strippedName = DensukeScraper.normalizeMemberName(playerName);

        // a. densuke_member_id を取得
        String mi = densukeMemberMappingRepository
                .findByDensukeUrlIdAndPlayerId(urlId, playerId)
                .map(DensukeMemberMapping::getDensukeMemberId)
                .orElse(null);

        if (mi == null) {
            // リストページのメンバーマップから探す
            mi = memberNameToMi.get(strippedName);
            if (mi != null) {
                if (!saveMemberMapping(urlId, playerId, mi, playerName)) {
                    errors.add("選手[" + playerName + "]: 伝助メンバーID(" + mi + ")が別選手に既にマッピングされているため書き戻しを中断");
                    return;
                }
            }
        }

        if (mi == null) {
            // メンバーを新規追加
            mi = insertMember(urlId, playerId, strippedName, base, cd, cookies, pageId, errors);
        }

        if (mi == null) {
            errors.add("選手[" + playerName + "]: メンバーIDの取得に失敗");
            return;
        }

        // b. densuke_row_ids を取得（編集フォームをフェッチして保存＋B-3構造整合検証、1URL1回）
        boolean rowIdsUsable = ensureRowIds(urlId, urlSessions, sessionMap, base, cd, mi, cookies, pageId, errors, urlRowIdStatus);
        if (!rowIdsUsable) {
            // B-3: フォーム行数不一致で row_id の位置合わせが保証できない → 当該書き込みを中止。
            // stale な row_id で別日/別試合へ書き込むデータ破壊を防ぐ（URL単位のエラーは errors[] に記録済み）。
            log.warn("Skip densuke write for player {} (urlId={}): row_id integrity not guaranteed (form row count mismatch)",
                    playerName, urlId);
            return;
        }

        // c. 当該URLの全セッション×このプレイヤーのステータスを取得
        List<Long> urlSessionIds = urlSessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allPlayerParticipants =
                practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, urlSessionIds);
        Map<String, PracticeParticipant> bySessionAndMatch = allPlayerParticipants.stream()
                .collect(Collectors.toMap(
                        p -> p.getSessionId() + "_" + p.getMatchNumber(),
                        p -> p, (a, b) -> a));

        // d. row ID をプリフェッチ
        Map<String, String> rowIdsByKey = new LinkedHashMap<>();
        for (PracticeSession session : urlSessions) {
            for (int matchNum = 1; matchNum <= session.getTotalMatches(); matchNum++) {
                String key = session.getId() + "_" + matchNum;
                densukeRowIdRepository
                        .findByDensukeUrlIdAndSessionDateAndMatchNumber(
                                urlId, session.getSessionDate(), matchNum)
                        .ifPresent(r -> rowIdsByKey.put(key, r.getDensukeRowId()));
            }
        }

        // e. regist フォームデータを構築
        RegistFormResult registResult = buildRegistFormData(
                pageId, mi, strippedName, dirtyParticipants,
                urlSessions, bySessionAndMatch, rowIdsByKey, lotteryConfirmation);
        Map<String, String> formData = registResult.formData;
        Set<String> writtenSessionMatchKeys = registResult.writtenKeys;

        if (formData.size() <= 5) {
            log.debug("No row IDs found for player {} / urlId {}, skipping regist", playerName, urlId);
            return;
        }

        // e. POST regist（Cookie 付き）
        log.debug("Regist form data for {}: {}", playerName, formData);
        Connection.Response response = Jsoup.connect(base + "regist?cd=" + cd)
                .data(formData)
                .cookies(cookies)
                .method(Connection.Method.POST)
                .userAgent("Mozilla/5.0")
                .referrer(base + "list?cd=" + cd)
                .timeout(10000)
                .execute();

        log.info("Regist response for {}: status={}, url={}", playerName, response.statusCode(), response.url());
        if (response.statusCode() >= 400) {
            errors.add("選手[" + playerName + "]: regist HTTP " + response.statusCode());
            return;
        }

        // f. 成功 → regist に含まれた参加者のみ dirty=false に更新
        List<PracticeParticipant> writtenParticipants = dirtyParticipants.stream()
                .filter(p -> writtenSessionMatchKeys.contains(p.getSessionId() + "_" + p.getMatchNumber()))
                .collect(Collectors.toList());
        for (PracticeParticipant p : writtenParticipants) {
            p.setDirty(false);
        }
        practiceParticipantRepository.saveAll(writtenParticipants);

        int skippedCount = dirtyParticipants.size() - writtenParticipants.size();
        log.info("Written to densuke: player={}, urlId={}, {} written, {} skipped (no row ID)",
                playerName, urlId, writtenParticipants.size(), skippedCount);
    }

    /**
     * 伝助にメンバーを新規追加し、302リダイレクトから mi を取得する。
     */
    private String insertMember(Long urlId, Long playerId, String memberName,
                                 String base, String cd,
                                 Map<String, String> cookies, String pageId,
                                 List<String> errors) {
        try {
            log.info("Inserting new member to densuke: {}", memberName);
            Connection.Response insertResponse = Jsoup.connect(base + "insert?cd=" + cd)
                    .data("id", pageId)
                    .data("membername", memberName)
                    .cookies(cookies)
                    .method(Connection.Method.POST)
                    .userAgent("Mozilla/5.0")
                    .referrer(base + "list?cd=" + cd)
                    .followRedirects(false)
                    .timeout(10000)
                    .execute();

            // 302 リダイレクトの Location ヘッダーから ii パラメータで mi を取得
            String location = insertResponse.header("Location");
            if (location != null) {
                String mi = extractQueryParam(location, "ii");
                if (mi != null) {
                    if (!saveMemberMapping(urlId, playerId, mi, memberName)) {
                        errors.add("選手[" + memberName + "]: 伝助メンバーID(" + mi + ")が別選手に既にマッピングされているため書き戻しを中断");
                        return null;
                    }
                    return mi;
                }
                log.warn("Insert redirect has no ii param: {}", location);
            }

            // フォールバック: リストページを再取得して名前で検索
            log.info("Falling back to list page search after insert for: {}", memberName);
            Connection.Response listResponse = Jsoup.connect(base + "list?cd=" + cd)
                    .cookies(cookies)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .execute();
            Document listDoc = listResponse.parse();
            Map<String, String> memberMap = extractAllMemberMappings(listDoc);
            String mi = memberMap.get(memberName);
            if (mi != null) {
                if (!saveMemberMapping(urlId, playerId, mi, memberName)) {
                    errors.add("選手[" + memberName + "]: 伝助メンバーID(" + mi + ")が別選手に既にマッピングされているため書き戻しを中断");
                    return null;
                }
                return mi;
            }

            log.warn("Could not find mi for player {} after insert", memberName);
            return null;

        } catch (IOException e) {
            log.warn("Failed to insert densuke member {}: {}", memberName, e.getMessage());
            errors.add("選手[" + memberName + "]: メンバー追加失敗 - " + e.getMessage());
            return null;
        }
    }

    /**
     * メンバーマッピングを保存する。
     * @return true: 保存成功（または同一プレイヤーで既存）、false: 別プレイヤーに競合しており保存失敗
     */
    boolean saveMemberMapping(Long urlId, Long playerId, String mi, String playerName) {
        // 同一 densuke_member_id が既にマッピングされていないかチェック
        Optional<DensukeMemberMapping> existing =
                densukeMemberMappingRepository.findByDensukeUrlIdAndDensukeMemberId(urlId, mi);
        if (existing.isPresent()) {
            if (existing.get().getPlayerId().equals(playerId)) {
                // 同一プレイヤーで既に登録済み → 再保存不要、成功扱い
                log.debug("Densuke member mapping already exists: player={}, mi={}", playerName, mi);
                return true;
            }
            log.warn("Densuke member_id {} is already mapped to player_id={}, skipping mapping for player={} (id={})",
                    mi, existing.get().getPlayerId(), playerName, playerId);
            return false;
        }
        try {
            densukeMemberMappingRepository.save(DensukeMemberMapping.builder()
                    .densukeUrlId(urlId)
                    .playerId(playerId)
                    .densukeMemberId(mi)
                    .build());
            log.info("Mapped densuke member: player={}, mi={}", playerName, mi);
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // TOCTOU: チェック後に並行処理で先に登録された場合 → 再取得して同一プレイヤーなら成功扱い
            Optional<DensukeMemberMapping> retry =
                    densukeMemberMappingRepository.findByDensukeUrlIdAndDensukeMemberId(urlId, mi);
            if (retry.isPresent() && retry.get().getPlayerId().equals(playerId)) {
                log.info("Concurrent mapping resolved as same player: player={}, mi={}", playerName, mi);
                return true;
            }
            log.warn("Concurrent mapping detected for densuke member_id {} (player={}): {}",
                    mi, playerName, e.getMessage());
            return false;
        }
    }

    /**
     * A-4: 書き込み対象プレイヤーのうち、正規化後に同名となる複数 playerId（DB側の名寄せ衝突）の
     * 正規化名を返す。読取側（DensukeImportService.playerNameMap の衝突検知）と同じ定義で、
     * 書き込み側でも当該名の選手をスキップして別人の伝助列へ○×が付く事故を防ぐ。
     */
    private Set<String> findDbNameCollisions(Map<Long, String> playerIdToName) {
        Map<String, Long> countByNormalized = new java.util.HashMap<>();
        for (String name : playerIdToName.values()) {
            countByNormalized.merge(DensukeScraper.normalizeMemberName(name), 1L, Long::sum);
        }
        return countByNormalized.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Map<String, String> extractAllMemberMappings(Document doc) {
        return extractAllMemberMappings(doc, null);
    }

    /**
     * リスト画面のヘッダー行から全メンバーの名前→mi マッピングを構築する。
     * 名前は {@link DensukeScraper#normalizeMemberName(String)} で正規化（絵文字・不可視文字・全空白除去）した状態で格納する。
     *
     * <p>A-4: 正規化後に同名となる複数の伝助メンバー列（名寄せ衝突）を検知したら、当該名を
     * マッピングから除外する（後勝ちで別列に出欠を書き込む事故＝別人に丸/×が付くのを防ぐ）。
     * 衝突した名前は {@code collisionsOut}（非null時）に格納し、呼び出し元が管理者へ通知できるようにする。
     */
    private Map<String, String> extractAllMemberMappings(Document doc, Set<String> collisionsOut) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> collisions = new LinkedHashSet<>();
        Element table = doc.selectFirst("table.listtbl");
        if (table == null) return result;
        Element headerRow = table.selectFirst("tr");
        if (headerRow == null) return result;

        for (Element cell : headerRow.select("td")) {
            Element link = cell.selectFirst("a");
            if (link == null) continue;
            String name = DensukeScraper.normalizeMemberName(link.text().trim());
            if (name.isEmpty()) continue;

            String href = link.attr("href");
            Matcher m = MEMBERDATA_PATTERN.matcher(href);
            if (m.find()) {
                if (result.containsKey(name) || collisions.contains(name)) {
                    collisions.add(name);
                } else {
                    result.put(name, m.group(1));
                }
            }
        }
        // A-4: 衝突名は書き込み対象から除外（黙って後勝ち/先勝ちにしない）
        for (String c : collisions) {
            result.remove(c);
        }
        if (!collisions.isEmpty()) {
            log.warn("Densuke write: member name collisions detected, skipped {} name(s): {}",
                    collisions.size(), collisions);
            if (collisionsOut != null) {
                collisionsOut.addAll(collisions);
            }
        }
        return result;
    }

    /**
     * リスト画面の hidden input name="id" からページ固有IDを取得する。
     * <p>package-private: 同パッケージの {@link DensukeScheduleWriteService} から流用するため
     * 可視性を拡張している（伝助の編集系 POST に必要な pageId を共通取得する目的）。
     */
    String extractPageId(Document doc) {
        Element idInput = doc.selectFirst("input[name=id]");
        if (idInput != null) {
            return idInput.attr("value");
        }
        log.warn("Could not find hidden 'id' input in densuke list page");
        return null;
    }

    /**
     * 各セッション×試合の join-{id} を確認し、未保存のものを取得して保存する。
     */
    /**
     * @return row_id が書き込みに使える整合状態なら true（呼び出し元は regist 続行）、
     *         フォーム行数不一致で位置合わせ不能なら false（呼び出し元は当該書き込みを中止）。
     */
    private boolean ensureRowIds(Long urlId, List<PracticeSession> sessions,
                               Map<Long, PracticeSession> sessionMap,
                               String base, String cd, String mi,
                               Map<String, String> cookies, String pageId,
                               List<String> errors, Map<Long, Boolean> urlRowIdStatus) throws IOException {
        // B-3: 当該URLはこのバッチで既に判定済みなら再フェッチせずキャッシュした usable を返す（1URL1回）。
        if (urlRowIdStatus != null) {
            Boolean cached = urlRowIdStatus.get(urlId);
            if (cached != null) {
                return cached;
            }
        }

        // 編集フォームを取得して現在の join-{id} 構造を得る（Cookie・id 付き）。
        // B-3: 未保存行の補完だけでなく、キャッシュ済み row_id と現フォーム構造の整合検証・矛盾時の
        // 再構築を必ず1回行う。全行キャッシュ済み（未保存行なし）でも構造変化を検知できるよう、
        // 未保存行の有無に関わらずフェッチ・検証する（従来の hasNew スキップだと構造変化を見逃す）。
        Document formDoc = Jsoup.connect(base + "list?cd=" + cd)
                .data("id", pageId)
                .data("mi", mi)
                .cookies(cookies)
                .method(Connection.Method.POST)
                .userAgent("Mozilla/5.0")
                .referrer(base + "list?cd=" + cd)
                .timeout(10000)
                .execute()
                .parse();

        Map<String, String> joinInputs = extractJoinInputs(formDoc);
        boolean usable = parseAndSaveRowIds(urlId, sessions, formDoc, joinInputs, errors);

        if (urlRowIdStatus != null) {
            urlRowIdStatus.put(urlId, usable);
        }
        return usable;
    }

    /**
     * 編集フォームから join-{id} のマップを抽出する。
     */
    private Map<String, String> extractJoinInputs(Document doc) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Element input : doc.select("input[name], select[name]")) {
            String name = input.attr("name");
            if (name.startsWith("join-")) {
                result.put(name, input.attr("value"));
            }
        }
        return result;
    }

    /**
     * フォームの行構造を解析し、日付×試合番号 → join-id の対応を densuke_row_ids に保存する。
     * 伝助の編集フォームでは、テーブルの各行が日程×試合番号に対応する。
     *
     * <p><b>前提条件（HTML構造への依存）:</b><br>
     * このメソッドは「伝助の編集フォームに含まれる {@code join-{id}} の出現順序」と
     * 「アプリ側の日付×試合番号の昇順」が一致することを前提としている。
     *
     * <p><b>HTML構造変更時のリスク:</b><br>
     * 伝助側の HTML 構造（テーブルレイアウト・input name の命名規則等）が変更された場合、
     * join-id の対応付けが無言でズレる可能性がある。その場合、書き込みは
     * {@code joinIds.size() < schedule.size()} のチェックを通過しつつも誤ったIDに
     * 書き込む、あるいは全スキップになる。障害発生時はまず伝助の編集フォームのHTMLを
     * 直接確認すること。
     */
    /**
     * @return row_id が書き込みに使える整合状態なら true。join-ID件数とスケジュール件数が
     *         不一致（伝助フォーム構造変化で位置合わせ不能）なら false を返し、呼び出し元は
     *         当該URLの書き込み（regist POST）を中止する（stale row_id での別日/別試合書き込みを防ぐ）。
     */
    boolean parseAndSaveRowIds(Long urlId, List<PracticeSession> sessions,
                                     Document formDoc, Map<String, String> joinInputs, List<String> errors) {
        Element table = formDoc.selectFirst("table.listtbl");
        if (table == null || joinInputs.isEmpty()) {
            // フォーム解析に失敗（一時的な取得不良の可能性）。既存キャッシュを使う従来挙動を維持（非ブロック）。
            log.warn("Could not find listtbl or join inputs in densuke edit form (urlId={})", urlId);
            return true;
        }

        List<Map.Entry<LocalDate, Integer>> schedule = buildScheduleOrder(sessions);
        List<String> joinIds = new ArrayList<>(joinInputs.keySet());

        if (joinIds.size() != schedule.size()) {
            // B-3: 件数不一致は伝助フォーム構造変化の可能性が高く、位置合わせが保証できない。
            // errors[] に記録して可視化し、false を返して当該URLの書き込みを中止させる
            // （stale な row_id で別日/別試合に書き込むデータ破壊を防ぐ）。
            log.warn("Join ID count ({}) differs from schedule count ({}), aborting write to avoid misalignment. urlId={}",
                    joinIds.size(), schedule.size(), urlId);
            if (errors != null) {
                errors.add("伝助フォームの行数(" + joinIds.size() + ")とアプリの予定数(" + schedule.size()
                        + ")が不一致のため当該URLの書き込みを中止しました（伝助フォーム構造変更の可能性・urlId=" + urlId + "）");
            }
            return false;
        }

        // B-3: row_id 整合の防御。現フォームの (日付×試合番号 → row_id) を組み立て、
        // キャッシュ済み densuke_row_ids と矛盾（同じ日付×試合番号で row_id が異なる）があれば、
        // 伝助フォーム構造が変化したとみなして当該URLの row_id を破棄し、現フォームから再構築する。
        Map<String, String> currentByKey = new LinkedHashMap<>();
        for (int i = 0; i < schedule.size(); i++) {
            Map.Entry<LocalDate, Integer> entry = schedule.get(i);
            currentByKey.put(entry.getKey() + "_" + entry.getValue(),
                    joinIds.get(i).substring("join-".length()));
        }

        List<DensukeRowId> cached = densukeRowIdRepository.findByDensukeUrlId(urlId);
        boolean structureChanged = cached.stream().anyMatch(r -> {
            String current = currentByKey.get(r.getSessionDate() + "_" + r.getMatchNumber());
            return current != null && !current.equals(r.getDensukeRowId());
        });
        if (structureChanged) {
            // 監査ログ: 伝助フォーム構造変化を検知・row_id 再構築
            log.warn("B-3: densuke form structure change detected for urlId={}. Discarding {} cached row_ids and rebuilding.",
                    urlId, cached.size());
            if (errors != null) {
                errors.add("伝助フォーム構造の変化を検知したため row_id を再構築しました（urlId=" + urlId + "）");
            }
            densukeRowIdRepository.deleteAll(cached);
            densukeRowIdRepository.flush();
            cached = List.of();
        }

        Set<String> cachedKeys = cached.stream()
                .map(r -> r.getSessionDate() + "_" + r.getMatchNumber())
                .collect(Collectors.toSet());

        List<DensukeRowId> toSave = new ArrayList<>();
        for (int i = 0; i < schedule.size(); i++) {
            Map.Entry<LocalDate, Integer> entry = schedule.get(i);
            String rowId = joinIds.get(i).substring("join-".length());

            if (cachedKeys.contains(entry.getKey() + "_" + entry.getValue())) continue;

            toSave.add(DensukeRowId.builder()
                    .densukeUrlId(urlId)
                    .densukeRowId(rowId)
                    .sessionDate(entry.getKey())
                    .matchNumber(entry.getValue())
                    .build());
        }

        if (!toSave.isEmpty()) {
            densukeRowIdRepository.saveAll(toSave);
            log.info("Saved {} densuke row IDs for urlId={}", toSave.size(), urlId);
        }
        return true;
    }

    private List<Map.Entry<LocalDate, Integer>> buildScheduleOrder(List<PracticeSession> sessions) {
        List<Map.Entry<LocalDate, Integer>> list = new ArrayList<>();
        List<PracticeSession> sorted = sessions.stream()
                .sorted(Comparator.comparing(PracticeSession::getSessionDate))
                .collect(Collectors.toList());
        for (PracticeSession s : sorted) {
            for (int m = 1; m <= s.getTotalMatches(); m++) {
                list.add(Map.entry(s.getSessionDate(), m));
            }
        }
        return list;
    }

    /**
     * regist フォームデータ構築結果
     */
    static class RegistFormResult {
        final Map<String, String> formData;
        final Set<String> writtenKeys;

        RegistFormResult(Map<String, String> formData, Set<String> writtenKeys) {
            this.formData = formData;
            this.writtenKeys = writtenKeys;
        }
    }

    /**
     * regist POST 用のフォームデータを構築する。
     * 通常同期時はdirty行のみ、抽選確定時はアクティブステータスのみを含める。
     * package-private でテスト可能にしている。
     */
    RegistFormResult buildRegistFormData(
            String pageId, String mi, String memberName,
            List<PracticeParticipant> dirtyParticipants,
            List<PracticeSession> urlSessions,
            Map<String, PracticeParticipant> bySessionAndMatch,
            Map<String, String> rowIdsByKey,
            boolean lotteryConfirmation) {

        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("id", pageId);
        formData.put("mi", mi);
        formData.put("ai", "u2");
        formData.put("membername", memberName);
        formData.put("membercomment", "");

        Set<String> writtenKeys = new HashSet<>();

        Set<String> dirtyKeys = dirtyParticipants.stream()
                .map(p -> p.getSessionId() + "_" + p.getMatchNumber())
                .collect(Collectors.toSet());

        for (PracticeSession session : urlSessions) {
            for (int matchNum = 1; matchNum <= session.getTotalMatches(); matchNum++) {
                String key = session.getId() + "_" + matchNum;
                String rowId = rowIdsByKey.get(key);
                if (rowId == null) continue;

                PracticeParticipant pp = bySessionAndMatch.get(key);
                ParticipantStatus status = pp != null ? pp.getStatus() : null;

                // 通常同期: dirtyでないマスはスキップ（未入力保護）
                if (!lotteryConfirmation && !dirtyKeys.contains(key)) {
                    continue;
                }

                // 抽選確定時: WON/WAITLISTED/OFFERED/PENDING のみ書き戻し、それ以外は省略
                if (lotteryConfirmation && !isActiveStatus(status)) {
                    continue;
                }

                String joinKey = "join-" + rowId;
                int value = toJoinValue(status);
                formData.put(joinKey, String.valueOf(value));
                writtenKeys.add(key);
            }
        }

        return new RegistFormResult(formData, writtenKeys);
    }

    /**
     * 抽選確定時に伝助へ書き戻す対象のステータスか判定する。
     * WON/WAITLISTED/OFFERED/PENDING は書き戻し対象、それ以外（CANCELLED等/未登録）は省略。
     */
    private boolean isActiveStatus(ParticipantStatus status) {
        if (status == null) return false;
        return switch (status) {
            case PENDING, WON, WAITLISTED, OFFERED -> true;
            case CANCELLED, DECLINED, WAITLIST_DECLINED -> false;
        };
    }

    private int toJoinValue(ParticipantStatus status) {
        if (status == null) return 1; // ×（未登録者は不参加扱い）
        return switch (status) {
            case PENDING, WON -> 3;       // ○
            case WAITLISTED, OFFERED -> 2; // △
            case CANCELLED, DECLINED, WAITLIST_DECLINED -> 1; // ×
        };
    }

    static String extractCd(String url) {
        return extractQueryParam(url, "cd");
    }

    static String extractBase(String url) {
        try {
            URI uri = new URI(url);
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (Exception e) {
            log.warn("Failed to extract base from URL: {}", url);
            return null;
        }
    }

    private static String extractQueryParam(String url, String paramName) {
        if (url == null) return null;
        try {
            URI uri = new URI(url.replace(" ", "%20"));
            String query = uri.getQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && paramName.equals(kv[0])) {
                    return kv[1];
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract param '{}' from: {}", paramName, url);
        }
        return null;
    }
}
