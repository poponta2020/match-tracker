package com.karuta.matchtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.AdminEditParticipantsRequest;
import com.karuta.matchtracker.dto.LotteryResultDto;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final PlayerRepository playerRepository;
    private final VenueRepository venueRepository;
    private final NotificationService notificationService;
    private final SystemSettingService systemSettingService;
    private final WaitlistPromotionService waitlistPromotionService;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final ObjectMapper objectMapper;

    // details JSON 用の内部レコード
    record LotteryDetails(List<SessionDetail> sessions) {}
    record SessionDetail(Long sessionId, LocalDate date, List<MatchDetail> matches) {}
    record MatchDetail(int match, int applicants, int winners, int waitlisted) {}
    record ReLotteryDetails(Long sessionId, int promotedKept, int relotteryTargets) {}
    record ErrorDetail(String error) {}
    record MessageDetail(String message) {}

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
    public LotteryExecution executeLottery(int year, int month, Long executedBy, ExecutionType type, Long organizationId) {
        log.info("Starting lottery for {}-{} (type: {})", year, month, type);

        LotteryExecution execution = LotteryExecution.builder()
                .targetYear(year)
                .targetMonth(month)
                .executionType(type)
                .executedBy(executedBy)
                .executedAt(JstDateTimeUtil.now())
                .status(ExecutionStatus.SUCCESS)
                .build();

        // IDを確保するために先にsave（details は処理後に更新して再save）
        lotteryExecutionRepository.save(execution);

        try {
            // 対象月のセッションを日付昇順で取得（団体フィルタ付き）
            List<PracticeSession> sessions;
            if (organizationId != null) {
                sessions = practiceSessionRepository
                        .findByYearAndMonthAndOrganizationId(year, month, organizationId);
            } else {
                sessions = practiceSessionRepository.findByYearAndMonth(year, month);
            }
            sessions = sessions.stream()
                    .sorted(Comparator.comparing(PracticeSession::getSessionDate))
                    .collect(Collectors.toList());

            if (sessions.isEmpty()) {
                log.info("No sessions found for {}-{}", year, month);
                execution.setDetails(toJson(new MessageDetail("No sessions found")));
                return lotteryExecutionRepository.save(execution);
            }

            // 月内の落選者を追跡する（セッション跨ぎの優先当選判定用）
            Set<Long> monthlyLosers = new HashSet<>();
            List<SessionDetail> sessionDetails = new ArrayList<>();

            for (PracticeSession session : sessions) {
                SessionDetail sessionDetail = processSession(session, monthlyLosers, execution.getId());
                sessionDetails.add(sessionDetail);
            }

            execution.setDetails(toJson(new LotteryDetails(sessionDetails)));

            log.info("Lottery completed for {}-{}: {} sessions processed", year, month, sessions.size());

        } catch (Exception e) {
            log.error("Lottery failed for {}-{}", year, month, e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setDetails(toJson(new ErrorDetail(e.getMessage())));
        }

        return lotteryExecutionRepository.save(execution);
    }

    /**
     * 1セッション（1日）の全試合を処理する
     */
    private SessionDetail processSession(PracticeSession session, Set<Long> monthlyLosers, Long lotteryId) {
        log.debug("Processing session: {} (date: {}, capacity: {})",
                session.getId(), session.getSessionDate(), session.getCapacity());

        // このセッション内の落選者を追跡（連鎖落選用）
        Set<Long> sessionLosers = new HashSet<>();

        // このセッションの全PENDING参加者を取得
        List<PracticeParticipant> allParticipants = practiceParticipantRepository
                .findBySessionIdAndStatus(session.getId(), ParticipantStatus.PENDING);

        if (allParticipants.isEmpty()) {
            return new SessionDetail(session.getId(), session.getSessionDate(), List.of());
        }

        // 試合番号でグループ化し、番号昇順で処理
        Map<Integer, List<PracticeParticipant>> byMatch = allParticipants.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber,
                        TreeMap::new, Collectors.toList()));

        List<MatchDetail> matchDetails = new ArrayList<>();

        // 前試合のキャンセル待ち順番を追跡（連続試合で順番を引き継ぐため）
        Map<Long, Integer> prevWaitlistOrder = new HashMap<>();
        int prevMatchNumber = -1;

        for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
            int matchNumber = entry.getKey();
            List<PracticeParticipant> applicants = entry.getValue();

            // 連続する試合番号の場合のみ前試合の順番を引き継ぐ
            Map<Long, Integer> inheritedOrder = (matchNumber == prevMatchNumber + 1)
                    ? prevWaitlistOrder : Collections.emptyMap();
            Map<Long, Integer> currentWaitlistOrder = new HashMap<>();

            MatchDetail detail = processMatch(session, matchNumber, applicants,
                    sessionLosers, monthlyLosers, lotteryId, inheritedOrder, currentWaitlistOrder);
            matchDetails.add(detail);

            prevWaitlistOrder = currentWaitlistOrder;
            prevMatchNumber = matchNumber;
        }

        return new SessionDetail(session.getId(), session.getSessionDate(), matchDetails);
    }

    /**
     * 1試合の抽選を処理する
     */
    private MatchDetail processMatch(PracticeSession session, int matchNumber,
                                List<PracticeParticipant> applicants,
                                Set<Long> sessionLosers, Set<Long> monthlyLosers,
                                Long lotteryId,
                                Map<Long, Integer> previousMatchWaitlistOrder,
                                Map<Long, Integer> currentMatchWaitlistOrder) {

        Integer capacity = session.getCapacity();
        int totalApplicants = applicants.size();

        // 定員未設定 or 定員以下 → 全員当選
        if (capacity == null || totalApplicants <= capacity) {
            for (PracticeParticipant p : applicants) {
                p.setStatus(ParticipantStatus.WON);
                p.setDirty(true);
                p.setLotteryId(lotteryId);
            }
            practiceParticipantRepository.saveAll(applicants);

            log.debug("Match {}: all {} applicants win (capacity: {})",
                    matchNumber, totalApplicants, capacity);
            return new MatchDetail(matchNumber, totalApplicants, totalApplicants, 0);
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

            // 一般枠の最低保証を計算（セッションのorganizationIdを使用）
            int normalReservePercent = systemSettingService.getLotteryNormalReservePercent(session.getOrganizationId());
            int normalReserve = 0;
            if (normalReservePercent > 0 && !normalApplicants.isEmpty() && !priorityApplicants.isEmpty()) {
                normalReserve = Math.max(1, (int) Math.ceil(capacity * normalReservePercent / 100.0));
                // 一般申込者数を超えないようにする
                normalReserve = Math.min(normalReserve, normalApplicants.size());
                // 定員を超えないようにする
                normalReserve = Math.min(normalReserve, capacity);
            }
            int prioritySlots = capacity - normalReserve;

            if (priorityApplicants.size() >= prioritySlots && normalReserve > 0) {
                // 優先枠と一般枠に分けて抽選
                Collections.shuffle(priorityApplicants);
                winners.addAll(priorityApplicants.subList(0, prioritySlots));
                lotteryLosers.addAll(priorityApplicants.subList(prioritySlots, priorityApplicants.size()));

                Collections.shuffle(normalApplicants);
                winners.addAll(normalApplicants.subList(0, normalReserve));
                if (normalApplicants.size() > normalReserve) {
                    lotteryLosers.addAll(normalApplicants.subList(normalReserve, normalApplicants.size()));
                }
            } else if (priorityApplicants.size() >= capacity) {
                // 一般枠なし（一般申込者0 or 設定0%）で優先者が定員以上
                Collections.shuffle(priorityApplicants);
                winners.addAll(priorityApplicants.subList(0, capacity));
                lotteryLosers.addAll(priorityApplicants.subList(capacity, priorityApplicants.size()));
                lotteryLosers.addAll(normalApplicants);
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
            p.setDirty(true);
            p.setLotteryId(lotteryId);
        }

        // Step 4: 落選者（連鎖落選 + 抽選落選）にキャンセル待ち番号を付与
        List<PracticeParticipant> allLosers = new ArrayList<>();
        allLosers.addAll(cascadeLosers);
        allLosers.addAll(lotteryLosers);

        // キャンセル待ち番号を割り当て（連続試合では前試合の順番を引き継ぐ）
        List<PracticeParticipant> withPrevOrder = new ArrayList<>();
        List<PracticeParticipant> withoutPrevOrder = new ArrayList<>();

        for (PracticeParticipant p : allLosers) {
            if (previousMatchWaitlistOrder.containsKey(p.getPlayerId())) {
                withPrevOrder.add(p);
            } else {
                withoutPrevOrder.add(p);
            }
        }

        // 前試合の順番を維持
        withPrevOrder.sort(Comparator.comparingInt(p -> previousMatchWaitlistOrder.get(p.getPlayerId())));

        // 新規落選者はランダム
        Collections.shuffle(withoutPrevOrder);

        // 結合して連番を付与
        List<PracticeParticipant> orderedLosers = new ArrayList<>();
        orderedLosers.addAll(withPrevOrder);
        orderedLosers.addAll(withoutPrevOrder);

        for (int i = 0; i < orderedLosers.size(); i++) {
            PracticeParticipant p = orderedLosers.get(i);
            p.setStatus(ParticipantStatus.WAITLISTED);
            p.setDirty(true);
            p.setWaitlistNumber(i + 1);
            p.setLotteryId(lotteryId);
            currentMatchWaitlistOrder.put(p.getPlayerId(), i + 1);
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

        return new MatchDetail(matchNumber, totalApplicants, winners.size(), allLosers.size());
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
                .executedAt(JstDateTimeUtil.now())
                .status(ExecutionStatus.SUCCESS)
                .build();

        // IDを確保するために先にsave（details は処理後に更新して再save）
        lotteryExecutionRepository.save(execution);

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

            // 前試合のキャンセル待ち順番を追跡（連続試合で順番を引き継ぐため）
            Map<Long, Integer> prevWaitlistOrder = new HashMap<>();
            int prevMatchNumber = -1;

            for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
                int matchNumber = entry.getKey();
                long promotedInMatch = promotedCountByMatch.getOrDefault(matchNumber, 0L);

                if (originalCapacity != null && promotedInMatch > 0) {
                    // 繰り上がり者分の枠を差し引いた仮想的な定員で抽選
                    session.setCapacity(originalCapacity - (int) promotedInMatch);
                }

                Map<Long, Integer> inheritedOrder = (matchNumber == prevMatchNumber + 1)
                        ? prevWaitlistOrder : Collections.emptyMap();
                Map<Long, Integer> currentWaitlistOrder = new HashMap<>();

                processMatch(session, matchNumber, entry.getValue(),
                        sessionLosers, monthlyLosers, execution.getId(),
                        inheritedOrder, currentWaitlistOrder);

                prevWaitlistOrder = currentWaitlistOrder;
                prevMatchNumber = matchNumber;

                // 定員を戻す
                session.setCapacity(originalCapacity);
            }

            execution.setDetails(toJson(new ReLotteryDetails(
                    sessionId, promoted.size(), reLotteryTargets.size())));

            log.info("Re-lottery completed for session {}: {} promoted kept, {} re-lotteried",
                    sessionId, promoted.size(), reLotteryTargets.size());

        } catch (Exception e) {
            log.error("Re-lottery failed for session {}", sessionId, e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setDetails(toJson(new ErrorDetail(e.getMessage())));
        }

        return lotteryExecutionRepository.save(execution);
    }

    /**
     * セッションの抽選結果DTOを組み立てる
     */
    public LotteryResultDto buildLotteryResult(PracticeSession session) {
        List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionIdOrderByMatchAndStatus(session.getId());

        // プレイヤー情報をまとめて取得
        Set<Long> playerIds = participants.stream()
                .map(PracticeParticipant::getPlayerId)
                .collect(Collectors.toSet());
        Map<Long, Player> playersMap = playerRepository.findAllById(playerIds)
                .stream().collect(Collectors.toMap(Player::getId, p -> p));

        // 会場名を取得
        String venueName = null;
        if (session.getVenueId() != null) {
            venueName = venueRepository.findById(session.getVenueId())
                    .map(Venue::getName)
                    .orElse(null);
        }

        // 試合番号でグループ化
        Map<Integer, List<PracticeParticipant>> byMatch = participants.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber, TreeMap::new, Collectors.toList()));

        Map<Integer, LotteryResultDto.MatchResult> matchResults = new TreeMap<>();
        int capacity = session.getCapacity() != null ? session.getCapacity() : Integer.MAX_VALUE;

        for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
            List<LotteryResultDto.ParticipantResult> winners = new ArrayList<>();
            List<LotteryResultDto.ParticipantResult> waitlisted = new ArrayList<>();

            for (PracticeParticipant p : entry.getValue()) {
                Player player = playersMap.get(p.getPlayerId());
                if (player == null) continue;

                LotteryResultDto.ParticipantResult result = LotteryResultDto.ParticipantResult.builder()
                        .playerId(p.getPlayerId())
                        .playerName(player.getName())
                        .kyuRank(player.getKyuRank())
                        .danRank(player.getDanRank())
                        .status(p.getStatus())
                        .waitlistNumber(p.getWaitlistNumber())
                        .build();

                if (p.getStatus() == ParticipantStatus.WON) {
                    winners.add(result);
                } else if (p.getStatus() == ParticipantStatus.WAITLISTED
                        || p.getStatus() == ParticipantStatus.OFFERED
                        || p.getStatus() == ParticipantStatus.DECLINED) {
                    waitlisted.add(result);
                }
            }

            waitlisted.sort(Comparator.comparing(r -> r.getWaitlistNumber() != null ? r.getWaitlistNumber() : Integer.MAX_VALUE));

            matchResults.put(entry.getKey(), LotteryResultDto.MatchResult.builder()
                    .matchNumber(entry.getKey())
                    .capacity(capacity)
                    .lotteryRequired(entry.getValue().size() > capacity)
                    .winners(winners)
                    .waitlisted(waitlisted)
                    .build());
        }

        return LotteryResultDto.builder()
                .sessionId(session.getId())
                .sessionDate(session.getSessionDate())
                .venueName(venueName)
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .capacity(session.getCapacity())
                .matchResults(matchResults)
                .build();
    }

    /**
     * 管理者による参加者手動編集
     *
     * - 参加者追加
     * - ステータス変更（WON→CANCELLEDの場合は繰り上げフローを発動）
     * - キャンセル待ち順番変更
     */
    @Transactional
    public void editParticipants(AdminEditParticipantsRequest request) {
        // 参加者追加
        if (request.getAdditions() != null) {
            for (AdminEditParticipantsRequest.AddParticipant add : request.getAdditions()) {
                PracticeParticipant participant = PracticeParticipant.builder()
                        .sessionId(request.getSessionId())
                        .playerId(add.getPlayerId())
                        .matchNumber(request.getMatchNumber())
                        .status(add.getStatus() != null ? add.getStatus() : ParticipantStatus.WON)
                        .build();
                practiceParticipantRepository.save(participant);
            }
        }

        // ステータス変更
        if (request.getStatusChanges() != null) {
            for (AdminEditParticipantsRequest.StatusChange change : request.getStatusChanges()) {
                PracticeParticipant p = practiceParticipantRepository.findById(change.getParticipantId())
                        .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", change.getParticipantId()));
                ParticipantStatus oldStatus = p.getStatus();
                p.setStatus(change.getNewStatus());
                p.setDirty(true);
                if (change.getWaitlistNumber() != null) {
                    p.setWaitlistNumber(change.getWaitlistNumber());
                }
                practiceParticipantRepository.save(p);

                // WON → CANCELLED の場合、繰り上げフローを発動（当日は除く）
                if (oldStatus == ParticipantStatus.WON && change.getNewStatus() == ParticipantStatus.CANCELLED) {
                    PracticeSession session = practiceSessionRepository.findById(p.getSessionId())
                            .orElse(null);
                    if (session != null && !lotteryDeadlineHelper.isToday(session.getSessionDate())) {
                        waitlistPromotionService.promoteNextWaitlisted(
                                p.getSessionId(), p.getMatchNumber(), session.getSessionDate());
                    }
                }
            }
        }

        // キャンセル待ち順番変更
        if (request.getWaitlistReorders() != null) {
            for (AdminEditParticipantsRequest.WaitlistReorder reorder : request.getWaitlistReorders()) {
                PracticeParticipant p = practiceParticipantRepository.findById(reorder.getParticipantId())
                        .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", reorder.getParticipantId()));
                p.setWaitlistNumber(reorder.getNewWaitlistNumber());
                practiceParticipantRepository.save(p);
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize details to JSON", e);
            return "{\"error\":\"JSON serialization failed\"}";
        }
    }
}
