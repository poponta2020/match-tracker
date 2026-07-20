package com.karuta.matchtracker.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 抽選の公平化ロジック（純ロジック・DB 非依存）。
 *
 * <p>選手ごとの当選日の多重集合を保持し、次の2つのカウンタを算出する。
 * <ul>
 *   <li>{@code todayTaken}:  そのセッション日に取れた試合数（{@code == sessionDate} の件数）</li>
 *   <li>{@code recentTaken}: そのセッション日から遡る30日間 {@code [sessionDate-30, sessionDate)}
 *       に取れた試合数（当日は含めない＝{@code todayTaken} 側で扱う）</li>
 * </ul>
 * ルール2（直近30日の重み付き抽選）の候補選択（{@link #pickWeighted}）もここで行う。
 *
 * <p><b>決定性:</b> {@link #pickWeighted} は候補が ID 昇順で渡される前提で、実行全体で共有する
 * 単一の {@link Random} を用いた累積和走査で1人を選ぶ。同一シードなら同一選択となり、
 * プレビューと確定の当落一致（AC-R3）を担保する。
 */
public class LotteryFairShareTracker {

    /** recentTaken の窓幅（日） */
    static final int RECENT_WINDOW_DAYS = 30;

    // playerId -> 当選日リスト（多重集合。同日複数WONは複数要素）
    private final Map<Long, List<LocalDate>> winDates = new HashMap<>();

    /** 1件の当選を記録する（sessionDate を1件追加する） */
    public void recordWin(Long playerId, LocalDate sessionDate) {
        winDates.computeIfAbsent(playerId, k -> new ArrayList<>()).add(sessionDate);
    }

    /** そのセッション日に取れた試合数（{@code == sessionDate} の件数） */
    public int todayTaken(Long playerId, LocalDate sessionDate) {
        List<LocalDate> dates = winDates.get(playerId);
        if (dates == null) {
            return 0;
        }
        int count = 0;
        for (LocalDate d : dates) {
            if (d.isEqual(sessionDate)) {
                count++;
            }
        }
        return count;
    }

    /** 窓 {@code [sessionDate-30, sessionDate)} 内の当選件数（当日は含めない） */
    public int recentTaken(Long playerId, LocalDate sessionDate) {
        List<LocalDate> dates = winDates.get(playerId);
        if (dates == null) {
            return 0;
        }
        LocalDate from = sessionDate.minusDays(RECENT_WINDOW_DAYS);
        int count = 0;
        for (LocalDate d : dates) {
            // from <= d < sessionDate
            if (!d.isBefore(from) && d.isBefore(sessionDate)) {
                count++;
            }
        }
        return count;
    }

    /**
     * ルール2: todayTaken が同点で並ぶ候補（{@code candidatePlayerIds}, ID 昇順）から、
     * 直近30日の取得数が少ない選手ほど有利な重み付き抽選で1人を選び、その playerId を返す。
     *
     * <p>重み {@code w[i] = 1 / (min(recentTaken[i], cap) + 1)}。
     * cap は候補の recentTaken の p パーセンタイル（{@link #computeCap}）。
     * 累積和走査で決定するため、候補順（ID 昇順）と rng が同じなら同じ結果になる。
     *
     * @param candidatePlayerIds 候補の playerId（ID 昇順・1人以上）
     * @param sessionDate        セッション日
     * @param capPercentile      パーセンタイル設定（0〜100）
     * @param rng                実行全体で共有する Random
     * @return 選ばれた playerId
     */
    public Long pickWeighted(List<Long> candidatePlayerIds, LocalDate sessionDate,
                             int capPercentile, Random rng) {
        int n = candidatePlayerIds.size();
        int[] recent = new int[n];
        for (int i = 0; i < n; i++) {
            recent[i] = recentTaken(candidatePlayerIds.get(i), sessionDate);
        }
        int cap = computeCap(recent, capPercentile);

        double[] weights = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            weights[i] = weightOf(recent[i], cap);
            total += weights[i];
        }

        double r = rng.nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < n; i++) {
            acc += weights[i];
            if (r < acc) {
                return candidatePlayerIds.get(i);
            }
        }
        // 浮動小数点誤差のフォールバック（末尾候補）
        return candidatePlayerIds.get(n - 1);
    }

    /**
     * 重み {@code 1 / (min(recentTaken, cap) + 1)}。recentTaken が cap 以上の候補は
     * すべて同一の重み {@code 1/(cap+1)} になる。
     */
    static double weightOf(int recentTaken, int cap) {
        return 1.0 / (Math.min(recentTaken, cap) + 1);
    }

    /**
     * §3.4 のキャップ算出。候補の recentTaken の nearest-rank パーセンタイル値を cap とする。
     * cap が候補の最小値と一致する場合は cap+1 して最小値の候補を保護する（全員フラット化の回避）。
     *
     * <pre>
     * 1. recentTaken を昇順ソート: v[0] &lt;= v[1] &lt;= ... &lt;= v[n-1]
     * 2. idx = clamp(ceil((p/100) * n) - 1, 0, n-1)
     * 3. cap = v[idx]
     * 4. cap == v[0] なら cap = v[0] + 1
     * </pre>
     *
     * @param recentTakens  候補の recentTaken（順不同でよい・1件以上）
     * @param capPercentile p（0〜100。範囲外は 0〜100 にクランプ）
     */
    static int computeCap(int[] recentTakens, int capPercentile) {
        int n = recentTakens.length;
        int[] sorted = recentTakens.clone();
        Arrays.sort(sorted); // 昇順
        int p = Math.max(0, Math.min(100, capPercentile));
        int idx = (int) Math.ceil((p / 100.0) * n) - 1;
        idx = Math.max(0, Math.min(n - 1, idx));
        int cap = sorted[idx];
        if (cap == sorted[0]) {
            cap = sorted[0] + 1;
        }
        return cap;
    }
}
