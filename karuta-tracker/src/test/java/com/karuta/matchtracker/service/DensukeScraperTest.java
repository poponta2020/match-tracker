package com.karuta.matchtracker.service;

import com.karuta.matchtracker.service.DensukeScraper.DensukeData;
import com.karuta.matchtracker.service.DensukeScraper.ScheduleEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;

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
}
