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
 * 2026-04-24 に取得した densuke 実ページスナップショット (snapshot-2026-04-24.html) に対する
 * スクレイパーの出力を検証する回帰テスト。
 *
 * Issue #521 調査: 4/23 12:51 の 3名同時キャンセル通知の原因特定において、
 * 本番スクレイパーが × (col1) を正しく absent として扱うことを確認する目的で作成。
 */
@DisplayName("DensukeScraper 実ページスナップショットテスト")
class DensukeScraperLiveSnapshotTest {

    private final DensukeScraper scraper = new DensukeScraper();

    @Test
    @DisplayName("2026-04-23 1試合目: 鮎川知佳が × → absent、participants にも maybeParticipants にも含まれない")
    void verifyAyukawaNotInMarkedLists() throws IOException {
        DensukeData data = parseSnapshot();

        ScheduleEntry match1 = data.getEntries().stream()
                .filter(e -> LocalDate.of(2026, 4, 23).equals(e.getDate()) && e.getMatchNumber() == 1)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("4/23 1試合目 が見つからない"));

        assertThat(match1.getParticipants()).doesNotContain("鮎川知佳");
        assertThat(match1.getMaybeParticipants()).doesNotContain("鮎川知佳");
    }

    @Test
    @DisplayName("2026-04-23 2試合目: 深井世奈が × → absent、participants にも maybeParticipants にも含まれない")
    void verifyFukaiNotInMarkedLists() throws IOException {
        DensukeData data = parseSnapshot();

        ScheduleEntry match2 = data.getEntries().stream()
                .filter(e -> LocalDate.of(2026, 4, 23).equals(e.getDate()) && e.getMatchNumber() == 2)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("4/23 2試合目 が見つからない"));

        assertThat(match2.getParticipants()).doesNotContain("深井世奈");
        assertThat(match2.getMaybeParticipants()).doesNotContain("深井世奈");
    }

    @Test
    @DisplayName("2026-04-23 2試合目: 森保滉大が ○ → participants に含まれる（再参加後の現状）")
    void verifyMorihoInParticipants() throws IOException {
        DensukeData data = parseSnapshot();

        ScheduleEntry match2 = data.getEntries().stream()
                .filter(e -> LocalDate.of(2026, 4, 23).equals(e.getDate()) && e.getMatchNumber() == 2)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("4/23 2試合目 が見つからない"));

        assertThat(match2.getParticipants()).contains("森保滉大");
    }

    @Test
    @DisplayName("memberNames に 鮎川知佳・深井世奈・森保滉大 が含まれる（行整列の基盤確認）")
    void verifyMemberNamesContainTargets() throws IOException {
        DensukeData data = parseSnapshot();
        assertThat(data.getMemberNames()).contains("鮎川知佳", "深井世奈", "森保滉大");
    }

    @Test
    @DisplayName("memberLastChangeTimes に各メンバーの title 属性（M/d HH:mm）がパースされて格納される (Issue #544)")
    void verifyMemberLastChangeTimesPopulated() throws IOException {
        DensukeData data = parseSnapshot();

        assertThat(data.getMemberLastChangeTimes())
                .as("Issue #521 調査対象の 鮎川知佳・深井世奈 は共に title=4/23 12:51")
                .containsEntry("鮎川知佳", LocalDateTime.of(2026, 4, 23, 12, 51))
                .containsEntry("深井世奈", LocalDateTime.of(2026, 4, 23, 12, 51))
                .containsEntry("森保滉大", LocalDateTime.of(2026, 4, 23, 15, 28))
                .containsEntry("高橋叶愛", LocalDateTime.of(2026, 4, 24, 12, 5))
                .containsEntry("佐藤彩乃", LocalDateTime.of(2026, 4, 23, 18, 7));

        // スナップショット上、1名（中野大空）だけ title="" のため map に入らず、残りは全員 entry を持つ
        assertThat(data.getMemberLastChangeTimes())
                .as("title が空のメンバーは map に含まれない")
                .doesNotContainKey("中野大空");
        assertThat(data.getMemberLastChangeTimes().size())
                .as("title がある全メンバー分 entry が作られる（53件 = 全54名 − 空title 1名）")
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
