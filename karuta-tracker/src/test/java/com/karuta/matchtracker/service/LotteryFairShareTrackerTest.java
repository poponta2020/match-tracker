package com.karuta.matchtracker.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LotteryFairShareTracker 公平抽選ロジック テスト")
class LotteryFairShareTrackerTest {

    private static final LocalDate D = LocalDate.of(2026, 7, 31);

    // ---------- 窓カウント（recentTaken / todayTaken） ----------

    @Test
    @DisplayName("recentTaken は窓 [d-30, d) の件数（当日は含めない）・境界は from 含む・d 含まない")
    void recentTaken_windowBoundaries() {
        LotteryFairShareTracker t = new LotteryFairShareTracker();
        // from = D - 30 = 2026-07-01
        t.recordWin(1L, D.minusDays(30)); // == from（2026-07-01）→ 含む
        t.recordWin(1L, D.minusDays(31)); // from-1（2026-06-30）→ 含まない
        t.recordWin(1L, D.minusDays(1));  // D-1（2026-07-30）→ 含む
        t.recordWin(1L, D);               // == D → recent には含めない（today 側）

        assertThat(t.recentTaken(1L, D)).isEqualTo(2); // 07-01, 07-30
        assertThat(t.todayTaken(1L, D)).isEqualTo(1);  // 07-31
    }

    @Test
    @DisplayName("同日複数WONは todayTaken に複数計上される")
    void todayTaken_countsMultipleSameDayWins() {
        LotteryFairShareTracker t = new LotteryFairShareTracker();
        t.recordWin(1L, D);
        t.recordWin(1L, D);
        t.recordWin(1L, D);

        assertThat(t.todayTaken(1L, D)).isEqualTo(3);
        assertThat(t.recentTaken(1L, D)).isZero();
    }

    @Test
    @DisplayName("記録のない選手は recentTaken/todayTaken ともに0")
    void unknownPlayer_isZero() {
        LotteryFairShareTracker t = new LotteryFairShareTracker();
        assertThat(t.recentTaken(999L, D)).isZero();
        assertThat(t.todayTaken(999L, D)).isZero();
    }

    @Test
    @DisplayName("recentTaken は暦月境界に依存せず、月をまたいでも連続する")
    void recentTaken_spansCalendarMonthBoundary() {
        LotteryFairShareTracker t = new LotteryFairShareTracker();
        LocalDate anchor = LocalDate.of(2026, 8, 5);
        // 直近30日 [2026-07-06, 2026-08-05) には前月(7月)の勝ちも含まれる
        t.recordWin(1L, LocalDate.of(2026, 7, 20)); // 前月・窓内 → 含む
        t.recordWin(1L, LocalDate.of(2026, 7, 1));  // 前月・窓外（>30日前）→ 含まない

        assertThat(t.recentTaken(1L, anchor)).isEqualTo(1);
    }

    // ---------- キャップ算出（§3.4） ----------

    @Test
    @DisplayName("computeCap: 11人 p=30→cap=3・p=50→cap=6（nearest-rank）")
    void computeCap_elevenCandidates() {
        // sorted: 0,1,2,3,4,6,7,8,9,10,11（n=11）
        int[] recent = {0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11};
        // p=30: idx = ceil(0.30*11)-1 = 4-1 = 3 → v[3]=3
        assertThat(LotteryFairShareTracker.computeCap(recent, 30)).isEqualTo(3);
        // p=50: idx = ceil(0.50*11)-1 = 6-1 = 5 → v[5]=6
        assertThat(LotteryFairShareTracker.computeCap(recent, 50)).isEqualTo(6);
    }

    @Test
    @DisplayName("computeCap: パーセンタイルが最小値と一致する場合は cap=最小値+1（保護）")
    void computeCap_minGuard() {
        // spread あり・p=30 が最小値(0)に落ちるケース → cap は 0+1=1
        int[] recent = {0, 3, 5}; // n=3, p=30: idx=ceil(0.9)-1=0 → v[0]=0 → guard → 1
        assertThat(LotteryFairShareTracker.computeCap(recent, 30)).isEqualTo(1);
    }

    @Test
    @DisplayName("computeCap: 全員同値なら +1 ガードで cap=値+1（全員フラットのまま）")
    void computeCap_allEqual_guard() {
        int[] recent = {2, 2, 2, 2};
        // 何パーセンタイルでも v[idx]=2=min → guard → 3
        assertThat(LotteryFairShareTracker.computeCap(recent, 50)).isEqualTo(3);
    }

    @Test
    @DisplayName("computeCap: 範囲外パーセンタイルは 0〜100 にクランプ")
    void computeCap_clampsPercentile() {
        int[] recent = {0, 1, 2, 3};
        // p=0 → idx=ceil(0)-1=-1 → clamp 0 → v[0]=0 → guard → 1
        assertThat(LotteryFairShareTracker.computeCap(recent, -50)).isEqualTo(1);
        // p=100 → idx=ceil(4)-1=3 → v[3]=3
        assertThat(LotteryFairShareTracker.computeCap(recent, 200)).isEqualTo(3);
    }

    // ---------- 重み ----------

    @Test
    @DisplayName("weightOf: 1/(min(recent,cap)+1)・cap 以上は同一重み")
    void weightOf_formula() {
        int cap = 3;
        assertThat(LotteryFairShareTracker.weightOf(0, cap)).isEqualTo(1.0 / 1);
        assertThat(LotteryFairShareTracker.weightOf(2, cap)).isEqualTo(1.0 / 3);
        // recent >= cap は全員 1/(cap+1) で同一
        assertThat(LotteryFairShareTracker.weightOf(3, cap)).isEqualTo(1.0 / 4);
        assertThat(LotteryFairShareTracker.weightOf(10, cap)).isEqualTo(1.0 / 4);
    }

    // ---------- pickWeighted（決定性・確率） ----------

    @Test
    @DisplayName("pickWeighted: 同一シードなら同一選択（決定性）")
    void pickWeighted_deterministicForSameSeed() {
        LotteryFairShareTracker t = new LotteryFairShareTracker();
        // 候補にばらつきを持たせる
        t.recordWin(20L, D.minusDays(1));
        t.recordWin(20L, D.minusDays(2));
        t.recordWin(30L, D.minusDays(3));
        List<Long> candidates = List.of(10L, 20L, 30L); // ID 昇順

        for (long seed = 0; seed < 20; seed++) {
            Long a = t.pickWeighted(candidates, D, 30, new Random(seed));
            Long b = t.pickWeighted(candidates, D, 30, new Random(seed));
            assertThat(a).isEqualTo(b);
        }
    }

    @Test
    @DisplayName("pickWeighted: recentTaken が少ない選手ほど当選しやすい（重み付き）")
    void pickWeighted_favorsLowerRecentTaken() {
        LotteryFairShareTracker t = new LotteryFairShareTracker();
        // A(=10) recent=0, B(=20) recent=5
        for (int i = 1; i <= 5; i++) {
            t.recordWin(20L, D.minusDays(i));
        }
        List<Long> candidates = List.of(10L, 20L); // ID 昇順

        int aWins = 0;
        int trials = 1000;
        for (int seed = 0; seed < trials; seed++) {
            // p=100 → cap=max(=5) → A 重み 1/1、B 重み 1/6 → A が有利
            if (t.pickWeighted(candidates, D, 100, new Random(seed)) == 10L) {
                aWins++;
            }
        }
        // A の当選割合が明確に多いこと（理論値 ~6/7）
        assertThat(aWins).isGreaterThan(trials / 2);
        assertThat(aWins).isGreaterThan(trials - aWins);
    }
}
