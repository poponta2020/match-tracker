package com.karuta.matchtracker.service;

import com.karuta.matchtracker.service.DensukeScraper.DensukeData;
import com.karuta.matchtracker.service.DensukeScraper.ScheduleEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 匿名化済みの合成 densuke ページスナップショット (snapshot-2026-04-24.html) に対する
 * スクレイパーの出力を検証する回帰テスト。
 *
 * Issue #521 調査: 4/23 12:51 の 3名同時キャンセル通知の原因特定において、
 * 本番スクレイパーが × (col1) を正しく absent として扱うことを確認する目的で作成。
 *
 * フィクスチャは個人情報・ページコード等を含む実ページではなく、
 * 必要なシナリオを再現する最小構成の合成 HTML を使用する。
 */
@DisplayName("DensukeScraper スナップショットテスト")
class DensukeScraperLiveSnapshotTest {

    private final DensukeScraper scraper = new DensukeScraper();

    @Test
    @DisplayName("4/23 1試合目: メンバーFが × → absent、participants にも maybeParticipants にも含まれない")
    void verifyAbsentMemberNotInMarkedListsMatch1() throws IOException {
        DensukeData data = parseSnapshot();

        ScheduleEntry match1 = data.getEntries().stream()
                .filter(e -> LocalDate.of(2026, 4, 23).equals(e.getDate()) && e.getMatchNumber() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("4/23 1試合目 が見つからない"));

        assertThat(match1.getParticipants()).doesNotContain("メンバーF");
        assertThat(match1.getMaybeParticipants()).doesNotContain("メンバーF");
    }

    @Test
    @DisplayName("4/23 2試合目: メンバーDが × → absent、participants にも maybeParticipants にも含まれない")
    void verifyAbsentMemberNotInMarkedListsMatch2() throws IOException {
        DensukeData data = parseSnapshot();

        ScheduleEntry match2 = data.getEntries().stream()
                .filter(e -> LocalDate.of(2026, 4, 23).equals(e.getDate()) && e.getMatchNumber() == 2)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("4/23 2試合目 が見つからない"));

        assertThat(match2.getParticipants()).doesNotContain("メンバーD");
        assertThat(match2.getMaybeParticipants()).doesNotContain("メンバーD");
    }

    @Test
    @DisplayName("4/23 2試合目: メンバーCが ○ → participants に含まれる")
    void verifyPresentMemberInParticipants() throws IOException {
        DensukeData data = parseSnapshot();

        ScheduleEntry match2 = data.getEntries().stream()
                .filter(e -> LocalDate.of(2026, 4, 23).equals(e.getDate()) && e.getMatchNumber() == 2)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("4/23 2試合目 が見つからない"));

        assertThat(match2.getParticipants()).contains("メンバーC");
    }

    @Test
    @DisplayName("memberNames に メンバーA〜F が含まれる（行整列の基盤確認）")
    void verifyMemberNamesContainTargets() throws IOException {
        DensukeData data = parseSnapshot();
        assertThat(data.getMemberNames())
                .contains("メンバーA", "メンバーB", "メンバーC", "メンバーD", "メンバーE", "メンバーF");
    }

    @Test
    @DisplayName("memberLastChangeTimes に各メンバーの title 属性（M/d HH:mm）がパースされて格納される (Issue #544)")
    void verifyMemberLastChangeTimesPopulated() throws IOException {
        DensukeData data = parseSnapshot();

        assertThat(data.getMemberLastChangeTimes())
                .as("Issue #521 調査対象に相当する メンバーD・メンバーF は共に title=4/23 12:51")
                .containsEntry("メンバーD", LocalDateTime.of(2026, 4, 23, 12, 51))
                .containsEntry("メンバーF", LocalDateTime.of(2026, 4, 23, 12, 51))
                .containsEntry("メンバーC", LocalDateTime.of(2026, 4, 23, 15, 28))
                .containsEntry("メンバーA", LocalDateTime.of(2026, 4, 24, 12, 5))
                .containsEntry("メンバーB", LocalDateTime.of(2026, 4, 23, 18, 7));

        // フィクスチャ上、メンバーE のみ title="" のため map に入らず、残りは全員 entry を持つ
        assertThat(data.getMemberLastChangeTimes())
                .as("title が空のメンバーは map に含まれない")
                .doesNotContainKey("メンバーE");
        assertThat(data.getMemberLastChangeTimes().size())
                .as("title がある全メンバー分 entry が作られる（全メンバー数 − 空title 1名）")
                .isEqualTo(data.getMemberNames().size() - 1);
    }

    private DensukeData parseSnapshot() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/densuke/snapshot-2026-04-24.html")) {
            if (in == null) {
                throw new IllegalStateException("snapshot-2026-04-24.html がクラスパスに無い");
            }
            Document doc = Jsoup.parse(in, "UTF-8", "https://densuke.biz/");
            return scraper.parse(doc, 2026);
        }
    }
}
