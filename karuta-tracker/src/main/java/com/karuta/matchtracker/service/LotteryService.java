package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽選サービス
 *
 * 抽選アルゴリズム:
 * 1. セッションを日付昇順で処理
 * 2. 各セッション内で試合を番号昇順で処理
 * 3. 各試合について:
 *    a. 連鎖落選の適用（同セッション内の先行試合で落選した人を自動落選）
 *    b. 優先当選者の決定（同月内の別セッションで落選経験がある人を優先）
 *    c. 残り枠のランダム抽選
 *    d. 落選者にキャンセル待ち番号を付与
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LotteryService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final NotificationService notificationService;

    /**
     * 指定年月の全セッションに対して抽選を実行する
     *
     * @param year       対象年
     * @param month      対象月
     * @param executedBy 実行者ID（自動の場合null）
     * @param type       実行種別
     * @return 抽選実行履歴
     */
    @Transactional
    public LotteryExecution executeLottery(int year, int month, Long executedBy, ExecutionType type) {
        log.info("Starting lottery for {}-{} (type: {})", year, month, type);

        LotteryExecution execution = LotteryExecution.builder()
                .targetYear(year)
                .targetMonth(month)
                .executionType(type)
                .executedBy(executedBy)
                .executedAt(LocalDateTime.now())
                .status(ExecutionStatus.SUCCESS)
                .build();

        try {
            // 対象月の全セッションを日付昇順で取得
            List<PracticeSession> sessions = practiceSessionRepository
                    .findByYearAndMonth(year, month)
                    .stream()
                    .sorted(Comparator.comparing(PracticeSession::getSessionDate))
                    .collect(Collectors.toList());

            if (sessions.isEmpty()) {
                log.info("No sessions found for {}-{}", year, month);
                execution.setDetails("{\"message\": \"No sessions found\"}");
                lotteryExecutionRepository.save(execution);
                return execution;
            }

            // 月内の落選者を追跡する（セッション跨ぎの優先当選判定用）
            Set<Long> monthlyLosers = new HashSet<>();
            StringBuilder details = new StringBuilder();
            details.append("{\"sessions\": [");

            for (int i = 0; i < sessions.size(); i++) {
                PracticeSession session = sessions.get(i);
                if (i > 0) details.append(", ");
                String sessionDetail = processSession(session, monthlyLosers, execution.getId());
                details.append(sessionDetail);
            }

            details.append("]}");
            execution.setDetails(details.toString());

            // 抽選結果の通知を送信
            for (PracticeSession session : sessions) {
                List<PracticeParticipant> processed = practiceParticipantRepository
                        .findBySessionId(session.getId())
                        .stream()
                        .filter(p -> p.getStatus() == ParticipantStatus.WON
                                || p.getStatus() == ParticipantStatus.WAITLISTED)
                        .collect(Collectors.toList());
                notificationService.createLotteryResultNotifications(processed);
            }

            log.info("Lottery completed for {}-{}: {} sessions processed", year, month, sessions.size());

        } catch (Exception e) {
            log.error("Lottery failed for {}-{}", year, month, e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setDetails("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }

        lotteryExecutionRepository.save(execution);
        return execution;
    }

    /**
     * 1セッション（1日）の全試合を処理する
     */
    private String processSession(PracticeSession session, Set<Long> monthlyLosers, Long lotteryId) {
        log.debug("Processing session: {} (date: {}, capacity: {})",
                session.getId(), session.getSessionDate(), session.getCapacity());

        // このセッション内の落選者を追跡（連鎖落選用）
        Set<Long> sessionLosers = new HashSet<>();

        // このセッションの全PENDING参加者を取得
        List<PracticeParticipant> allParticipants = practiceParticipantRepository
                .findBySessionIdAndStatus(session.getId(), ParticipantStatus.PENDING);

        if (allParticipants.isEmpty()) {
            return String.format("{\"sessionId\": %d, \"date\": \"%s\", \"matches\": []}",
                    session.getId(), session.getSessionDate());
        }

        // 試合番号でグループ化し、番号昇順で処理
        Map<Integer, List<PracticeParticipant>> byMatch = allParticipants.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber,
                        TreeMap::new, Collectors.toList()));

        StringBuilder matchDetails = new StringBuilder();
        boolean first = true;

        for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
            if (!first) matchDetails.append(", ");
            first = false;

            int matchNumber = entry.getKey();
            List<PracticeParticipant> applicants = entry.getValue();

            String detail = processMatch(session, matchNumber, applicants,
                    sessionLosers, monthlyLosers, lotteryId);
            matchDetails.append(detail);
        }

        return String.format("{\"sessionId\": %d, \"date\": \"%s\", \"matches\": [%s]}",
                session.getId(), session.getSessionDate(), matchDetails);
    }

    /**
     * 1試合の抽選を処理する
     */
    private String processMatch(PracticeSession session, int matchNumber,
                                List<PracticeParticipant> applicants,
                                Set<Long> sessionLosers, Set<Long> monthlyLosers,
                                Long lotteryId) {

        Integer capacity = session.getCapacity();
        int totalApplicants = applicants.size();

        // 定員未設定 or 定員以下 → 全員当選
        if (capacity == null || totalApplicants <= capacity) {
            for (PracticeParticipant p : applicants) {
                p.setStatus(ParticipantStatus.WON);
                p.setLotteryId(lotteryId);
            }
            practiceParticipantRepository.saveAll(applicants);

            log.debug("Match {}: all {} applicants win (capacity: {})",
                    matchNumber, totalApplicants, capacity);
            return String.format("{\"match\": %d, \"applicants\": %d, \"winners\": %d, \"waitlisted\": 0}",
                    matchNumber, totalApplicants, totalApplicants);
        }

        // 定員超過 → 抽選が必要
        log.debug("Match {}: {} applicants for {} capacity - lottery required",
                matchNumber, totalApplicants, capacity);

        // Step 1: 連鎖落選の適用
        List<PracticeParticipant> cascadeLosers = new ArrayList<>();
        List<PracticeParticipant> remaining = new ArrayList<>();

        for (PracticeParticipant p : applicants) {
            if (sessionLosers.contains(p.getPlayerId())) {
                // 同セッション内で既に落選 → 連鎖落選
                cascadeLosers.add(p);
            } else {
                remaining.add(p);
            }
        }

        log.debug("Match {}: {} cascade losers, {} remaining",
                matchNumber, cascadeLosers.size(), remaining.size());

        // Step 2: 残りの参加者で抽選
        List<PracticeParticipant> winners = new ArrayList<>();
        List<PracticeParticipant> lotteryLosers = new ArrayList<>();

        if (remaining.size() <= capacity) {
            // 連鎖落選を除けば定員以下 → 残り全員当選
            winners.addAll(remaining);
        } else {
            // 優先当選者の決定（同月内の別セッションで落選経験がある人）
            List<PracticeParticipant> priorityApplicants = new ArrayList<>();
            List<PracticeParticipant> normalApplicants = new ArrayList<>();

            for (PracticeParticipant p : remaining) {
                if (monthlyLosers.contains(p.getPlayerId())) {
                    priorityApplicants.add(p);
                } else {
                    normalApplicants.add(p);
                }
            }

            log.debug("Match {}: {} priority, {} normal applicants",
                    matchNumber, priorityApplicants.size(), normalApplicants.size());

            if (priorityApplicants.size() >= capacity) {
                // 優先当選者が定員以上 → 優先当選者同士で抽選
                Collections.shuffle(priorityApplicants);
                winners.addAll(priorityApplicants.subList(0, capacity));
                lotteryLosers.addAll(priorityApplicants.subList(capacity, priorityApplicants.size()));
                lotteryLosers.addAll(normalApplicants); // 一般申込者は全員落選
            } else {
                // 優先当選者は全員当選 + 残り枠を一般からランダム
                winners.addAll(priorityApplicants);
                int remainingSlots = capacity - priorityApplicants.size();

                Collections.shuffle(normalApplicants);
                winners.addAll(normalApplicants.subList(0, Math.min(remainingSlots, normalApplicants.size())));
                if (normalApplicants.size() > remainingSlots) {
                    lotteryLosers.addAll(normalApplicants.subList(remainingSlots, normalApplicants.size()));
                }
            }
        }

        // Step 3: ステータス更新 - 当選者
        for (PracticeParticipant p : winners) {
            p.setStatus(ParticipantStatus.WON);
            p.setLotteryId(lotteryId);
        }

        // Step 4: 落選者（連鎖落選 + 抽選落選）にキャンセル待ち番号を付与
        List<PracticeParticipant> allLosers = new ArrayList<>();
        allLosers.addAll(cascadeLosers);
        allLosers.addAll(lotteryLosers);

        // ランダムにキャンセル待ち番号を割り当て
        Collections.shuffle(allLosers);
        for (int i = 0; i < allLosers.size(); i++) {
            PracticeParticipant p = allLosers.get(i);
            p.setStatus(ParticipantStatus.WAITLISTED);
            p.setWaitlistNumber(i + 1);
            p.setLotteryId(lotteryId);
        }

        // Step 5: 月内・セッション内の落選者リストを更新
        for (PracticeParticipant p : allLosers) {
            sessionLosers.add(p.getPlayerId());
            monthlyLosers.add(p.getPlayerId());
        }

        // 全参加者を保存
        List<PracticeParticipant> all = new ArrayList<>();
        all.addAll(winners);
        all.addAll(allLosers);
        practiceParticipantRepository.saveAll(all);

        log.info("Match {}: {} winners, {} waitlisted (from {} applicants, capacity {})",
                matchNumber, winners.size(), allLosers.size(), totalApplicants, capacity);

        return String.format("{\"match\": %d, \"applicants\": %d, \"winners\": %d, \"waitlisted\": %d}",
                matchNumber, totalApplicants, winners.size(), allLosers.size());
    }

    /**
     * セッション単位で再抽選を実行する
     *
     * - キャンセル待ちから繰り上がった人（WON + respondedAt != null）は維持
     * - それ以外の当選者・キャンセル待ちをリセットして再抽選
     * - 同月内の他セッションの落選者は優先当選の対象
     */
    @Transactional
    public LotteryExecution reExecuteLottery(Long sessionId, Long executedBy) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        int year = session.getSessionDate().getYear();
        int month = session.getSessionDate().getMonthValue();

        log.info("Re-executing lottery for session {} (date: {})", sessionId, session.getSessionDate());

        LotteryExecution execution = LotteryExecution.builder()
                .targetYear(year)
                .targetMonth(month)
                .executionType(ExecutionType.MANUAL_RELOTTERY)
                .sessionId(sessionId)
                .executedBy(executedBy)
                .executedAt(LocalDateTime.now())
                .status(ExecutionStatus.SUCCESS)
                .build();

        try {
            // このセッションの全参加者を取得
            List<PracticeParticipant> allParticipants = practiceParticipantRepository
                    .findBySessionId(sessionId);

            // 繰り上がり承諾者を特定（維持する）
            List<PracticeParticipant> promoted = allParticipants.stream()
                    .filter(p -> p.getStatus() == ParticipantStatus.WON && p.getRespondedAt() != null)
                    .collect(Collectors.toList());

            // キャンセル済みを除外
            List<PracticeParticipant> cancelledParticipants = allParticipants.stream()
                    .filter(p -> p.getStatus() == ParticipantStatus.CANCELLED)
                    .collect(Collectors.toList());

            // 再抽選対象: 繰り上がり者とキャンセル者以外
            Set<Long> promotedIds = promoted.stream()
                    .map(PracticeParticipant::getId)
                    .collect(Collectors.toSet());
            Set<Long> cancelledIds = cancelledParticipants.stream()
                    .map(PracticeParticipant::getId)
                    .collect(Collectors.toSet());

            List<PracticeParticipant> reLotteryTargets = allParticipants.stream()
                    .filter(p -> !promotedIds.contains(p.getId()) && !cancelledIds.contains(p.getId()))
                    .collect(Collectors.toList());

            // 対象者をPENDINGにリセット
            for (PracticeParticipant p : reLotteryTargets) {
                p.setStatus(ParticipantStatus.PENDING);
                p.setWaitlistNumber(null);
                p.setLotteryId(null);
                p.setOfferedAt(null);
                p.setOfferDeadline(null);
                p.setRespondedAt(null);
            }
            practiceParticipantRepository.saveAll(reLotteryTargets);

            // 月内の他セッションでの落選者を取得（優先当選判定用）
            Set<Long> monthlyLosers = new HashSet<>(
                    practiceParticipantRepository.findMonthlyLoserPlayerIds(year, month, sessionId));

            // セッション内落選者を追跡
            Set<Long> sessionLosers = new HashSet<>();

            // 繰り上がり者の試合番号ごとのカウントを考慮して定員調整
            Map<Integer, Long> promotedCountByMatch = promoted.stream()
                    .filter(p -> p.getMatchNumber() != null)
                    .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber, Collectors.counting()));

            // 試合番号でグループ化し再抽選
            Map<Integer, List<PracticeParticipant>> byMatch = reLotteryTargets.stream()
                    .filter(p -> p.getMatchNumber() != null && p.getStatus() == ParticipantStatus.PENDING)
                    .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber,
                            TreeMap::new, Collectors.toList()));

            // 一時的にcapacityを調整して再抽選
            Integer originalCapacity = session.getCapacity();

            for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
                int matchNumber = entry.getKey();
                long promotedInMatch = promotedCountByMatch.getOrDefault(matchNumber, 0L);

                if (originalCapacity != null && promotedInMatch > 0) {
                    // 繰り上がり者分の枠を差し引いた仮想的な定員で抽選
                    session.setCapacity(originalCapacity - (int) promotedInMatch);
                }

                processMatch(session, matchNumber, entry.getValue(),
                        sessionLosers, monthlyLosers, execution.getId());

                // 定員を戻す
                session.setCapacity(originalCapacity);
            }

            execution.setDetails(String.format(
                    "{\"sessionId\": %d, \"promoted_kept\": %d, \"relottery_targets\": %d}",
                    sessionId, promoted.size(), reLotteryTargets.size()));

            log.info("Re-lottery completed for session {}: {} promoted kept, {} re-lotteried",
                    sessionId, promoted.size(), reLotteryTargets.size());

        } catch (Exception e) {
            log.error("Re-lottery failed for session {}", sessionId, e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setDetails("{\"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        }

        lotteryExecutionRepository.save(execution);
        return execution;
    }
}
