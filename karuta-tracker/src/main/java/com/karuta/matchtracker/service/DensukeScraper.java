package com.karuta.matchtracker.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DensukeScraper {

    /**
     * 伝助のスクレイピング結果
     */
    @Data
    public static class DensukeData {
        private List<ScheduleEntry> entries = new ArrayList<>();
        private List<String> memberNames = new ArrayList<>();
        /**
         * 伝助ヘッダの各メンバーリンクの title 属性（"M/d HH:mm"）を
         * LocalDateTime（scrape 年固定）に変換したもの。
         * title が空 or parse 不可なメンバーは entry を持たない（Issue #544）。
         */
        private Map<String, LocalDateTime> memberLastChangeTimes = new LinkedHashMap<>();
    }

    /**
     * 1つの日程エントリ（日付 × 試合番号 × 参加者リスト）
     */
    @Data
    public static class ScheduleEntry {
        private LocalDate date;
        private int matchNumber;
        private String venueName; // 会場名（ラベルから抽出）
        private String rawLabel; // 元のラベル（デバッグ用）
        private List<String> participants = new ArrayList<>();  // ○の参加者名
        private List<String> maybeParticipants = new ArrayList<>(); // △の参加者名
    }

    // 日付パターン: "3/3(火)" or "3/14(金)"
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})\\([^)]+\\)");
    // 試合番号パターン: "1試合目" or "2試合目"
    private static final Pattern MATCH_PATTERN = Pattern.compile("(\\d+)試合目");
    // 伝助 title 属性: "M/d HH:mm" 例: "4/23 18:07"
    private static final Pattern DENSUKE_TITLE_PATTERN = Pattern.compile("^\\s*(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{2})\\s*$");
    // 会場名パターン: 日付の後、試合番号or時間の前の文字列 例: "4/1(水)すずらん 1試合目 17:20~"
    private static final Pattern VENUE_PATTERN = Pattern.compile("\\([^)]+\\)(.+?)[\\s\u3000]+\\d+試合目");

    /**
     * 伝助URLからデータをスクレイピング
     */
    public DensukeData scrape(String url, int year) throws IOException {
        log.info("Scraping densuke URL: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();

        return parse(doc, year);
    }

    /**
     * Jsoup Document から伝助データをパースする。ネットワーク I/O を持たないのでユニットテスト可能。
     */
    DensukeData parse(Document doc, int year) throws IOException {
        Element table = doc.selectFirst("table.listtbl");
        if (table == null) {
            throw new IOException("伝助のテーブルが見つかりません");
        }

        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            throw new IOException("テーブルにデータがありません");
        }

        // 1行目: ヘッダー行。先頭の凡例列数はページ設定 (○/△/× の3択 or ○/× の2択) で変わるため、
        // 「memberdata アンカーを持つ最初のセル」を動的に検出してメンバー列の開始位置を求める。
        Element headerRow = rows.get(0);
        Elements headerCells = headerRow.select("td");
        int memberStartIdx = findMemberStartIndex(headerCells);
        if (memberStartIdx < 0) {
            throw new IOException("伝助のメンバー列が見つかりません");
        }

        List<String> memberNames = new ArrayList<>();
        Map<String, LocalDateTime> memberLastChangeTimes = new LinkedHashMap<>();
        for (int i = memberStartIdx; i < headerCells.size(); i++) {
            Element cell = headerCells.get(i);
            Element link = cell.selectFirst("a");
            String name = stripLeadingEmoji(link != null ? link.text() : cell.text());
            memberNames.add(name);

            if (link != null) {
                LocalDateTime titleTime = parseDensukeTitleAsDateTime(link.attr("title"), year);
                if (titleTime != null) {
                    memberLastChangeTimes.put(name, titleTime);
                }
            }
        }
        log.info("Found {} members in densuke (memberStartIdx={}, withTitleTime={})",
                memberNames.size(), memberStartIdx, memberLastChangeTimes.size());

        // 2行目以降: 日程データ
        DensukeData data = new DensukeData();
        data.setMemberNames(memberNames);
        data.setMemberLastChangeTimes(memberLastChangeTimes);
        LocalDate currentDate = null;
        String currentVenue = null;

        for (int rowIdx = 1; rowIdx < rows.size(); rowIdx++) {
            Element row = rows.get(rowIdx);
            Elements cells = row.select("td");
            if (cells.isEmpty()) continue;

            String label = cells.get(0).text().trim();
            if (label.isEmpty()) continue;

            // 日付を抽出
            Matcher dateMatcher = DATE_PATTERN.matcher(label);
            if (dateMatcher.find()) {
                int month = Integer.parseInt(dateMatcher.group(1));
                int day = Integer.parseInt(dateMatcher.group(2));
                currentDate = LocalDate.of(year, month, day);

                // 会場名を抽出（日付行のみに含まれる）
                Matcher venueMatcher = VENUE_PATTERN.matcher(label);
                if (venueMatcher.find()) {
                    currentVenue = venueMatcher.group(1).trim();
                }
            }

            if (currentDate == null) continue;

            // 試合番号を抽出
            Matcher matchMatcher = MATCH_PATTERN.matcher(label);
            int matchNumber = 1;
            if (matchMatcher.find()) {
                matchNumber = Integer.parseInt(matchMatcher.group(1));
            }

            // memberStartIdx 以降が各参加者の出欠データ
            ScheduleEntry entry = new ScheduleEntry();
            entry.setDate(currentDate);
            entry.setMatchNumber(matchNumber);
            entry.setVenueName(currentVenue);
            entry.setRawLabel(label);

            for (int colIdx = memberStartIdx; colIdx < cells.size(); colIdx++) {
                int memberIdx = colIdx - memberStartIdx;
                if (memberIdx >= memberNames.size()) break;

                Element cell = cells.get(colIdx);
                Element div = cell.selectFirst("div[class^=col]");
                if (div == null) continue;

                String className = div.className();
                if ("col3".equals(className)) {
                    // ○ = 参加
                    entry.getParticipants().add(memberNames.get(memberIdx));
                } else if ("col2".equals(className) && "△".equals(div.text().trim())) {
                    // △ = 未定
                    entry.getMaybeParticipants().add(memberNames.get(memberIdx));
                }
            }

            data.getEntries().add(entry);
            log.debug("Parsed: {} match{} - {} participants, {} maybe",
                    currentDate, matchNumber, entry.getParticipants().size(), entry.getMaybeParticipants().size());
        }

        log.info("Scraped {} schedule entries from densuke", data.getEntries().size());
        return data;
    }

    /**
     * 伝助ヘッダの `<a title="M/d HH:mm">` 属性を LocalDateTime に変換する。
     * 年は呼び出し元（scrape 年）を採用する（年跨ぎは今回のスコープ外）。
     *
     * @return parse 成功時 LocalDateTime、title が null/空/フォーマット不一致/日付不正なら null
     */
    static LocalDateTime parseDensukeTitleAsDateTime(String title, int year) {
        if (title == null) return null;
        Matcher m = DENSUKE_TITLE_PATTERN.matcher(title);
        if (!m.matches()) return null;
        try {
            int month = Integer.parseInt(m.group(1));
            int day = Integer.parseInt(m.group(2));
            int hour = Integer.parseInt(m.group(3));
            int minute = Integer.parseInt(m.group(4));
            return LocalDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute));
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    /**
     * ヘッダー行セルから、最初の「memberdata アンカー（参加者名リンク）」を含むセルの index を返す。
     * 見つからなければ -1。
     */
    static int findMemberStartIndex(Elements headerCells) {
        for (int i = 0; i < headerCells.size(); i++) {
            if (headerCells.get(i).selectFirst("a[href*=memberdata]") != null) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 名前の先頭に付いている絵文字（Symbol カテゴリの文字）を除去し、
     * 不可視のUnicode制御文字（Variation Selector等）を全体から除去する。
     * 例: "🔰田中" → "田中", "🌟鈴木" → "鈴木", "︎井桁" → "井桁"
     */
    static String stripLeadingEmoji(String name) {
        if (name == null || name.isEmpty()) return name;

        // 全体からVariation Selector (U+FE00–U+FE0F, U+E0100–U+E01EF) および
        // その他の不可視FORMAT文字（ゼロ幅スペース等）を除去
        StringBuilder sb = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> {
            if (cp >= 0xFE00 && cp <= 0xFE0F) return;          // Variation Selectors
            if (cp >= 0xE0100 && cp <= 0xE01EF) return;        // Variation Selectors Supplement
            if (cp == 0x200B || cp == 0x200C || cp == 0x200D    // Zero-width chars
                    || cp == 0xFEFF || cp == 0x2060) return;    // BOM, Word Joiner
            sb.appendCodePoint(cp);
        });
        String cleaned = sb.toString();

        // 先頭の絵文字（Symbolカテゴリ）を除去
        int i = 0;
        while (i < cleaned.length()) {
            int codePoint = cleaned.codePointAt(i);
            int type = Character.getType(codePoint);
            if (type == Character.OTHER_SYMBOL
                    || type == Character.MATH_SYMBOL
                    || type == Character.MODIFIER_SYMBOL) {
                i += Character.charCount(codePoint);
            } else {
                break;
            }
        }
        return cleaned.substring(i).trim();
    }
}
