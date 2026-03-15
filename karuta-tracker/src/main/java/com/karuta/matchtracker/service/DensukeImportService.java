package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DensukeImportService {

    private final DensukeScraper densukeScraper;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PlayerRepository playerRepository;
    private final VenueRepository venueRepository;

    /**
     * インポート結果
     */
    @Data
    public static class ImportResult {
        private int totalEntries;           // 処理した日程エントリ数
        private int createdSessionCount;    // 作成した練習日数
        private int registeredCount;        // 登録した参加者数
        private int skippedCount;           // スキップした数
        private List<String> unmatchedNames = new ArrayList<>();  // アプリに未登録の名前
        private List<String> unmatchedVenues = new ArrayList<>(); // 会場名がDBに見つからない
        private List<String> details = new ArrayList<>();         // 詳細ログ
    }

    /**
     * 伝助URLから参加者データをインポート
     *
     * @param url 伝助のURL
     * @param targetDate 特定の日付のみインポートする場合（nullなら全日付）
     * @return インポート結果
     */
    @Transactional
    public ImportResult importFromDensuke(String url, LocalDate targetDate) throws IOException {
        // 年を推定
        int year = targetDate != null ? targetDate.getYear() : LocalDate.now().getYear();

        // スクレイピング
        DensukeScraper.DensukeData scraped = densukeScraper.scrape(url, year);

        // 全選手の名前→IDマップ
        Map<String, Long> playerNameMap = playerRepository.findAll().stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toMap(Player::getName, Player::getId, (a, b) -> a));

        // 会場名→Venueマップ
        Map<String, Venue> venueNameMap = venueRepository.findAll().stream()
                .collect(Collectors.toMap(Venue::getName, v -> v, (a, b) -> a));

        ImportResult result = new ImportResult();
        result.setTotalEntries(scraped.getEntries().size());

        Set<String> unmatchedNameSet = new LinkedHashSet<>();
        Set<String> unmatchedVenueSet = new LinkedHashSet<>();

        // 日付ごとの最大試合番号を事前計算（セッション作成用）
        Map<LocalDate, Integer> maxMatchByDate = new LinkedHashMap<>();
        Map<LocalDate, String> venueByDate = new LinkedHashMap<>();
        for (DensukeScraper.ScheduleEntry entry : scraped.getEntries()) {
            maxMatchByDate.merge(entry.getDate(), entry.getMatchNumber(), Math::max);
            if (entry.getVenueName() != null) {
                venueByDate.putIfAbsent(entry.getDate(), entry.getVenueName());
            }
        }

        for (DensukeScraper.ScheduleEntry entry : scraped.getEntries()) {
            // 対象日付のフィルタ
            if (targetDate != null && !entry.getDate().equals(targetDate)) {
                continue;
            }

            // 練習セッションを検索、なければ作成
            Optional<PracticeSession> sessionOpt = practiceSessionRepository.findBySessionDate(entry.getDate());
            PracticeSession session;

            if (sessionOpt.isEmpty()) {
                // 会場名からVenueを検索
                String venueName = venueByDate.get(entry.getDate());
                Long venueId = null;
                if (venueName != null) {
                    Venue venue = venueNameMap.get(venueName);
                    if (venue != null) {
                        venueId = venue.getId();
                    } else {
                        unmatchedVenueSet.add(venueName);
                    }
                }

                int totalMatches = maxMatchByDate.getOrDefault(entry.getDate(), 3);

                // 練習セッションを新規作成
                session = PracticeSession.builder()
                        .sessionDate(entry.getDate())
                        .totalMatches(totalMatches)
                        .venueId(venueId)
                        .createdBy(1L)
                        .updatedBy(1L)
                        .build();
                session = practiceSessionRepository.save(session);
                result.setCreatedSessionCount(result.getCreatedSessionCount() + 1);
                result.getDetails().add(String.format("%s 練習日を作成（会場: %s, %d試合）",
                        entry.getDate(), venueName != null ? venueName : "不明", totalMatches));

                log.info("Created practice session: {} venue={} totalMatches={}", entry.getDate(), venueName, totalMatches);
            } else {
                session = sessionOpt.get();
            }

            // 既存の参加者を削除（上書きモード）
            practiceParticipantRepository.deleteBySessionIdAndMatchNumber(session.getId(), entry.getMatchNumber());

            // ○の参加者を登録
            int matchRegistered = 0;
            for (String name : entry.getParticipants()) {
                Long playerId = playerNameMap.get(name);
                if (playerId == null) {
                    unmatchedNameSet.add(name);
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                PracticeParticipant participant = PracticeParticipant.builder()
                        .sessionId(session.getId())
                        .playerId(playerId)
                        .matchNumber(entry.getMatchNumber())
                        .build();
                practiceParticipantRepository.save(participant);
                result.setRegisteredCount(result.getRegisteredCount() + 1);
                matchRegistered++;
            }

            result.getDetails().add(String.format("%s 第%d試合: %d名登録",
                    entry.getDate(), entry.getMatchNumber(), matchRegistered));
        }

        result.setUnmatchedNames(new ArrayList<>(unmatchedNameSet));
        result.setUnmatchedVenues(new ArrayList<>(unmatchedVenueSet));

        log.info("Densuke import completed: {} entries, {} sessions created, {} registered, {} skipped, {} unmatched names",
                result.getTotalEntries(), result.getCreatedSessionCount(), result.getRegisteredCount(),
                result.getSkippedCount(), result.getUnmatchedNames().size());

        return result;
    }
}
