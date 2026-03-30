package com.karuta.matchtracker.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
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

        Element table = doc.selectFirst("table.listtbl");
        if (table == null) {
            throw new IOException("伝助のテーブルが見つかりません");
        }

        Elements rows = table.select("tr");
        if (rows.isEmpty()) {
            throw new IOException("テーブルにデータがありません");
        }

        // 1行目: ヘッダー行 — 列4以降が参加者名
        Element headerRow = rows.get(0);
        Elements headerCells = headerRow.select("td");
        List<String> memberNames = new ArrayList<>();
        for (int i = 4; i < headerCells.size(); i++) {
            Element cell = headerCells.get(i);
            Element link = cell.selectFirst("a");
            String name = stripLeadingEmoji(link != null ? link.text() : cell.text());
            memberNames.add(name);
        }
        log.info("Found {} members in densuke", memberNames.size());

        // 2行目以降: 日程データ
        DensukeData data = new DensukeData();
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

            // 列4以降が各参加者の出欠データ
            ScheduleEntry entry = new ScheduleEntry();
            entry.setDate(currentDate);
            entry.setMatchNumber(matchNumber);
            entry.setVenueName(currentVenue);
            entry.setRawLabel(label);

            for (int colIdx = 4; colIdx < cells.size(); colIdx++) {
                int memberIdx = colIdx - 4;
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
     * 名前の先頭に付いている絵文字（Symbol カテゴリの文字）を除去する。
     * 例: "🔰田中" → "田中", "🌟鈴木" → "鈴木"
     */
    static String stripLeadingEmoji(String name) {
        if (name == null || name.isEmpty()) return name;
        int i = 0;
        while (i < name.length()) {
            int codePoint = name.codePointAt(i);
            int type = Character.getType(codePoint);
            if (type == Character.OTHER_SYMBOL
                    || type == Character.MATH_SYMBOL
                    || type == Character.MODIFIER_SYMBOL) {
                i += Character.charCount(codePoint);
            } else {
                break;
            }
        }
        return name.substring(i).trim();
    }
}
