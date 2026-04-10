package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.entity.*;
import com.karuta.matchtracker.entity.Notification.NotificationType;
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
    private final PlayerService playerService;
    private final VenueRepository venueRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final LotteryService lotteryService;
    private final WaitlistPromotionService waitlistPromotionService;
    private final PracticeParticipantService practiceParticipantService;
    private final LineNotificationService lineNotificationService;
    private final OrganizationService organizationService;

    @Data
    public static class ImportResult {
        private int totalEntries;
        private int createdSessionCount;
        private int registeredCount;
        private int skippedCount;
        private int removedCount;
        private List<String> unmatchedNames = new ArrayList<>();
        private List<String> unmatchedVenues = new ArrayList<>();
        private List<String> details = new ArrayList<>();
    }

    public static final Long SYSTEM_USER_ID = 0L;

    private enum ImportPhase { PHASE1, PHASE2, PHASE3 }

    @Transactional
    public ImportResult importFromDensuke(String url, LocalDate targetDate, Long createdBy, Long organizationId) throws IOException {
        int year = targetDate != null ? targetDate.getYear() : JstDateTimeUtil.today().getYear();

        DensukeScraper.DensukeData scraped = densukeScraper.scrape(url, year);

        Map<String, Long> playerNameMap = playerService.findAllPlayersRaw().stream()
                .filter(p -> p.getDeletedAt() == null)
                .collect(Collectors.toMap(
                        p -> DensukeScraper.stripLeadingEmoji(p.getName()),
                        Player::getId, (a, b) -> a));
        Map<Long, String> playerIdMap = playerNameMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey, (a, b) -> a));

        Map<String, Venue> venueNameMap = venueRepository.findAll().stream()
                .collect(Collectors.toMap(Venue::getName, v -> v, (a, b) -> a));

        DeadlineType deadlineType = lotteryDeadlineHelper.getDeadlineType(organizationId);

        ImportResult result = new ImportResult();
        result.setTotalEntries(scraped.getEntries().size());

        Set<String> unmatchedNameSet = new LinkedHashSet<>();
        Set<String> unmatchedVenueSet = new LinkedHashSet<>();
        List<AdminWaitlistNotificationData> pendingNotifications = new ArrayList<>();
        Set<PracticeSession> vacancyChangedSessions = new LinkedHashSet<>();

        Map<LocalDate, Integer> maxMatchByDate = new LinkedHashMap<>();
        Map<LocalDate, String> venueByDate = new LinkedHashMap<>();
        for (DensukeScraper.ScheduleEntry entry : scraped.getEntries()) {
            maxMatchByDate.merge(entry.getDate(), entry.getMatchNumber(), Math::max);
            if (entry.getVenueName() != null) {
                venueByDate.putIfAbsent(entry.getDate(), entry.getVenueName());
            }
        }

        for (DensukeScraper.ScheduleEntry entry : scraped.getEntries()) {
            if (targetDate != null
                    && !(entry.getDate().getYear() == targetDate.getYear()
                         && entry.getDate().getMonthValue() == targetDate.getMonthValue())) {
                continue;
            }

            // --- セッション作成/取得（既存ロジック維持） ---
            PracticeSession session = findOrCreateSession(entry, organizationId, createdBy,
                    maxMatchByDate, venueByDate, venueNameMap, unmatchedVenueSet, result);

            // --- フェーズ判定 ---
            int entryYear = entry.getDate().getYear();
            int entryMonth = entry.getDate().getMonthValue();
            ImportPhase phase = determinePhase(deadlineType, entryYear, entryMonth, entry.getDate(), organizationId);

            // --- フェーズ別処理 ---
            switch (phase) {
                case PHASE1 -> processPhase1(entry, session, deadlineType, playerNameMap, playerIdMap,
                        unmatchedNameSet, result);
                case PHASE2 -> {
                    result.getDetails().add(String.format("%s 第%d試合: 締切後・抽選確定前のためスキップ",
                            entry.getDate(), entry.getMatchNumber()));
                    result.setSkippedCount(result.getSkippedCount() + entry.getParticipants().size());
                }
                case PHASE3 -> processPhase3(entry, session, scraped.getMemberNames(),
                        playerNameMap, playerIdMap, unmatchedNameSet, result, pendingNotifications, vacancyChangedSessions);
            }
        }

        // 蓄積した通知をセッション×トリガー×プレイヤーでグルーピングしてまとめ送信
        if (!pendingNotifications.isEmpty()) {
            sendPendingNotifications(pendingNotifications);
        }

        // 伝助同期で空き枠変動があったセッションの統合通知を送信
        sendConsolidatedVacancyNotifications(vacancyChangedSessions);

        result.setUnmatchedNames(new ArrayList<>(unmatchedNameSet));
        result.setUnmatchedVenues(new ArrayList<>(unmatchedVenueSet));

        if (!unmatchedNameSet.isEmpty()) {
            notifyAdminsOfUnmatchedNames(new ArrayList<>(unmatchedNameSet), organizationId);
        }

        log.info("Densuke import completed: {} entries, {} sessions created, {} registered, {} skipped, {} removed",
                result.getTotalEntries(), result.getCreatedSessionCount(), result.getRegisteredCount(),
                result.getSkippedCount(), result.getRemovedCount());

        return result;
    }

    /**
     * フェーズ判定
     */
    private ImportPhase determinePhase(DeadlineType deadlineType, int year, int month,
                                       LocalDate sessionDate, Long organizationId) {
        if (deadlineType == DeadlineType.SAME_DAY) {
            // SAME_DAY: 締切前→Phase1、締切後→Phase3（Phase2なし）
            return lotteryDeadlineHelper.isBeforeSameDayDeadline(sessionDate)
                    ? ImportPhase.PHASE1 : ImportPhase.PHASE3;
        }

        // MONTHLY
        if (lotteryDeadlineHelper.isBeforeDeadline(year, month, organizationId)) {
            return ImportPhase.PHASE1;
        }
        if (lotteryService.isLotteryConfirmed(year, month, organizationId)) {
            return ImportPhase.PHASE3;
        }
        return ImportPhase.PHASE2;
    }

    // ========================================================================
    // Phase 1: 締め切り前
    // ========================================================================

    private void processPhase1(DensukeScraper.ScheduleEntry entry, PracticeSession session,
                               DeadlineType deadlineType,
                               Map<String, Long> playerNameMap, Map<Long, String> playerIdMap,
                               Set<String> unmatchedNameSet, ImportResult result) {
        List<PracticeParticipant> existing =
                practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), entry.getMatchNumber());
        Map<Long, PracticeParticipant> existingByPlayerId = existing.stream()
                .collect(Collectors.toMap(PracticeParticipant::getPlayerId, p -> p, (a, b) -> a));

        // ○の参加者IDを収集
        Set<Long> markedPlayerIds = new LinkedHashSet<>();
        for (String name : entry.getParticipants()) {
            Long playerId = playerNameMap.get(name);
            if (playerId != null) {
                markedPlayerIds.add(playerId);
            }
        }

        // not-○: 既存レコード(dirty=false)を削除（1-E）
        for (PracticeParticipant p : existing) {
            if (!markedPlayerIds.contains(p.getPlayerId()) && !p.isDirty()) {
                practiceParticipantRepository.delete(p);
                result.setRemovedCount(result.getRemovedCount() + 1);
                log.info("Phase1: removed non-○ participant {} from session {} match {}",
                        playerIdMap.get(p.getPlayerId()), entry.getDate(), entry.getMatchNumber());
            }
        }

        // ○: 新規追加 or キャンセル済みレコードの再有効化（1-A）
        int matchRegistered = 0;
        for (String name : entry.getParticipants()) {
            Long playerId = playerNameMap.get(name);
            if (playerId == null) {
                unmatchedNameSet.add(name);
                result.setSkippedCount(result.getSkippedCount() + 1);
                continue;
            }

            PracticeParticipant existingParticipant = existingByPlayerId.get(playerId);
            if (existingParticipant != null) {
                // CANCELLED/DECLINED/WAITLIST_DECLINED → 再有効化
                if (!existingParticipant.isDirty() && isTerminalStatus(existingParticipant.getStatus())) {
                    reactivatePhase1(existingParticipant, deadlineType, session, entry.getMatchNumber());
                    result.setRegisteredCount(result.getRegisteredCount() + 1);
                    matchRegistered++;
                    log.info("Phase1: reactivated {} ({}) for session {} match {}",
                            name, existingParticipant.getStatus(), entry.getDate(), entry.getMatchNumber());
                }
                continue; // 1-B, 1-C: アクティブな既存レコードはスキップ
            }
            if (practiceParticipantRepository.existsBySessionIdAndPlayerIdAndMatchNumber(
                    session.getId(), playerId, entry.getMatchNumber())) {
                continue;
            }

            // SAME_DAY → WON/WAITLISTED、MONTHLY → PENDING
            ParticipantStatus status;
            Integer waitlistNumber = null;
            if (deadlineType == DeadlineType.SAME_DAY) {
                if (practiceParticipantService.isFreeRegistrationOpen(session, entry.getMatchNumber())) {
                    status = ParticipantStatus.WON;
                } else {
                    status = ParticipantStatus.WAITLISTED;
                    waitlistNumber = practiceParticipantRepository
                            .findMaxWaitlistNumber(session.getId(), entry.getMatchNumber()).orElse(0) + 1;
                }
            } else {
                status = ParticipantStatus.PENDING;
            }

            PracticeParticipant participant = PracticeParticipant.builder()
                    .sessionId(session.getId())
                    .playerId(playerId)
                    .matchNumber(entry.getMatchNumber())
                    .status(status)
                    .waitlistNumber(waitlistNumber)
                    .dirty(false)
                    .build();
            practiceParticipantRepository.save(participant);
            result.setRegisteredCount(result.getRegisteredCount() + 1);
            matchRegistered++;
        }

        result.getDetails().add(String.format("%s 第%d試合 [Phase1]: %d名登録",
                entry.getDate(), entry.getMatchNumber(), matchRegistered));
    }

    // ========================================================================
    // Phase 3: 抽選確定後（またはSAME_DAY締切後）
    // ========================================================================

    private void processPhase3(DensukeScraper.ScheduleEntry entry, PracticeSession session,
                               List<String> memberNames,
                               Map<String, Long> playerNameMap, Map<Long, String> playerIdMap,
                               Set<String> unmatchedNameSet, ImportResult result,
                               List<AdminWaitlistNotificationData> pendingNotifications,
                               Set<PracticeSession> vacancyChangedSessions) {
        // 既存参加者をマップ化
        List<PracticeParticipant> existing =
                practiceParticipantRepository.findBySessionIdAndMatchNumber(session.getId(), entry.getMatchNumber());
        Map<Long, PracticeParticipant> existingByPlayerId = existing.stream()
                .collect(Collectors.toMap(PracticeParticipant::getPlayerId, p -> p, (a, b) -> a));

        // 伝助の各カテゴリのプレイヤーIDを収集
        Set<Long> markedIds = resolvePlayerIds(entry.getParticipants(), playerNameMap, unmatchedNameSet);
        Set<Long> maybeIds = resolvePlayerIds(entry.getMaybeParticipants(), playerNameMap, unmatchedNameSet);

        // ×/空白: memberNames に含まれるが ○にも△にもいない
        Set<String> markedAndMaybeNames = new HashSet<>();
        markedAndMaybeNames.addAll(entry.getParticipants());
        markedAndMaybeNames.addAll(entry.getMaybeParticipants());

        Set<Long> absentIds = new LinkedHashSet<>();
        for (String name : memberNames) {
            if (!markedAndMaybeNames.contains(name)) {
                Long playerId = playerNameMap.get(name);
                if (playerId != null) {
                    absentIds.add(playerId);
                }
            }
        }

        int processed = 0;

        // --- ○ の処理 (3-A) ---
        for (Long playerId : markedIds) {
            PracticeParticipant p = existingByPlayerId.get(playerId);
            if (processPhase3Maru(playerId, p, session, entry.getMatchNumber(), vacancyChangedSessions)) {
                processed++;
            }
        }

        // --- △ の処理 (3-B) ---
        for (Long playerId : maybeIds) {
            PracticeParticipant p = existingByPlayerId.get(playerId);
            if (processPhase3Sankaku(playerId, p, session, entry.getMatchNumber(), pendingNotifications)) {
                processed++;
            }
        }

        // --- ×/空白 の処理 (3-C) ---
        for (Long playerId : absentIds) {
            PracticeParticipant p = existingByPlayerId.get(playerId);
            if (processPhase3Batsu(playerId, p, session, entry.getMatchNumber(), pendingNotifications)) {
                processed++;
            }
        }

        result.setRegisteredCount(result.getRegisteredCount() + processed);
        result.getDetails().add(String.format("%s 第%d試合 [Phase3]: %d件処理",
                entry.getDate(), entry.getMatchNumber(), processed));
    }

    /**
     * 3-A: 伝助○の処理
     */
    private boolean processPhase3Maru(Long playerId, PracticeParticipant existing,
                                      PracticeSession session, int matchNumber, Set<PracticeSession> vacancyChangedSessions) {
        if (existing == null) {
            // 3-A1/A2/A3: 未登録 → 定員判定して登録
            registerNewParticipant(playerId, session, matchNumber, vacancyChangedSessions);
            return true;
        }
        if (existing.isDirty()) return false; // dirty保護

        ParticipantStatus status = existing.getStatus();
        switch (status) {
            case WON -> { return false; } // 3-A4: 一致、スキップ
            case WAITLISTED -> {
                // 当日12:00以降かつ空き枠がある場合: WONに昇格（先着参加の仕様）
                if (lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())) {
                    long wonCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                            session.getId(), matchNumber, ParticipantStatus.WON);
                    int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
                    if (wonCount < capacity) {
                        Integer oldWaitlistNumber = existing.getWaitlistNumber();
                        existing.setStatus(ParticipantStatus.WON);
                        existing.setWaitlistNumber(null);
                        existing.setDirty(false); // 伝助は既に○なので書き戻し不要
                        practiceParticipantRepository.save(existing);
                        // 後続のキャンセル待ち番号を一括繰り上げ
                        if (oldWaitlistNumber != null) {
                            practiceParticipantRepository.decrementWaitlistNumbersAfter(session.getId(), matchNumber, oldWaitlistNumber);
                        }
                        log.info("Phase3-A6: promoted WAITLISTED player {} to WON (same-day after noon, vacancy available)",
                                playerId);
                        vacancyChangedSessions.add(session);
                        return true;
                    }
                }
                // 3-A6: 12:00前または空き枠なし → 抽選バイパス不可。dirty=trueにして△で書き戻す
                existing.setDirty(true);
                practiceParticipantRepository.save(existing);
                log.info("Phase3-A6: WAITLISTED player {} set dirty for △ write-back", playerId);
                return true;
            }
            case OFFERED -> {
                // 3-A8: オファー期限内なら承諾
                if (existing.getOfferDeadline() != null
                        && JstDateTimeUtil.now().isAfter(existing.getOfferDeadline())) {
                    log.info("Phase3-A8: offer expired for player {}, skipping", playerId);
                    return false;
                }
                try {
                    waitlistPromotionService.respondToOffer(existing.getId(), true);
                    log.info("Phase3-A8: accepted offer for player {} via densuke", playerId);
                } catch (Exception e) {
                    log.warn("Phase3-A8: failed to accept offer for player {}: {}", playerId, e.getMessage());
                }
                return true;
            }
            case CANCELLED, DECLINED, WAITLIST_DECLINED -> {
                // 3-A10/A11: 既存レコードを再利用して再登録（一意制約違反を防ぐ）
                reactivateAsNewParticipant(existing, session, matchNumber, vacancyChangedSessions);
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * 3-B: 伝助△の処理（キャンセル待ち希望）
     */
    private boolean processPhase3Sankaku(Long playerId, PracticeParticipant existing,
                                         PracticeSession session, int matchNumber,
                                         List<AdminWaitlistNotificationData> pendingNotifications) {
        if (existing == null) {
            // 3-B1: 未登録 → WAITLISTED（最後尾）
            createWaitlisted(playerId, session, matchNumber);
            return true;
        }
        if (existing.isDirty()) return false; // dirty保護

        ParticipantStatus status = existing.getStatus();
        switch (status) {
            case WON -> {
                // 3-B2: WON → WAITLISTED（最後尾）→ 繰り上げ発動
                AdminWaitlistNotificationData notifData = waitlistPromotionService.demoteToWaitlistSuppressed(existing.getId());
                if (notifData != null) {
                    pendingNotifications.add(notifData);
                }
                log.info("Phase3-B2: demoted WON player {} to waitlist via densuke", playerId);
                return true;
            }
            case WAITLISTED, OFFERED -> { return false; } // 3-B4/B6: 一致、スキップ
            case CANCELLED, DECLINED, WAITLIST_DECLINED -> {
                // 3-B8: 既存レコードを再利用してキャンセル待ちに復帰（一意制約違反を防ぐ）
                reactivateAsWaitlisted(existing, session, matchNumber);
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * 3-C: 伝助×/空白の処理
     */
    private boolean processPhase3Batsu(Long playerId, PracticeParticipant existing,
                                       PracticeSession session, int matchNumber,
                                       List<AdminWaitlistNotificationData> pendingNotifications) {
        if (existing == null) return false; // 3-C1: 一致、何もしない
        if (existing.isDirty()) return false; // dirty保護

        ParticipantStatus status = existing.getStatus();
        switch (status) {
            case WON -> {
                // 3-C2: キャンセル → 繰り上げ発動
                AdminWaitlistNotificationData notifData = waitlistPromotionService.cancelParticipationSuppressed(
                        existing.getId(), null, null);
                if (notifData != null) {
                    pendingNotifications.add(notifData);
                }
                log.info("Phase3-C2: cancelled WON player {} via densuke", playerId);
                return true;
            }
            case WAITLISTED -> {
                // 3-C4: キャンセル待ち辞退
                existing.setStatus(ParticipantStatus.WAITLIST_DECLINED);
                existing.setDirty(true);
                Integer oldNumber = existing.getWaitlistNumber();
                existing.setWaitlistNumber(null);
                practiceParticipantRepository.save(existing);
                // 後続番号一括繰り上げ
                if (oldNumber != null) {
                    practiceParticipantRepository.decrementWaitlistNumbersAfter(session.getId(), matchNumber, oldNumber);
                }
                log.info("Phase3-C4: WAITLISTED player {} declined via densuke", playerId);
                return true;
            }
            case OFFERED -> {
                // 3-C6: オファー辞退 → DECLINED → 次繰り上げ（通知はバッチ送信）
                try {
                    AdminWaitlistNotificationData notifData = waitlistPromotionService.respondToOfferDeclineSuppressed(existing.getId());
                    if (notifData != null) {
                        pendingNotifications.add(notifData);
                    }
                    log.info("Phase3-C6: OFFERED player {} declined via densuke", playerId);
                } catch (Exception e) {
                    log.warn("Phase3-C6: failed to decline offer for player {}: {}", playerId, e.getMessage());
                }
                return true;
            }
            case CANCELLED, DECLINED, WAITLIST_DECLINED -> { return false; } // 3-C8: 一致
            default -> { return false; }
        }
    }

    // ========================================================================
    // ヘルパーメソッド
    // ========================================================================

    private void registerNewParticipant(Long playerId, PracticeSession session, int matchNumber, Set<PracticeSession> vacancyChangedSessions) {
        if (practiceParticipantService.isFreeRegistrationOpen(session, matchNumber)) {
            practiceParticipantRepository.save(PracticeParticipant.builder()
                    .sessionId(session.getId()).playerId(playerId).matchNumber(matchNumber)
                    .status(ParticipantStatus.WON).dirty(true).build());
            log.info("Phase3: registered player {} as WON for session {} match {}", playerId, session.getId(), matchNumber);
            vacancyChangedSessions.add(session);
        } else {
            createWaitlisted(playerId, session, matchNumber);
        }
    }

    private void createWaitlisted(Long playerId, PracticeSession session, int matchNumber) {
        int maxNumber = practiceParticipantRepository
                .findMaxWaitlistNumber(session.getId(), matchNumber).orElse(0);
        practiceParticipantRepository.save(PracticeParticipant.builder()
                .sessionId(session.getId()).playerId(playerId).matchNumber(matchNumber)
                .status(ParticipantStatus.WAITLISTED).waitlistNumber(maxNumber + 1)
                .dirty(true).build());
        log.info("Phase3: registered player {} as WAITLISTED #{} for session {} match {}",
                playerId, maxNumber + 1, session.getId(), matchNumber);
    }

    /**
     * CANCELLED/DECLINED/WAITLIST_DECLINED の既存レコードを再利用して WON or WAITLISTED に復帰させる。
     * 新規INSERTではなく既存レコードのUPDATEにすることで一意制約違反を防ぐ。
     */
    private void reactivateAsNewParticipant(PracticeParticipant existing, PracticeSession session, int matchNumber, Set<PracticeSession> vacancyChangedSessions) {
        clearCancelledFields(existing);
        if (practiceParticipantService.isFreeRegistrationOpen(session, matchNumber)) {
            existing.setStatus(ParticipantStatus.WON);
            existing.setWaitlistNumber(null);
            log.info("Phase3: reactivated player {} as WON for session {} match {}",
                    existing.getPlayerId(), session.getId(), matchNumber);
            practiceParticipantRepository.save(existing);
            vacancyChangedSessions.add(session);
        } else {
            int maxNumber = practiceParticipantRepository
                    .findMaxWaitlistNumber(session.getId(), matchNumber).orElse(0);
            existing.setStatus(ParticipantStatus.WAITLISTED);
            existing.setWaitlistNumber(maxNumber + 1);
            log.info("Phase3: reactivated player {} as WAITLISTED #{} for session {} match {}",
                    existing.getPlayerId(), maxNumber + 1, session.getId(), matchNumber);
            practiceParticipantRepository.save(existing);
        }
    }

    /**
     * CANCELLED/DECLINED/WAITLIST_DECLINED の既存レコードを再利用して WAITLISTED に復帰させる。
     */
    private void reactivateAsWaitlisted(PracticeParticipant existing, PracticeSession session, int matchNumber) {
        clearCancelledFields(existing);
        int maxNumber = practiceParticipantRepository
                .findMaxWaitlistNumber(session.getId(), matchNumber).orElse(0);
        existing.setStatus(ParticipantStatus.WAITLISTED);
        existing.setWaitlistNumber(maxNumber + 1);
        practiceParticipantRepository.save(existing);
        log.info("Phase3: reactivated player {} as WAITLISTED #{} for session {} match {}",
                existing.getPlayerId(), maxNumber + 1, session.getId(), matchNumber);
    }

    /**
     * Phase1でCANCELLED/DECLINED/WAITLIST_DECLINEDのレコードを再有効化する。
     * MONTHLY → PENDING、SAME_DAY → WON or WAITLISTED。
     */
    private void reactivatePhase1(PracticeParticipant existing, DeadlineType deadlineType,
                                   PracticeSession session, int matchNumber) {
        clearCancelledFields(existing);
        if (deadlineType == DeadlineType.SAME_DAY) {
            if (practiceParticipantService.isFreeRegistrationOpen(session, matchNumber)) {
                existing.setStatus(ParticipantStatus.WON);
                existing.setWaitlistNumber(null);
            } else {
                int maxNumber = practiceParticipantRepository
                        .findMaxWaitlistNumber(session.getId(), matchNumber).orElse(0);
                existing.setStatus(ParticipantStatus.WAITLISTED);
                existing.setWaitlistNumber(maxNumber + 1);
            }
        } else {
            existing.setStatus(ParticipantStatus.PENDING);
            existing.setWaitlistNumber(null);
        }
        practiceParticipantRepository.save(existing);
    }

    /**
     * 伝助同期で空き枠変動があったセッションについて、統合通知を送信する。
     * SameDayVacancySchedulerと同じロジックでセッション単位の空き枠を集計し、1回だけ通知する。
     */
    private void sendConsolidatedVacancyNotifications(Set<PracticeSession> sessions) {
        for (PracticeSession session : sessions) {
            if (!lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())) {
                continue;
            }
            int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
            if (capacity <= 0) continue;

            int totalMatches = session.getTotalMatches() != null ? session.getTotalMatches() : 1;
            Map<Integer, Integer> vacanciesByMatch = new LinkedHashMap<>();

            for (int mn = 1; mn <= totalMatches; mn++) {
                List<PracticeParticipant> wonParticipants = practiceParticipantRepository
                        .findBySessionIdAndMatchNumberAndStatus(session.getId(), mn, ParticipantStatus.WON);
                List<PracticeParticipant> waitlistedParticipants = practiceParticipantRepository
                        .findBySessionIdAndMatchNumberAndStatus(session.getId(), mn, ParticipantStatus.WAITLISTED);

                if (wonParticipants.size() < capacity && waitlistedParticipants.isEmpty()) {
                    int vacancies = capacity - wonParticipants.size();
                    vacanciesByMatch.put(mn, vacancies);
                }
            }

            if (!vacanciesByMatch.isEmpty()) {
                lineNotificationService.sendConsolidatedSameDayVacancyNotification(session, vacanciesByMatch, null);
                lineNotificationService.sendConsolidatedAdminVacancyNotification(session, vacanciesByMatch);
                log.info("Densuke sync: sent consolidated vacancy notification for session {} ({} matches with vacancies)",
                        session.getId(), vacanciesByMatch.size());
            }
        }
    }

    private boolean isTerminalStatus(ParticipantStatus status) {
        return status == ParticipantStatus.CANCELLED
                || status == ParticipantStatus.DECLINED
                || status == ParticipantStatus.WAITLIST_DECLINED;
    }

    /**
     * キャンセル系ステータスに紐づくフィールドをクリアし、dirty=true にする。
     */
    private void clearCancelledFields(PracticeParticipant existing) {
        existing.setDirty(true);
        existing.setCancelReason(null);
        existing.setCancelReasonDetail(null);
        existing.setCancelledAt(null);
        existing.setOfferedAt(null);
        existing.setOfferDeadline(null);
        existing.setRespondedAt(null);
    }

    private Set<Long> resolvePlayerIds(List<String> names, Map<String, Long> playerNameMap,
                                        Set<String> unmatchedNameSet) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String name : names) {
            Long playerId = playerNameMap.get(name);
            if (playerId != null) {
                ids.add(playerId);
            } else {
                unmatchedNameSet.add(name);
            }
        }
        return ids;
    }

    private PracticeSession findOrCreateSession(DensukeScraper.ScheduleEntry entry, Long organizationId,
                                                 Long createdBy,
                                                 Map<LocalDate, Integer> maxMatchByDate,
                                                 Map<LocalDate, String> venueByDate,
                                                 Map<String, Venue> venueNameMap,
                                                 Set<String> unmatchedVenueSet,
                                                 ImportResult result) {
        Optional<PracticeSession> sessionOpt = practiceSessionRepository
                .findBySessionDateAndOrganizationId(entry.getDate(), organizationId);

        if (sessionOpt.isPresent()) {
            PracticeSession session = sessionOpt.get();
            if (session.getVenueId() == null) {
                String venueName = venueByDate.get(entry.getDate());
                if (venueName != null) {
                    Venue venue = venueNameMap.get(venueName);
                    if (venue != null) {
                        session.setVenueId(venue.getId());
                        practiceSessionRepository.save(session);
                        result.getDetails().add(String.format("%s 会場を補完: %s", entry.getDate(), venueName));
                    } else {
                        unmatchedVenueSet.add(venueName);
                    }
                }
            }
            return session;
        }

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
        PracticeSession session = PracticeSession.builder()
                .sessionDate(entry.getDate())
                .totalMatches(totalMatches)
                .venueId(venueId)
                .organizationId(organizationId)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();
        session = practiceSessionRepository.save(session);
        result.setCreatedSessionCount(result.getCreatedSessionCount() + 1);
        result.getDetails().add(String.format("%s 練習日を作成（会場: %s, %d試合）",
                entry.getDate(), venueName != null ? venueName : "不明", totalMatches));
        return session;
    }

    // ========================================================================
    // registerAndSync（既存ロジック維持）
    // ========================================================================

    @Transactional
    public ImportResult registerAndSync(List<String> names, String url, LocalDate targetDate,
                                         Long createdBy, Long organizationId) throws IOException {
        int created = 0;
        for (String rawName : names) {
            String name = DensukeScraper.stripLeadingEmoji(rawName);
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
            organizationService.ensurePlayerBelongsToOrganization(player.getId(), organizationId);
            created++;
            log.info("Auto-registered player: {} (org: {})", name, organizationId);
        }
        log.info("Registered {} new players, now re-syncing from densuke", created);
        return importFromDensuke(url, targetDate, createdBy, organizationId);
    }

    // ========================================================================
    // 通知（既存ロジック維持）
    // ========================================================================

    private void notifyAdminsOfUnmatchedNames(List<String> unmatchedNames, Long organizationId) {
        List<Player> admins = new ArrayList<>(playerRepository.findByRoleAndActive(Player.Role.SUPER_ADMIN));
        playerRepository.findByRoleAndActive(Player.Role.ADMIN).stream()
                .filter(a -> organizationId.equals(a.getAdminOrganizationId()))
                .forEach(admins::add);

        if (admins.isEmpty()) return;

        String nameList = String.join("、", unmatchedNames);
        String message = String.format("伝助に登録されている以下の名前がアプリに未登録です。\n%s\n\n伝助管理ページから一括登録できます。",
                nameList);
        String title = "伝助同期: 未登録者あり（" + unmatchedNames.size() + "名）";

        List<Notification> toSave = new ArrayList<>();

        for (Player admin : admins) {
            List<Notification> existing = notificationRepository.findByPlayerIdAndTypeOrderByCreatedAtDesc(
                    admin.getId(), NotificationType.DENSUKE_UNMATCHED_NAMES);

            if (!existing.isEmpty()) {
                Notification latest = existing.get(0);
                if (message.equals(latest.getMessage())) {
                    continue;
                }
                Notification active = existing.stream()
                        .filter(n -> n.getDeletedAt() == null)
                        .findFirst().orElse(null);
                if (active != null) {
                    active.setTitle(title);
                    active.setMessage(message);
                    active.setIsRead(false);
                    toSave.add(active);
                } else {
                    toSave.add(Notification.builder()
                            .playerId(admin.getId())
                            .type(NotificationType.DENSUKE_UNMATCHED_NAMES)
                            .title(title).message(message).build());
                }
            } else {
                toSave.add(Notification.builder()
                        .playerId(admin.getId())
                        .type(NotificationType.DENSUKE_UNMATCHED_NAMES)
                        .title(title).message(message).build());
            }
        }

        if (!toSave.isEmpty()) {
            notificationRepository.saveAll(toSave);
            for (Notification n : toSave) {
                Long orgId = admins.stream()
                        .filter(a -> a.getId().equals(n.getPlayerId()))
                        .map(Player::getAdminOrganizationId)
                        .filter(Objects::nonNull)
                        .findFirst().orElse(organizationId);
                notificationService.sendPushIfEnabled(
                        n.getPlayerId(), n.getType(), n.getTitle(), n.getMessage(), "/admin/densuke", orgId);
            }
        }
        log.info("Densuke unmatched names notification: {} new/updated", toSave.size());
    }

    /**
     * 蓄積された通知データをセッション×トリガー×プレイヤーでグルーピングしてまとめ送信する。
     */
    private void sendPendingNotifications(List<AdminWaitlistNotificationData> pendingNotifications) {
        // セッションID×トリガー×プレイヤーIDでグルーピング
        pendingNotifications.stream()
                .collect(Collectors.groupingBy(d -> d.getSessionId() + ":" + d.getTriggerAction() + ":" + d.getTriggerPlayerId()))
                .values()
                .forEach(dataList -> {
                    Long sessionId = dataList.get(0).getSessionId();
                    PracticeSession session = practiceSessionRepository.findById(sessionId).orElse(null);
                    if (session != null) {
                        waitlistPromotionService.sendBatchedAdminWaitlistNotifications(dataList, session);
                    }
                });

        // プレイヤー向けオファー統合通知: promotedParticipantをセッションID×プレイヤーIDでグルーピング
        pendingNotifications.stream()
                .filter(d -> d.getPromotedParticipant() != null)
                .collect(Collectors.groupingBy(d -> d.getSessionId() + ":" + d.getPromotedParticipant().getPlayerId()))
                .values()
                .forEach(dataList -> {
                    Long sessionId = dataList.get(0).getSessionId();
                    PracticeSession session = practiceSessionRepository.findById(sessionId).orElse(null);
                    if (session != null) {
                        List<PracticeParticipant> offeredParticipants = dataList.stream()
                                .map(AdminWaitlistNotificationData::getPromotedParticipant)
                                .collect(Collectors.toList());
                        AdminWaitlistNotificationData first = dataList.get(0);
                        lineNotificationService.sendConsolidatedWaitlistOfferNotification(
                                offeredParticipants, session, first.getTriggerAction(),
                                first.getTriggerPlayerId());
                    }
                });
    }
}
