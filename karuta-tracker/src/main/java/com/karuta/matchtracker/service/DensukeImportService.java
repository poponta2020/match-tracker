package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;

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
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final NotificationRepository notificationRepository;

    /**
     * インポート結果
     */
    @Data
    public static class ImportResult {
        private int totalEntries;           // 処理した日程エントリ数
        private int createdSessionCount;    // 作成した練習日数
        private int registeredCount;        // 登録した参加者数
        private int skippedCount;           // スキップした数
        private int removedCount;           // 伝助から消えて自動削除した参加者数
        private List<String> unmatchedNames = new ArrayList<>();  // アプリに未登録の名前
        private List<String> unmatchedVenues = new ArrayList<>(); // 会場名がDBに見つからない
        private List<String> details = new ArrayList<>();         // 詳細ログ
    }

    /** スケジューラー等、認証ユーザーがいない場合のシステムユーザーID */
    public static final Long SYSTEM_USER_ID = 0L;

    /**
     * 伝助URLから参加者データをインポート
     *
     * @param url 伝助のURL
     * @param targetDate 特定の日付のみインポートする場合（nullなら全日付）
     * @param createdBy 操作者のプレイヤーID（スケジューラーの場合はSYSTEM_USER_ID）
     * @return インポート結果
     */
    @Transactional
    public ImportResult importFromDensuke(String url, LocalDate targetDate, Long createdBy) throws IOException {
        // 年を推定
        int year = targetDate != null ? targetDate.getYear() : JstDateTimeUtil.today().getYear();

        // スクレイピング
        DensukeScraper.DensukeData scraped = densukeScraper.scrape(url, year);

        // 全選手の名前→IDマップ、ID→名前マップ
        Map<String, Long> playerNameMap = playerRepository.findAll().stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toMap(Player::getName, Player::getId, (a, b) -> a));
        Map<Long, String> playerIdMap = playerNameMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));

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
                        .createdBy(createdBy)
                        .updatedBy(createdBy)
                        .build();
                session = practiceSessionRepository.save(session);
                result.setCreatedSessionCount(result.getCreatedSessionCount() + 1);
                result.getDetails().add(String.format("%s 練習日を作成（会場: %s, %d試合）",
                        entry.getDate(), venueName != null ? venueName : "不明", totalMatches));

                log.info("Created practice session: {} venue={} totalMatches={}", entry.getDate(), venueName, totalMatches);
            } else {
                session = sessionOpt.get();
            }

            // 抽選済みセッションはスキップ（参加者を変更しない）
            if (lotteryExecutionRepository.findTopBySessionIdOrderByExecutedAtDesc(session.getId()).isPresent()) {
                result.getDetails().add(String.format("%s 第%d試合: 抽選済みのためスキップ",
                        entry.getDate(), entry.getMatchNumber()));
                result.setSkippedCount(result.getSkippedCount() + entry.getParticipants().size());
                continue;
            }

            // 既存の参加者を取得
            List<PracticeParticipant> existingParticipants =
                    practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), entry.getMatchNumber());
            Set<Long> existingPlayerIds = existingParticipants.stream()
                    .map(PracticeParticipant::getPlayerId)
                    .collect(Collectors.toSet());

            // 伝助の参加者IDを収集
            Set<Long> densukePlayerIds = new LinkedHashSet<>();
            for (String name : entry.getParticipants()) {
                Long playerId = playerNameMap.get(name);
                if (playerId != null) {
                    densukePlayerIds.add(playerId);
                }
            }

            // 伝助から消えた参加者を自動削除（DBにいるが伝助にいない）
            for (Long existingId : existingPlayerIds) {
                if (!densukePlayerIds.contains(existingId)) {
                    String playerName = playerIdMap.get(existingId);
                    // DBから参加者を削除
                    existingParticipants.stream()
                            .filter(p -> p.getPlayerId().equals(existingId))
                            .findFirst()
                            .ifPresent(p -> {
                                practiceParticipantRepository.delete(p);
                                result.setRemovedCount(result.getRemovedCount() + 1);
                                log.info("Auto-removed participant: {} from session {} match {}",
                                        playerName, entry.getDate(), entry.getMatchNumber());
                            });
                }
            }

            // 新規参加者のみ追加（既存はそのまま）
            int matchRegistered = 0;
            for (String name : entry.getParticipants()) {
                Long playerId = playerNameMap.get(name);
                if (playerId == null) {
                    unmatchedNameSet.add(name);
                    result.setSkippedCount(result.getSkippedCount() + 1);
                    continue;
                }

                if (existingPlayerIds.contains(playerId)) {
                    continue; // 既に登録済み、スキップ
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

        // 未登録者がいる場合、管理者に通知
        if (!unmatchedNameSet.isEmpty()) {
            notifyAdminsOfUnmatchedNames(new ArrayList<>(unmatchedNameSet));
        }

        log.info("Densuke import completed: {} entries, {} sessions created, {} registered, {} skipped, {} unmatched names",
                result.getTotalEntries(), result.getCreatedSessionCount(), result.getRegisteredCount(),
                result.getSkippedCount(), result.getUnmatchedNames().size());

        return result;
    }

    /**
     * 未登録者を一括登録し、再度伝助からインポート
     *
     * @param names 登録する名前リスト
     * @param url 伝助URL（再同期用）
     * @param targetDate 対象日付（nullなら全日付）
     * @param createdBy 操作者のプレイヤーID
     * @return 再同期のインポート結果
     */
    @Transactional
    public ImportResult registerAndSync(List<String> names, String url, LocalDate targetDate, Long createdBy) throws IOException {
        int created = 0;
        for (String name : names) {
            // 既存チェック
            if (playerRepository.findByNameAndActive(name).isPresent()) {
                continue;
            }
            Player player = Player.builder()
                    .name(name)
                    .password("pppppppp")
                    .gender(Player.Gender.その他)
                    .dominantHand(Player.DominantHand.右)
                    .role(Player.Role.PLAYER)
                    .requirePasswordChange(true)
                    .build();
            playerRepository.save(player);
            created++;
            log.info("Auto-registered player: {}", name);
        }

        log.info("Registered {} new players, now re-syncing from densuke", created);

        // 再同期
        return importFromDensuke(url, targetDate, createdBy);
    }

    /**
     * 未登録者名を管理者に通知する（同じ内容の通知が既にあればスキップ、顔ぶれが変わっていれば更新）
     */
    private void notifyAdminsOfUnmatchedNames(List<String> unmatchedNames) {
        List<Player> admins = new ArrayList<>(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN));
        admins.addAll(playerRepository.findByRoleAndActive(Player.Role.ADMIN));

        if (admins.isEmpty()) return;

        String nameList = String.join("、", unmatchedNames);
        String message = String.format("伝助に登録されている以下の名前がアプリに未登録です。\n%s\n\n伝助管理ページから一括登録できます。",
                nameList);
        String title = "伝助同期: 未登録者あり（" + unmatchedNames.size() + "名）";

        List<Notification> toSave = new ArrayList<>();
        int skipped = 0;
        int updated = 0;

        for (Player admin : admins) {
            List<Notification> existing = notificationRepository.findByPlayerIdAndTypeAndDeletedAtIsNull(
                    admin.getId(), NotificationType.DENSUKE_UNMATCHED_NAMES);

            if (!existing.isEmpty()) {
                // 最新の既存通知と比較
                Notification latest = existing.get(0);
                if (message.equals(latest.getMessage())) {
                    // 同じ内容 → スキップ
                    skipped++;
                    continue;
                }
                // 顔ぶれが変わった → 既存を更新
                latest.setTitle(title);
                latest.setMessage(message);
                latest.setIsRead(false);
                toSave.add(latest);
                updated++;
            } else {
                // 既存なし → 新規作成
                toSave.add(Notification.builder()
                        .playerId(admin.getId())
                        .type(NotificationType.DENSUKE_UNMATCHED_NAMES)
                        .title(title)
                        .message(message)
                        .build());
            }
        }

        if (!toSave.isEmpty()) {
            notificationRepository.saveAll(toSave);
        }
        log.info("Densuke unmatched names notification: {} new/updated, {} skipped (unchanged)",
                toSave.size(), skipped);
    }
}
