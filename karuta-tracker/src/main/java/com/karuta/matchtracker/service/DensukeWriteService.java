package com.karuta.matchtracker.service;

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
                memberNameToMi = extractAllMemberMappings(listDoc);
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

            // ② 各プレイヤーの書き込み
            for (var playerEntry : urlEntry.getValue().entrySet()) {
                Long playerId = playerEntry.getKey();
                List<PracticeParticipant> participants = playerEntry.getValue();
                String playerName = playerNames.getOrDefault(playerId, "ID=" + playerId);

                try {
                    writePlayerToDensuke(urlId, playerId, playerName,
                            participants, urlSessions, sessionMap,
                            base, cd, cookies, pageId, memberNameToMi, orgErrors, false);
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
     */
    @Transactional
    public void writeAllForLotteryConfirmation(Long organizationId, int year, int month) {
        log.info("Starting bulk write-back for lottery confirmation: orgId={}, {}-{}", organizationId, year, month);
        lastAttemptAtByOrg.put(organizationId, JstDateTimeUtil.now());

        List<DensukeUrl> urls = densukeUrlRepository.findByYearAndMonth(year, month).stream()
                .filter(u -> u.getOrganizationId().equals(organizationId))
                .collect(Collectors.toList());

        if (urls.isEmpty()) {
            log.info("No densuke URLs found for orgId={}, {}-{}", organizationId, year, month);
            lastErrorsByOrg.put(organizationId, List.of());
            return;
        }

        List<String> errors = new ArrayList<>();

        for (DensukeUrl densukeUrl : urls) {
            Long urlId = densukeUrl.getId();
            String urlStr = densukeUrl.getUrl();
            String cd = extractCd(urlStr);
            String base = extractBase(urlStr);
            if (cd == null || base == null) {
                log.warn("URL parse error for bulk write-back: {}", urlStr);
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
                memberNameToMi = extractAllMemberMappings(listDoc);
            } catch (IOException e) {
                log.warn("Failed to fetch densuke list page for bulk write-back: {}", e.getMessage());
                continue;
            }

            if (pageId == null) continue;

            // プレイヤー名マップ
            Set<Long> playerIds = mappings.stream()
                    .map(DensukeMemberMapping::getPlayerId).collect(Collectors.toSet());
            Map<Long, String> playerNames = playerRepository.findAllById(playerIds).stream()
                    .collect(Collectors.toMap(Player::getId, Player::getName));

            // 各プレイヤーを書き込み（WON/WAITLISTED/OFFERED/PENDING のみ、dirty フィルタなし）
            for (DensukeMemberMapping mapping : mappings) {
                Long playerId = mapping.getPlayerId();
                String playerName = playerNames.getOrDefault(playerId, "ID=" + playerId);
                List<PracticeParticipant> allParticipants =
                        practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds);

                try {
                    writePlayerToDensuke(urlId, playerId, playerName,
                            allParticipants, sessions, sessionMap,
                            base, cd, cookies, pageId, memberNameToMi, errors, true);
                } catch (Exception e) {
                    log.warn("Bulk write-back failed for player {}: {}", playerName, e.getMessage());
                    errors.add("一括書き戻し[" + playerName + "]: " + e.getMessage());
                }
            }
            // dirty=false 更新は writePlayerToDensuke 内で書き戻したレコードのみに適用される
        }

        if (!errors.isEmpty()) {
            log.warn("Bulk write-back completed with {} errors", errors.size());
            lastErrorsByOrg.put(organizationId, new ArrayList<>(errors));
        } else {
            log.info("Bulk write-back completed successfully for orgId={}, {}-{}", organizationId, year, month);
            lastErrorsByOrg.put(organizationId, List.of());
            lastSuccessAtByOrg.put(organizationId, JstDateTimeUtil.now());
        }
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
            boolean lotteryConfirmation) throws IOException {

        String strippedName = DensukeScraper.stripLeadingEmoji(playerName);

        // a. densuke_member_id を取得
        String mi = densukeMemberMappingRepository
                .findByDensukeUrlIdAndPlayerId(urlId, playerId)
                .map(DensukeMemberMapping::getDensukeMemberId)
                .orElse(null);

        if (mi == null) {
            // リストページのメンバーマップから探す
            mi = memberNameToMi.get(strippedName);
            if (mi != null) {
                saveMemberMapping(urlId, playerId, mi, playerName);
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

        // b. densuke_row_ids を取得（なければ編集フォームをフェッチして保存）
        ensureRowIds(urlId, urlSessions, sessionMap, base, cd, mi, cookies, pageId, errors);

        // c. 当該URLの全セッション×このプレイヤーのステータスを取得
        List<Long> urlSessionIds = urlSessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allPlayerParticipants =
                practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, urlSessionIds);
        Map<String, PracticeParticipant> bySessionAndMatch = allPlayerParticipants.stream()
                .collect(Collectors.toMap(
                        p -> p.getSessionId() + "_" + p.getMatchNumber(),
                        p -> p, (a, b) -> a));

        // d. regist フォームデータを構築（row ID のあるセッション×試合のみ）
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("id", pageId);
        formData.put("mi", mi);
        formData.put("ai", "u2");
        formData.put("membername", strippedName);
        formData.put("membercomment", "");

        // regist に含まれるセッション×試合番号を記録（dirty 解除の対象判定用）
        Set<String> writtenSessionMatchKeys = new HashSet<>();

        // 通常同期用: dirtyなキーだけ送信対象にする（未入力保護）
        Set<String> dirtyKeys = dirtyParticipants.stream()
                .map(p -> p.getSessionId() + "_" + p.getMatchNumber())
                .collect(Collectors.toSet());

        for (PracticeSession session : urlSessions) {
            for (int matchNum = 1; matchNum <= session.getTotalMatches(); matchNum++) {
                Optional<DensukeRowId> rowIdOpt = densukeRowIdRepository
                        .findByDensukeUrlIdAndSessionDateAndMatchNumber(
                                urlId, session.getSessionDate(), matchNum);
                if (rowIdOpt.isEmpty()) continue;

                String key = session.getId() + "_" + matchNum;
                PracticeParticipant pp = bySessionAndMatch.get(key);
                ParticipantStatus status = pp != null ? pp.getStatus() : null;

                // 通常同期: dirtyでないマスはスキップ（未入力保護）
                if (!lotteryConfirmation && !dirtyKeys.contains(key)) {
                    continue;
                }

                // 抽選確定時: WON/WAITLISTED/OFFERED/PENDING のみ書き戻し、それ以外は省略（伝助の既存値を維持）
                if (lotteryConfirmation && !isActiveStatus(status)) {
                    continue;
                }

                String joinKey = "join-" + rowIdOpt.get().getDensukeRowId();
                int value = toJoinValue(status);
                formData.put(joinKey, String.valueOf(value));
                writtenSessionMatchKeys.add(key);
            }
        }

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
                    saveMemberMapping(urlId, playerId, mi, memberName);
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
                saveMemberMapping(urlId, playerId, mi, memberName);
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

    private void saveMemberMapping(Long urlId, Long playerId, String mi, String playerName) {
        densukeMemberMappingRepository.save(DensukeMemberMapping.builder()
                .densukeUrlId(urlId)
                .playerId(playerId)
                .densukeMemberId(mi)
                .build());
        log.info("Mapped densuke member: player={}, mi={}", playerName, mi);
    }

    /**
     * リスト画面のヘッダー行から全メンバーの名前→mi マッピングを構築する。
     * 名前は絵文字を除去した状態で格納する。
     */
    private Map<String, String> extractAllMemberMappings(Document doc) {
        Map<String, String> result = new LinkedHashMap<>();
        Element table = doc.selectFirst("table.listtbl");
        if (table == null) return result;
        Element headerRow = table.selectFirst("tr");
        if (headerRow == null) return result;

        for (Element cell : headerRow.select("td")) {
            Element link = cell.selectFirst("a");
            if (link == null) continue;
            String name = DensukeScraper.stripLeadingEmoji(link.text().trim());
            if (name.isEmpty()) continue;

            String href = link.attr("href");
            Matcher m = MEMBERDATA_PATTERN.matcher(href);
            if (m.find()) {
                result.put(name, m.group(1));
            }
        }
        return result;
    }

    /**
     * リスト画面の hidden input name="id" からページ固有IDを取得する。
     */
    private String extractPageId(Document doc) {
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
    private void ensureRowIds(Long urlId, List<PracticeSession> sessions,
                               Map<Long, PracticeSession> sessionMap,
                               String base, String cd, String mi,
                               Map<String, String> cookies, String pageId,
                               List<String> errors) throws IOException {
        // 既にDBにある row_ids
        Set<String> existingKeys = densukeRowIdRepository.findByDensukeUrlId(urlId).stream()
                .map(r -> r.getSessionDate() + "_" + r.getMatchNumber())
                .collect(Collectors.toSet());

        // 未保存のセッション×試合があるか確認
        boolean hasNew = false;
        for (PracticeSession s : sessions) {
            for (int m = 1; m <= s.getTotalMatches(); m++) {
                if (!existingKeys.contains(s.getSessionDate() + "_" + m)) {
                    hasNew = true;
                    break;
                }
            }
            if (hasNew) break;
        }

        if (!hasNew) return;

        // 編集フォームを取得して join-{id} を抽出（Cookie・id 付き）
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
        parseAndSaveRowIds(urlId, sessions, formDoc, joinInputs);
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
    private void parseAndSaveRowIds(Long urlId, List<PracticeSession> sessions,
                                     Document formDoc, Map<String, String> joinInputs) {
        Element table = formDoc.selectFirst("table.listtbl");
        if (table == null || joinInputs.isEmpty()) {
            log.warn("Could not find listtbl or join inputs in densuke edit form");
            return;
        }

        List<Map.Entry<LocalDate, Integer>> schedule = buildScheduleOrder(sessions);
        List<String> joinIds = new ArrayList<>(joinInputs.keySet());

        if (joinIds.size() != schedule.size()) {
            log.warn("Join ID count ({}) differs from schedule count ({}), skipping row ID save to avoid misalignment",
                    joinIds.size(), schedule.size());
            return;
        }

        List<DensukeRowId> toSave = new ArrayList<>();
        for (int i = 0; i < schedule.size(); i++) {
            Map.Entry<LocalDate, Integer> entry = schedule.get(i);
            String rawJoinKey = joinIds.get(i);
            String rowId = rawJoinKey.substring("join-".length());

            Optional<DensukeRowId> existing = densukeRowIdRepository
                    .findByDensukeUrlIdAndSessionDateAndMatchNumber(
                            urlId, entry.getKey(), entry.getValue());
            if (existing.isPresent()) continue;

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
