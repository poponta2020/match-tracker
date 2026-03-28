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
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * アプリ→伝助への書き込みサービス
 *
 * dirty=true の参加者を対象に、伝助へ出欠を書き込む。
 * 書き込み成功後に dirty=false に更新する。
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

    // メモリ上の状態（スケジューラーから参照）
    // 注意: シングルインスタンス運用を前提としている。
    // スケールアウト（複数インスタンス）時は各インスタンスが独立した状態を持ち、
    // フロントエンドに表示される書き込み状況が不正確になる。
    // その場合は DB または Redis へ永続化する必要がある。
    private volatile LocalDateTime lastAttemptAt;
    private volatile LocalDateTime lastSuccessAt;
    private volatile List<String> lastErrors = new ArrayList<>();
    private volatile int lastPendingCount = 0;

    public DensukeWriteStatusDto getStatus() {
        return DensukeWriteStatusDto.builder()
                .lastAttemptAt(lastAttemptAt)
                .lastSuccessAt(lastSuccessAt)
                .errors(new ArrayList<>(lastErrors))
                .pendingCount(lastPendingCount)
                .build();
    }

    /**
     * dirty=true の参加者を伝助に書き込む（スケジューラーから呼ばれる）
     */
    @Transactional
    public void writeToDensuke() {
        lastAttemptAt = JstDateTimeUtil.now();
        List<String> errors = new ArrayList<>();

        // 当月・翌月の DensukeUrl を取得
        LocalDate now = JstDateTimeUtil.today();
        List<DensukeUrl> urls = new ArrayList<>();
        densukeUrlRepository.findByYearAndMonth(now.getYear(), now.getMonthValue()).ifPresent(urls::add);
        LocalDate next = now.plusMonths(1);
        densukeUrlRepository.findByYearAndMonth(next.getYear(), next.getMonthValue()).ifPresent(urls::add);

        if (urls.isEmpty()) {
            lastPendingCount = 0;
            lastErrors = errors;
            return;
        }

        // URL ごとに、その月のセッションを取得
        Map<Long, DensukeUrl> urlById = urls.stream()
                .collect(Collectors.toMap(DensukeUrl::getId, u -> u));
        Map<Long, List<PracticeSession>> sessionsByUrlId = new LinkedHashMap<>();
        for (DensukeUrl url : urls) {
            List<PracticeSession> sessions = practiceSessionRepository
                    .findByYearAndMonth(url.getYear(), url.getMonth());
            sessionsByUrlId.put(url.getId(), sessions);
        }

        // 対象セッション全体の ID セット
        List<Long> allSessionIds = sessionsByUrlId.values().stream()
                .flatMap(List::stream)
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        if (allSessionIds.isEmpty()) {
            lastPendingCount = 0;
            lastErrors = errors;
            return;
        }

        // dirty=true の参加者を取得
        List<PracticeParticipant> dirtyParticipants =
                practiceParticipantRepository.findDirtyBySessionIds(allSessionIds);
        lastPendingCount = dirtyParticipants.size();

        if (dirtyParticipants.isEmpty()) {
            lastErrors = errors;
            if (errors.isEmpty()) lastSuccessAt = JstDateTimeUtil.now();
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
        // Map<urlId, Map<playerId, List<PracticeParticipant>>>
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

        // 各（URL, プレイヤー）の書き込み
        for (var urlEntry : grouped.entrySet()) {
            Long urlId = urlEntry.getKey();
            DensukeUrl densukeUrl = urlById.get(urlId);
            List<PracticeSession> urlSessions = sessionsByUrlId.get(urlId);
            String urlStr = densukeUrl.getUrl();
            String cd = extractCd(urlStr);
            String base = extractBase(urlStr);
            if (cd == null || base == null) {
                errors.add("URL解析エラー: " + urlStr);
                continue;
            }

            for (var playerEntry : urlEntry.getValue().entrySet()) {
                Long playerId = playerEntry.getKey();
                List<PracticeParticipant> participants = playerEntry.getValue();
                String playerName = playerNames.getOrDefault(playerId, "ID=" + playerId);

                try {
                    writePlayerToDensuke(urlId, playerId, playerName,
                            participants, urlSessions, sessionMap, base, cd, errors);
                } catch (Exception e) {
                    log.warn("Failed to write player {} to densuke {}: {}", playerName, urlStr, e.getMessage());
                    errors.add("選手[" + playerName + "]: " + e.getMessage());
                }
            }
        }

        lastErrors = errors;
        if (errors.isEmpty()) lastSuccessAt = JstDateTimeUtil.now();
    }

    private void writePlayerToDensuke(
            Long urlId, Long playerId, String playerName,
            List<PracticeParticipant> dirtyParticipants,
            List<PracticeSession> urlSessions,
            Map<Long, PracticeSession> sessionMap,
            String base, String cd,
            List<String> errors) throws IOException {

        // a. densuke_member_id を取得（なければ INSERT）
        String mi = densukeMemberMappingRepository
                .findByDensukeUrlIdAndPlayerId(urlId, playerId)
                .map(DensukeMemberMapping::getDensukeMemberId)
                .orElseGet(() -> insertMember(urlId, playerId, playerName, base, cd, errors));

        if (mi == null) {
            errors.add("選手[" + playerName + "]: メンバーIDの取得に失敗");
            return;
        }

        // b. densuke_row_ids を取得（なければ編集フォームをフェッチして保存）
        ensureRowIds(urlId, urlSessions, sessionMap, base, cd, mi, errors);

        // c. 当該URLの全セッション×このプレイヤーのステータスを取得
        List<Long> urlSessionIds = urlSessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allPlayerParticipants =
                practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, urlSessionIds);
        Map<String, PracticeParticipant> bySessionAndMatch = allPlayerParticipants.stream()
                .collect(Collectors.toMap(
                        p -> p.getSessionId() + "_" + p.getMatchNumber(),
                        p -> p, (a, b) -> a));

        // d. join-{id} ごとに値を決定して POST regist
        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("mi", mi);

        for (PracticeSession session : urlSessions) {
            for (int matchNum = 1; matchNum <= session.getTotalMatches(); matchNum++) {
                Optional<DensukeRowId> rowIdOpt = densukeRowIdRepository
                        .findByDensukeUrlIdAndSessionDateAndMatchNumber(
                                urlId, session.getSessionDate(), matchNum);
                if (rowIdOpt.isEmpty()) continue;

                String joinKey = "join-" + rowIdOpt.get().getDensukeRowId();
                String key = session.getId() + "_" + matchNum;
                PracticeParticipant pp = bySessionAndMatch.get(key);
                int value = pp != null ? toJoinValue(pp.getStatus()) : 0;
                formData.put(joinKey, String.valueOf(value));
            }
        }

        if (formData.size() <= 1) {
            // join-{id} が一つも見つからない場合はスキップ
            log.debug("No row IDs found for player {} / urlId {}, skipping regist", playerName, urlId);
            return;
        }

        // e. POST regist
        Connection.Response response = Jsoup.connect(base + "regist?cd=" + cd)
                .data(formData)
                .method(Connection.Method.POST)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .execute();

        if (response.statusCode() >= 400) {
            errors.add("選手[" + playerName + "]: regist HTTP " + response.statusCode());
            return;
        }

        // f. 成功 → dirty=false に更新
        for (PracticeParticipant p : dirtyParticipants) {
            p.setDirty(false);
        }
        practiceParticipantRepository.saveAll(dirtyParticipants);
        log.info("Written to densuke: player={}, urlId={}, {} entries", playerName, urlId, dirtyParticipants.size());
    }

    /**
     * 伝助に新規メンバーを追加し、mi を返す。
     * 追加後、リスト画面から mi を探して densuke_member_mappings に保存する。
     */
    private String insertMember(Long urlId, Long playerId, String playerName,
                                 String base, String cd, List<String> errors) {
        try {
            // POST insert
            Jsoup.connect(base + "insert?cd=" + cd)
                    .data("name", playerName)
                    .method(Connection.Method.POST)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .execute();

            // INSERT 後、リスト画面から mi を探す
            Document listDoc = Jsoup.connect(base + "list?cd=" + cd)
                    .userAgent("Mozilla/5.0")
                    .timeout(10000)
                    .get();

            String mi = findMiByName(listDoc, playerName);
            if (mi == null) {
                log.warn("Could not find mi for player {} after insert", playerName);
                return null;
            }

            // densuke_member_mappings に保存
            densukeMemberMappingRepository.save(DensukeMemberMapping.builder()
                    .densukeUrlId(urlId)
                    .playerId(playerId)
                    .densukeMemberId(mi)
                    .build());
            log.info("Inserted densuke member: player={}, mi={}", playerName, mi);
            return mi;

        } catch (IOException e) {
            log.warn("Failed to insert densuke member {}: {}", playerName, e.getMessage());
            errors.add("選手[" + playerName + "]: メンバー追加失敗 - " + e.getMessage());
            return null;
        }
    }

    /**
     * リスト画面の ヘッダー行から playerName に対応する mi を探す。
     */
    private String findMiByName(Document doc, String playerName) {
        Element table = doc.selectFirst("table.listtbl");
        if (table == null) return null;
        Element headerRow = table.selectFirst("tr");
        if (headerRow == null) return null;

        for (Element cell : headerRow.select("td")) {
            Element link = cell.selectFirst("a");
            if (link == null) continue;
            String name = DensukeScraper.stripLeadingEmoji(link.text().trim());
            if (playerName.equals(name)) {
                String href = link.attr("href");
                return extractQueryParam(href, "mi");
            }
        }
        return null;
    }

    /**
     * 各セッション×試合の join-{id} を確認し、未保存のものを取得して保存する。
     */
    private void ensureRowIds(Long urlId, List<PracticeSession> sessions,
                               Map<Long, PracticeSession> sessionMap,
                               String base, String cd, String mi, List<String> errors) throws IOException {
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

        // 編集フォームを取得して join-{id} を抽出
        Document formDoc = Jsoup.connect(base + "list?cd=" + cd)
                .data("mi", mi)
                .method(Connection.Method.POST)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .execute()
                .parse();

        // join-XXXXXX という name を持つ input/select を探す
        // 伝助のフォームは input[name^=join-] または hidden field で行IDを持つ
        Map<String, String> joinInputs = extractJoinInputs(formDoc);

        // 日付×試合番号と join-{id} の対応を推測する
        // フォームの行構造を解析して session_date + match_number → join-id を取得
        parseAndSaveRowIds(urlId, sessions, formDoc, joinInputs);
    }

    /**
     * 編集フォームから join-{id} のマップを抽出する。
     * key: フィールド名 ("join-XXXXX")、value: 現在の値
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
        // join-{id} は伝助内部のID。フォームのテーブル行を順番に走査し、
        // join-{id} の出現順を日程×試合番号の順序と対応させる。
        Element table = formDoc.selectFirst("table.listtbl");
        if (table == null || joinInputs.isEmpty()) {
            log.warn("Could not find listtbl or join inputs in densuke edit form");
            return;
        }

        // 日程×試合番号リスト（DensukeScraper と同じ順序）を構築
        List<Map.Entry<LocalDate, Integer>> schedule = buildScheduleOrder(sessions);

        // join-{id} のリスト（出現順）
        List<String> joinIds = new ArrayList<>(joinInputs.keySet());

        // 日程×試合番号の数と join-{id} の数が合う場合に対応付け
        // 1人のプレイヤーの編集フォームには全日程分の join-{id} が含まれる
        if (joinIds.size() < schedule.size()) {
            log.warn("Join ID count ({}) less than schedule count ({}), skipping row ID save",
                    joinIds.size(), schedule.size());
            return;
        }

        List<DensukeRowId> toSave = new ArrayList<>();
        for (int i = 0; i < schedule.size(); i++) {
            Map.Entry<LocalDate, Integer> entry = schedule.get(i);
            String rawJoinKey = joinIds.get(i); // "join-XXXXX"
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

    /**
     * セッションリストから日付×試合番号の順序付きリストを構築する（DensukeScraper の解析順に合わせる）。
     */
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
     * ParticipantStatus → 伝助の join 値に変換する。
     */
    private int toJoinValue(ParticipantStatus status) {
        if (status == null) return 0;
        return switch (status) {
            case PENDING, WON -> 3;       // ○
            case WAITLISTED, OFFERED -> 2; // △
            case CANCELLED, DECLINED, WAITLIST_DECLINED -> 1; // ×
        };
    }

    /**
     * URL から cd パラメータを抽出する。
     * 例: "https://densuke.biz/list?cd=XXXXXXXXXX" → "XXXXXXXXXX"
     */
    static String extractCd(String url) {
        return extractQueryParam(url, "cd");
    }

    /**
     * URL のベース部分を抽出する。
     * 例: "https://densuke.biz/list?cd=XXXXXXXXXX" → "https://densuke.biz/"
     */
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
