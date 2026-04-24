package com.karuta.matchtracker.service;

import com.karuta.matchtracker.service.DensukeScraper.DensukeData;
import com.karuta.matchtracker.service.DensukeScraper.ScheduleEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DensukeScraperの単体テスト
 *
 * 注意: 実際の伝助URLへの接続テストはせず、パースロジックのみテストする。
 * DensukeScraper.scrape() はJsoup.connect()を直接使用するため、
 * HTMLパースの正確性はScheduleEntryのデータモデルテストで担保する。
 */
@DisplayName("DensukeScraper 単体テスト")
class DensukeScraperTest {

    private final DensukeScraper scraper = new DensukeScraper();

    @Test
    @DisplayName("ScheduleEntry: 日付・試合番号・参加者が正しく設定される")
    void testScheduleEntryDataModel() {
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDate(LocalDate.of(2026, 3, 14));
        entry.setMatchNumber(2);
        entry.setVenueName("すずらん");
        entry.setRawLabel("3/14(金)すずらん 2試合目 17:20~");
        entry.getParticipants().add("田中");
        entry.getParticipants().add("鈴木");
        entry.getMaybeParticipants().add("佐藤");

        assertThat(entry.getDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(entry.getMatchNumber()).isEqualTo(2);
        assertThat(entry.getVenueName()).isEqualTo("すずらん");
        assertThat(entry.getParticipants()).containsExactly("田中", "鈴木");
        assertThat(entry.getMaybeParticipants()).containsExactly("佐藤");
    }

    @Test
    @DisplayName("DensukeData: エントリの追加と取得が正しく動作する")
    void testDensukeDataModel() {
        DensukeData data = new DensukeData();
        assertThat(data.getEntries()).isEmpty();

        ScheduleEntry entry1 = new ScheduleEntry();
        entry1.setDate(LocalDate.of(2026, 3, 1));
        entry1.setMatchNumber(1);

        ScheduleEntry entry2 = new ScheduleEntry();
        entry2.setDate(LocalDate.of(2026, 3, 1));
        entry2.setMatchNumber(2);

        data.getEntries().add(entry1);
        data.getEntries().add(entry2);

        assertThat(data.getEntries()).hasSize(2);
        assertThat(data.getEntries().get(0).getMatchNumber()).isEqualTo(1);
        assertThat(data.getEntries().get(1).getMatchNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("無効なURLでIOExceptionがスローされる")
    void testScrapeInvalidUrl() {
        assertThatThrownBy(() -> scraper.scrape("http://invalid.example.com/nonexistent", 2026))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("ScheduleEntry: 初期状態で参加者リストが空")
    void testScheduleEntryDefaultLists() {
        ScheduleEntry entry = new ScheduleEntry();
        assertThat(entry.getParticipants()).isNotNull().isEmpty();
        assertThat(entry.getMaybeParticipants()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("stripLeadingEmoji: 名前の先頭の絵文字が除去される")
    void testStripLeadingEmoji() {
        assertThat(DensukeScraper.stripLeadingEmoji("🔰田中")).isEqualTo("田中");
        assertThat(DensukeScraper.stripLeadingEmoji("🌟鈴木")).isEqualTo("鈴木");
        assertThat(DensukeScraper.stripLeadingEmoji("🔰🌟佐藤")).isEqualTo("佐藤");
        assertThat(DensukeScraper.stripLeadingEmoji("田中")).isEqualTo("田中");
        assertThat(DensukeScraper.stripLeadingEmoji("")).isEqualTo("");
        assertThat(DensukeScraper.stripLeadingEmoji(null)).isNull();
    }

    @Test
    @DisplayName("stripLeadingEmoji: 不可視Unicode文字（Variation Selector等）が除去される")
    void testStripInvisibleUnicodeChars() {
        // U+FE0E (Variation Selector-15) が先頭に付いたケース（実際に発生した不具合）
        assertThat(DensukeScraper.stripLeadingEmoji("\uFE0E井桁堅章")).isEqualTo("井桁堅章");
        // U+FE0F (Variation Selector-16)
        assertThat(DensukeScraper.stripLeadingEmoji("\uFE0F田中")).isEqualTo("田中");
        // 名前の中間に紛れ込んだ場合も除去
        assertThat(DensukeScraper.stripLeadingEmoji("井桁\uFE0E堅章")).isEqualTo("井桁堅章");
        // 絵文字 + Variation Selector の組み合わせ
        assertThat(DensukeScraper.stripLeadingEmoji("🔰\uFE0E田中")).isEqualTo("田中");
    }

    @Test
    @DisplayName("parse: 3択 (○/△/×) ページで全メンバー・出欠が正しく取得される")
    void testParseThreeChoicePage() throws IOException {
        String html = buildDensukeHtml(
                // 3択 → 凡例は ○/△/× の3列
                new String[]{"○", "△", "×"},
                new String[]{"田中", "鈴木", "佐藤"},
                new DataRow("3/1(日)会場A 1試合目", new String[]{"col3:○", "col2:△", "col1:×"}),
                new DataRow("2試合目", new String[]{"col3:○", "col0:-", "col3:○"})
        );
        Document doc = Jsoup.parse(html);
        DensukeData data = scraper.parse(doc, 2026);

        assertThat(data.getMemberNames()).containsExactly("田中", "鈴木", "佐藤");
        assertThat(data.getEntries()).hasSize(2);

        ScheduleEntry entry1 = data.getEntries().get(0);
        assertThat(entry1.getDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(entry1.getMatchNumber()).isEqualTo(1);
        assertThat(entry1.getParticipants()).containsExactly("田中");
        assertThat(entry1.getMaybeParticipants()).containsExactly("鈴木");

        ScheduleEntry entry2 = data.getEntries().get(1);
        assertThat(entry2.getMatchNumber()).isEqualTo(2);
        assertThat(entry2.getParticipants()).containsExactly("田中", "佐藤");
        assertThat(entry2.getMaybeParticipants()).isEmpty();
    }

    @Test
    @DisplayName("parse: 2択 (○/×) ページでも先頭メンバーが読み飛ばされない (Issue #470)")
    void testParseTwoChoicePageIncludesFirstMember() throws IOException {
        // 再現: 伝助ページ自動作成で eventchoice=1 (2択) で作られたページは
        // 凡例列が ○/× の2列しかなく、先頭メンバー列が index=3 になる。
        // 旧コードは index=4 ハードコードで先頭メンバーを黙って読み飛ばしていた。
        String html = buildDensukeHtml(
                new String[]{"○", "×"},
                new String[]{"遠藤明日真", "米山優花", "土居悠太"},
                new DataRow("5/2(土) クラ館 1試合目", new String[]{"col3:○", "col3:○", "col1:×"})
        );
        Document doc = Jsoup.parse(html);
        DensukeData data = scraper.parse(doc, 2026);

        assertThat(data.getMemberNames())
                .as("2択ページでも先頭メンバーがメンバーリストに含まれる")
                .containsExactly("遠藤明日真", "米山優花", "土居悠太");

        ScheduleEntry entry = data.getEntries().get(0);
        assertThat(entry.getParticipants())
                .as("先頭メンバーの ○ 出欠が取り込まれる")
                .containsExactly("遠藤明日真", "米山優花");
    }

    @Test
    @DisplayName("parse: memberdata アンカーが見つからないテーブルは例外を投げる")
    void testParseRejectsTableWithoutMembers() {
        String html = "<table class=\"listtbl\"><tr><td></td><td class=\"rline\">○</td></tr></table>";
        Document doc = Jsoup.parse(html);
        assertThatThrownBy(() -> scraper.parse(doc, 2026))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("メンバー列");
    }

    // ====================================================================
    // parseDensukeTitleAsDateTime: title 属性パース (Issue #544)
    // ====================================================================

    @Test
    @DisplayName("parseDensukeTitleAsDateTime: 正常系 M/d HH:mm が LocalDateTime に変換される")
    void testParseTitleValid() {
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("4/23 18:07", 2026))
                .isEqualTo(LocalDateTime.of(2026, 4, 23, 18, 7));
        // 1桁の月・日も許容
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("1/5 09:30", 2026))
                .isEqualTo(LocalDateTime.of(2026, 1, 5, 9, 30));
        // 2桁の月・日
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("12/31 23:59", 2026))
                .isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
    }

    @Test
    @DisplayName("parseDensukeTitleAsDateTime: null / 空文字 は null を返す")
    void testParseTitleNullOrEmpty() {
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime(null, 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("", 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("   ", 2026)).isNull();
    }

    @Test
    @DisplayName("parseDensukeTitleAsDateTime: フォーマット不一致は null を返す（例外を投げない）")
    void testParseTitleInvalidFormat() {
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("xxx", 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("2026/04/23 18:07", 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("4-23 18:07", 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("4/23", 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("18:07", 2026)).isNull();
    }

    @Test
    @DisplayName("parseDensukeTitleAsDateTime: 不正な日付（2/30 等）は null を返す")
    void testParseTitleInvalidDate() {
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("2/30 10:00", 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("13/1 10:00", 2026)).isNull();
        assertThat(DensukeScraper.parseDensukeTitleAsDateTime("4/23 25:00", 2026)).isNull();
    }

    @Test
    @DisplayName("parse: ヘッダー title 属性が memberLastChangeTimes に格納される")
    void testParsePopulatesMemberLastChangeTimes() throws IOException {
        // 田中: title="4/23 18:07" → parse 成功
        // 鈴木: title="" → 無視（map に入らない）
        // 佐藤: title="xxx" → parse 失敗で無視
        // 高橋: title="4/24 12:05" → parse 成功
        String html = "<table class=\"listtbl\">"
                + "<tr>"
                + "<td> </td>"
                + "<td class=\"rline\"><div align=\"center\">○</div></td>"
                + "<td class=\"rline\"><div align=\"center\">×</div></td>"
                + "<td nowrap><a href=\"javascript:memberdata(1);\" title=\"4/23 18:07\">田中</a></td>"
                + "<td nowrap><a href=\"javascript:memberdata(2);\" title=\"\">鈴木</a></td>"
                + "<td nowrap><a href=\"javascript:memberdata(3);\" title=\"xxx\">佐藤</a></td>"
                + "<td nowrap><a href=\"javascript:memberdata(4);\" title=\"4/24 12:05\">高橋</a></td>"
                + "</tr>"
                + "</table>";
        Document doc = Jsoup.parse(html);
        DensukeData data = scraper.parse(doc, 2026);

        assertThat(data.getMemberNames()).containsExactly("田中", "鈴木", "佐藤", "高橋");
        assertThat(data.getMemberLastChangeTimes())
                .containsEntry("田中", LocalDateTime.of(2026, 4, 23, 18, 7))
                .containsEntry("高橋", LocalDateTime.of(2026, 4, 24, 12, 5))
                .doesNotContainKey("鈴木")
                .doesNotContainKey("佐藤")
                .hasSize(2);
    }

    @Test
    @DisplayName("parse: title 属性がないヘッダは memberLastChangeTimes が空になる（既存挙動と互換）")
    void testParseWithoutTitleAttributes() throws IOException {
        String html = buildDensukeHtml(
                new String[]{"○", "×"},
                new String[]{"田中", "鈴木"},
                new DataRow("5/1(金) 会場 1試合目", new String[]{"col3:○", "col1:×"})
        );
        Document doc = Jsoup.parse(html);
        DensukeData data = scraper.parse(doc, 2026);

        assertThat(data.getMemberNames()).containsExactly("田中", "鈴木");
        assertThat(data.getMemberLastChangeTimes()).isEmpty();
    }

    // ====================================================================
    // ヘルパー: 伝助HTMLのモック構築
    // ====================================================================

    private record DataRow(String label, String[] memberCells) {}

    /** 伝助ページ相当の最小HTMLを組み立てる（凡例列 + メンバー列 + 試合行） */
    private static String buildDensukeHtml(String[] legendSymbols, String[] memberNames, DataRow... dataRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"listtbl\">");
        // header row
        sb.append("<tr>");
        sb.append("<td> </td>"); // 空の先頭セル
        for (String sym : legendSymbols) {
            sb.append("<td class=\"rline\"><div align=\"center\">").append(sym).append("</div></td>");
        }
        for (int i = 0; i < memberNames.length; i++) {
            sb.append("<td nowrap><a href=\"javascript:memberdata(")
                    .append(1000 + i).append(");\">")
                    .append(memberNames[i]).append("</a></td>");
        }
        sb.append("</tr>");
        // data rows
        for (DataRow row : dataRows) {
            sb.append("<tr>");
            sb.append("<td nowrap>").append(row.label()).append("</td>");
            // 凡例列相当のカウントセル（実際のHTMLと同様に legendSymbols と同じ数だけ並べる）
            for (int i = 0; i < legendSymbols.length; i++) {
                sb.append("<td class=\"rline\"><div align=\"center\"><div class=\"col2\">0</div></div></td>");
            }
            // member vote cells
            for (String cell : row.memberCells()) {
                int colon = cell.indexOf(':');
                String cls = cell.substring(0, colon);
                String text = cell.substring(colon + 1);
                sb.append("<td><div align=\"center\"><div class=\"")
                        .append(cls).append("\">").append(text).append("</div></div></td>");
            }
            sb.append("</tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }
}
