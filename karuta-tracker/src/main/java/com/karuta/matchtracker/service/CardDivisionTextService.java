package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.util.Kimariji;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 札分け（札組）テキストのサーバー一元生成サービス。
 *
 * <p>フロントの {@code karuta-tracker-ui/src/pages/pairings/cardRules.js} の決定論生成
 * （FNV-1a 32bit → mulberry32 → 部分 Fisher-Yates → 3試合サイクル）を Java へ移植したもの。
 * 画面表示・LINE 送信の双方がこのサービスを使い、JS/Java 二重実装のドリフトを防ぐ。
 * cardRules.js は変更せず、{@code CardDivisionTextServiceTest} のゴールデン・パリティテストで
 * 同一 {@code (date, nonce, totalMatches)} に対する各試合の (種別, digits, removedCard) 一致を担保する。
 *
 * <p>32bit 符号なし演算の厳密再現に注意:
 * <ul>
 *   <li>{@code Math.imul} 相当 = Java の {@code int} 乗算（結果は 32bit にラップする）</li>
 *   <li>{@code x >>> 0} 相当 = {@code x & 0xFFFFFFFFL}（double へ渡す直前のみ）</li>
 *   <li>{@code x >>> n} 相当 = Java の {@code int} 論理右シフト {@code >>>}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CardDivisionTextService {

    private final CardRuleNonceService cardRuleNonceService;
    private final VenueRepository venueRepository;

    /** 全角スペース（U+3000）。抜き札行の意図的な区切り（cardRules.js の description と同一）。 */
    private static final char FULL_WIDTH_SPACE = '　';

    /** 札番号 "01".."99","00"(=100) の順序付き100枚（cardRules.js の ALL_CARDS と同一順）。 */
    private static final String[] ALL_CARDS = buildAllCards();

    private static String[] buildAllCards() {
        String[] cards = new String[100];
        for (int i = 0; i < 100; i++) {
            cards[i] = String.format("%02d", (i + 1) % 100);
        }
        return cards;
    }

    /** 1試合分の札ルール。パリティ検証の対象（種別・digits・removedCard）。 */
    public record CardRule(String type, List<Integer> digits, String removedCard) {}

    // ------------------------------------------------------------------
    // 決定論 PRNG（cardRules.js 移植）
    // ------------------------------------------------------------------

    /**
     * 文字列 {@code date#nonce} を 32bit 整数へハッシュする（FNV-1a 32bit）。
     * 返り値の <b>ビットパターン</b>が cardRules.js の {@code hashSeed(...) >>> 0} と一致する
     * （mulberry32 は {@code seed >>> 0} を状態に使うため、符号は問わずビットが一致すればよい）。
     */
    static int hashSeed(String date, int nonce) {
        String str = date + "#" + nonce;
        int h = 0x811c9dc5; // FNV-1a 32bit offset basis (2166136261)
        for (int i = 0; i < str.length(); i++) {
            h ^= str.charAt(i);   // charAt = UTF-16 code unit（JS charCodeAt と同一。日付/nonce は ASCII）
            h *= 0x01000193;      // FNV prime。int 乗算は Math.imul と同じく 32bit ラップ
        }
        return h;
    }

    /** 決定論的擬似乱数生成器（mulberry32）。同一シードなら同一の数列。 */
    private static final class Mulberry32 {
        private int a;

        Mulberry32(int seed) {
            this.a = seed; // JS: a = seed >>> 0（ビットパターンは int と同一）
        }

        double next() {
            a = a + 0x6d2b79f5;                       // (a + k) | 0
            int t = (a ^ (a >>> 15)) * (1 | a);        // Math.imul(a ^ (a>>>15), 1|a)
            t = ((t + (t ^ (t >>> 7)) * (61 | t)) ^ t);// (t + Math.imul(t^(t>>>7), 61|t)) ^ t
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    /** 配列から n 個を選ぶ（部分 Fisher-Yates）。cardRules.js の pickRandom と同じ乱数消費順。 */
    private static List<Integer> pickRandom(List<Integer> arr, int n, Mulberry32 rng) {
        List<Integer> a = new ArrayList<>(arr);
        int count = Math.min(n, a.size());
        for (int i = 0; i < count; i++) {
            int j = i + (int) Math.floor(rng.next() * (a.size() - i));
            Collections.swap(a, i, j);
        }
        return new ArrayList<>(a.subList(0, count));
    }

    private static int onesDigit(String card) {
        return card.charAt(1) - '0';
    }

    private static int tensDigit(String card) {
        return card.charAt(0) - '0';
    }

    private static List<Integer> sortedAsc(List<Integer> digits) {
        List<Integer> copy = new ArrayList<>(digits);
        Collections.sort(copy);
        return copy;
    }

    /**
     * 札ルール生成（3試合サイクル）。cardRules.js の generateCardRules と同一アルゴリズム。
     * 各試合が消費する乱数の本数・順序は試合番号のみで決まり totalMatches に依存しない。
     */
    public List<CardRule> generateCardRules(String isoDate, int nonce, int totalMatches) {
        Mulberry32 rng = new Mulberry32(hashSeed(isoDate, nonce));
        List<CardRule> rules = new ArrayList<>();
        List<Integer> prevUnusedDigits = null;
        List<Integer> prevUsedDigits = null;
        final List<Integer> allDigits = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

        for (int i = 0; i < totalMatches; i++) {
            int cyclePos = i % 3;

            if (cyclePos == 0) {
                List<Integer> chosen = sortedAsc(pickRandom(allDigits, 5, rng));
                List<Integer> unused = new ArrayList<>();
                for (int d : allDigits) if (!chosen.contains(d)) unused.add(d);
                prevUnusedDigits = unused;
                prevUsedDigits = chosen;
                rules.add(new CardRule("ones", chosen, null));

            } else if (cyclePos == 1) {
                List<Integer> source = prevUnusedDigits != null ? prevUnusedDigits : allDigits;
                List<Integer> chosen = sortedAsc(pickRandom(source, 3, rng));

                List<String> matchingCards = new ArrayList<>();
                for (String card : ALL_CARDS) {
                    if (chosen.contains(onesDigit(card)) || chosen.contains(tensDigit(card))) {
                        matchingCards.add(card);
                    }
                }
                String removedCard = matchingCards.get((int) Math.floor(rng.next() * matchingCards.size()));

                prevUsedDigits = chosen;
                List<Integer> nextUnused = new ArrayList<>();
                for (int d : allDigits) if (!chosen.contains(d)) nextUnused.add(d);
                prevUnusedDigits = nextUnused;

                rules.add(new CardRule("nuki", chosen, removedCard));

            } else {
                List<Integer> source = new ArrayList<>();
                for (int d : allDigits) if (!prevUsedDigits.contains(d)) source.add(d);
                List<Integer> chosen = sortedAsc(pickRandom(source, 5, rng));
                List<Integer> unused = new ArrayList<>();
                for (int d : source) if (!chosen.contains(d)) unused.add(d);
                prevUnusedDigits = unused;
                prevUsedDigits = chosen;
                rules.add(new CardRule("tens", chosen, null));
            }
        }
        return rules;
    }

    // ------------------------------------------------------------------
    // テキスト整形
    // ------------------------------------------------------------------

    /**
     * 札分けテキストを組み立てる。
     * <pre>
     * 【M/D 会場名】
     * 1試合目：一の位1.3.5.6.7
     * 2試合目：1.4.5　41(こひ)抜き
     * 3試合目：十の位2.4.6.8.9
     * </pre>
     * ヘッダの月・日は10の位が0のとき省略（7/5・10/9・12/25）。会場名が空なら {@code 【M/D】}。
     *
     * @param venueName 会場名（null/空なら省略）
     */
    public String buildText(LocalDate date, String venueName, int totalMatches, int nonce) {
        List<CardRule> rules = generateCardRules(date.toString(), nonce, totalMatches);

        StringBuilder sb = new StringBuilder();
        sb.append('【').append(date.getMonthValue()).append('/').append(date.getDayOfMonth());
        if (venueName != null && !venueName.isBlank()) {
            sb.append(' ').append(venueName);
        }
        sb.append('】');

        for (int i = 0; i < rules.size(); i++) {
            sb.append('\n').append(i + 1).append("試合目：").append(renderRule(rules.get(i)));
        }
        return sb.toString();
    }

    /** 札ルール1つを表示文言に変換する。抜き行のみ決まり字を {@code 番号(決まり字)抜き} で付与する。 */
    private String renderRule(CardRule rule) {
        String joined = joinDigits(rule.digits());
        return switch (rule.type()) {
            case "ones" -> "一の位" + joined;
            case "tens" -> "十の位" + joined;
            case "nuki" -> {
                int num = Integer.parseInt(rule.removedCard());
                if (num == 0) num = 100; // "00" → 100（parseInt(removedCard)||100 相当）
                yield joined + FULL_WIDTH_SPACE + num + "(" + Kimariji.of(num) + ")抜き";
            }
            default -> joined;
        };
    }

    private static String joinDigits(List<Integer> digits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.size(); i++) {
            if (i > 0) sb.append('.');
            sb.append(digits.get(i));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // セッション連携（API・スケジューラ共通）
    // ------------------------------------------------------------------

    /**
     * 練習セッションから札分けテキストを生成する（会場名・nonce・試合数を解決）。
     * 画面 API（Task 2）とスケジューラ（Task 3）の共通経路。
     */
    public String buildTextForSession(PracticeSession session) {
        LocalDate date = session.getSessionDate();
        int nonce = cardRuleNonceService.getNonce(date);
        int totalMatches = session.getTotalMatches() != null ? session.getTotalMatches() : 3;
        String venueName = resolveVenueName(session.getVenueId());
        return buildText(date, venueName, totalMatches, nonce);
    }

    private String resolveVenueName(Long venueId) {
        if (venueId == null) return null;
        return venueRepository.findById(venueId).map(Venue::getName).orElse(null);
    }
}
