package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 練習参加者管理サービス
 *
 * PracticeSessionServiceから参加者管理ロジックを分離したサービス。
 * 参加者の登録・削除・照会・統計を担当する。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PracticeParticipantService {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PlayerRepository playerRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /**
     * 特定の試合の参加者を設定（管理者用）
     */
    @Transactional
    public void setMatchParticipants(Long sessionId, Integer matchNumber, List<Long> playerIds) {
        log.info("Setting match participants for session: {}, match: {}", sessionId, matchNumber);

        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        if (matchNumber < 1 || matchNumber > session.getTotalMatches()) {
            throw new IllegalArgumentException(
                "Invalid match number: " + matchNumber + ". Must be between 1 and " + session.getTotalMatches()
            );
        }

        if (playerIds != null && !playerIds.isEmpty()) {
            List<Player> players = playerRepository.findAllById(playerIds);
            if (players.size() != playerIds.size()) {
                throw new ResourceNotFoundException("Some players not found");
            }
        }

        practiceParticipantRepository.deleteBySessionIdAndMatchNumber(sessionId, matchNumber);
        practiceParticipantRepository.flush();

        if (playerIds != null && !playerIds.isEmpty()) {
            List<PracticeParticipant> participants = playerIds.stream()
                    .map(playerId -> PracticeParticipant.builder()
                            .sessionId(sessionId)
                            .playerId(playerId)
                            .matchNumber(matchNumber)
                            .build())
                    .toList();
            practiceParticipantRepository.saveAll(participants);
        }

        log.info("Successfully set {} participants for session: {}, match: {}",
                playerIds != null ? playerIds.size() : 0, sessionId, matchNumber);
    }

    /**
     * 特定の練習セッションの参加者一覧を取得
     */
    public List<PlayerDto> getParticipants(Long sessionId) {
        log.debug("Getting participants for session id: {}", sessionId);

        if (!practiceSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("PracticeSession", sessionId);
        }

        List<Long> playerIds = practiceParticipantRepository.findPlayerIdsBySessionId(sessionId);
        List<Player> players = playerRepository.findAllById(playerIds);

        return players.stream()
                .map(PlayerDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 選手の練習参加を一括登録（月単位）
     *
     * 締め切り前: 全削除→再挿入（ステータスはPENDING）
     * 締め切り後: 抽選済み試合は変更不可。自由登録可能な試合のみ変更可能（ステータスはWON）
     */
    @Transactional
    public void registerParticipations(PracticeParticipationRequest request) {
        log.info("Registering participations for player {} in {}-{} with {} participations",
                request.getPlayerId(), request.getYear(), request.getMonth(),
                request.getParticipations().size());

        if (!playerRepository.existsById(request.getPlayerId())) {
            throw new ResourceNotFoundException("Player", request.getPlayerId());
        }

        List<Long> requestSessionIds = request.getParticipations().stream()
                .map(PracticeParticipationRequest.SessionMatchParticipation::getSessionId)
                .distinct()
                .collect(Collectors.toList());

        if (!requestSessionIds.isEmpty()) {
            List<PracticeSession> sessions = practiceSessionRepository.findAllById(requestSessionIds);
            if (sessions.size() != requestSessionIds.size()) {
                throw new ResourceNotFoundException("Some practice sessions not found");
            }
        }

        if (lotteryDeadlineHelper.isBeforeDeadline(request.getYear(), request.getMonth())) {
            registerParticipationsBeforeDeadline(request);
        } else {
            registerParticipationsAfterDeadline(request);
        }
    }

    private void registerParticipationsBeforeDeadline(PracticeParticipationRequest request) {
        List<PracticeSession> monthSessions = practiceSessionRepository
                .findByYearAndMonth(request.getYear(), request.getMonth());
        List<Long> allMonthSessionIds = monthSessions.stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        if (!allMonthSessionIds.isEmpty()) {
            practiceParticipantRepository.deleteByPlayerIdAndSessionIds(
                    request.getPlayerId(), allMonthSessionIds);
            entityManager.flush();
        }

        List<PracticeParticipant> participants = request.getParticipations().stream()
                .map(participation -> PracticeParticipant.builder()
                        .sessionId(participation.getSessionId())
                        .playerId(request.getPlayerId())
                        .matchNumber(participation.getMatchNumber())
                        .status(ParticipantStatus.PENDING)
                        .build())
                .collect(Collectors.toList());

        practiceParticipantRepository.saveAll(participants);

        log.info("Pre-deadline: registered {} participations (PENDING) for player {}",
                participants.size(), request.getPlayerId());
    }

    private void registerParticipationsAfterDeadline(PracticeParticipationRequest request) {
        Long playerId = request.getPlayerId();

        List<Long> sessionIds = request.getParticipations().stream()
                .map(PracticeParticipationRequest.SessionMatchParticipation::getSessionId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, PracticeSession> sessionsMap = practiceSessionRepository.findAllById(sessionIds)
                .stream().collect(Collectors.toMap(PracticeSession::getId, s -> s));

        int registered = 0;
        int skipped = 0;

        for (PracticeParticipationRequest.SessionMatchParticipation participation : request.getParticipations()) {
            Long sessionId = participation.getSessionId();
            Integer matchNumber = participation.getMatchNumber();
            PracticeSession session = sessionsMap.get(sessionId);

            if (practiceParticipantRepository.existsBySessionIdAndPlayerIdAndMatchNumber(
                    sessionId, playerId, matchNumber)) {
                skipped++;
                continue;
            }

            if (!isFreeRegistrationOpen(session, matchNumber)) {
                log.warn("Post-deadline: free registration not available for session {} match {}",
                        sessionId, matchNumber);
                skipped++;
                continue;
            }

            PracticeParticipant participant = PracticeParticipant.builder()
                    .sessionId(sessionId)
                    .playerId(playerId)
                    .matchNumber(matchNumber)
                    .status(ParticipantStatus.WON)
                    .build();
            practiceParticipantRepository.save(participant);
            registered++;
        }

        log.info("Post-deadline: registered {} (skipped {}) participations for player {}",
                registered, skipped, playerId);
    }

    /**
     * 自由登録が可能かどうかを判定する
     * 条件: 定員未到達 AND キャンセル待ちの人がいない
     */
    public boolean isFreeRegistrationOpen(PracticeSession session, Integer matchNumber) {
        if (session == null) return false;

        Integer capacity = session.getCapacity();
        if (capacity == null) return true;

        long wonCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                session.getId(), matchNumber, ParticipantStatus.WON);
        if (wonCount >= capacity) return false;

        boolean hasWaitlisted = practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(
                session.getId(), matchNumber, ParticipantStatus.WAITLISTED)
                || practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(
                session.getId(), matchNumber, ParticipantStatus.OFFERED);

        return !hasWaitlisted;
    }

    /**
     * 選手の特定月の参加状況を取得
     */
    public Map<Long, List<Integer>> getPlayerParticipationsByMonth(Long playerId, int year, int month) {
        log.debug("Getting participations for player {} in {}-{}", playerId, year, month);

        List<Long> sessionIds = practiceSessionRepository.findByYearAndMonth(year, month).stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        if (sessionIds.isEmpty()) {
            return Map.of();
        }

        List<PracticeParticipant> participations =
                practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds);

        return participations.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(
                        PracticeParticipant::getSessionId,
                        Collectors.mapping(PracticeParticipant::getMatchNumber, Collectors.toList())
                ));
    }

    /**
     * 選手の特定月の参加状況を取得（抽選ステータス付き）
     */
    public PlayerParticipationStatusDto getPlayerParticipationStatusByMonth(Long playerId, int year, int month) {
        log.debug("Getting participation status for player {} in {}-{}", playerId, year, month);

        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());

        if (sessionIds.isEmpty()) {
            return PlayerParticipationStatusDto.builder()
                    .participations(Map.of())
                    .lotteryExecuted(Map.of())
                    .build();
        }

        List<PracticeParticipant> participations =
                practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds);

        Map<Long, List<PlayerParticipationStatusDto.MatchParticipation>> participationMap = participations.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(
                        PracticeParticipant::getSessionId,
                        Collectors.mapping(p -> PlayerParticipationStatusDto.MatchParticipation.builder()
                                .participantId(p.getId())
                                .matchNumber(p.getMatchNumber())
                                .status(p.getStatus() != null ? p.getStatus() : ParticipantStatus.WON)
                                .waitlistNumber(p.getWaitlistNumber())
                                .build(), Collectors.toList())
                ));

        List<LotteryExecution> executions = lotteryExecutionRepository.findBySessionIdIn(sessionIds);
        Map<Long, Boolean> lotteryMap = new HashMap<>();
        for (Long sid : sessionIds) {
            lotteryMap.put(sid, false);
        }
        for (LotteryExecution exec : executions) {
            if (exec.getSessionId() != null) {
                lotteryMap.put(exec.getSessionId(), true);
            }
        }

        return PlayerParticipationStatusDto.builder()
                .participations(participationMap)
                .lotteryExecuted(lotteryMap)
                .build();
    }

    /**
     * 特定の試合に参加者を1名追加
     */
    @Transactional
    public void addParticipantToMatch(LocalDate sessionDate, Integer matchNumber, Long playerId) {
        log.info("Adding participant {} to match {} on {}", playerId, matchNumber, sessionDate);

        PracticeSession session = practiceSessionRepository.findBySessionDate(sessionDate)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", "sessionDate", sessionDate));

        if (matchNumber < 1 || matchNumber > session.getTotalMatches()) {
            throw new IllegalArgumentException(
                "Invalid match number: " + matchNumber + ". Must be between 1 and " + session.getTotalMatches()
            );
        }

        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Player", playerId);
        }

        List<PracticeParticipant> existing = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndMatchNumber(session.getId(), playerId, matchNumber);

        if (!existing.isEmpty()) {
            log.info("Player {} is already registered for match {} on {}", playerId, matchNumber, sessionDate);
            return;
        }

        PracticeParticipant participant = PracticeParticipant.builder()
                .sessionId(session.getId())
                .playerId(playerId)
                .matchNumber(matchNumber)
                .build();

        practiceParticipantRepository.save(participant);

        log.info("Successfully added participant {} to match {} on {}", playerId, matchNumber, sessionDate);
    }

    /**
     * 特定の試合から参加者を1名削除
     */
    @Transactional
    public void removeParticipantFromMatch(Long sessionId, Integer matchNumber, Long playerId) {
        log.info("Removing participant {} from session {} match {}", playerId, sessionId, matchNumber);
        practiceParticipantRepository.deleteBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber);
    }

    /**
     * 月別参加率TOP3を取得
     */
    public List<ParticipationRateDto> getParticipationRateTop3(int year, int month) {
        log.debug("Calculating participation rate top3 for {}-{}", year, month);
        List<ParticipationRateDto> allRates = computeAllParticipationRates(year, month);
        return allRates.stream()
                .sorted(Comparator.comparingDouble(ParticipationRateDto::getRate).reversed()
                        .thenComparing(Comparator.comparingInt(ParticipationRateDto::getParticipatedMatches).reversed()))
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * 特定の選手の月別参加率を取得
     */
    public ParticipationRateDto getPlayerParticipationRate(Long playerId, int year, int month) {
        List<ParticipationRateDto> allRates = computeAllParticipationRates(year, month);
        return allRates.stream()
                .filter(r -> r.getPlayerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    private List<ParticipationRateDto> computeAllParticipationRates(int year, int month) {
        LocalDate today = LocalDate.now();
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month)
                .stream()
                .filter(s -> !s.getSessionDate().isAfter(today))
                .collect(Collectors.toList());
        if (sessions.isEmpty()) {
            return List.of();
        }

        int totalScheduledMatches = sessions.stream()
                .mapToInt(s -> s.getTotalMatches() != null ? s.getTotalMatches() : 0)
                .sum();
        if (totalScheduledMatches == 0) {
            return List.of();
        }

        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        var participantsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                practiceParticipantRepository.findBySessionIdIn(sessionIds));
        var playersFuture = java.util.concurrent.CompletableFuture.supplyAsync(() ->
                playerRepository.findAllActive());

        java.util.concurrent.CompletableFuture.allOf(participantsFuture, playersFuture).join();

        List<PracticeParticipant> allParticipants = participantsFuture.join();
        Map<Long, Integer> playerParticipationCount = new HashMap<>();
        for (PracticeParticipant pp : allParticipants) {
            playerParticipationCount.merge(pp.getPlayerId(), 1, Integer::sum);
        }

        Map<Long, String> playerNames = playersFuture.join().stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));

        return playerParticipationCount.entrySet().stream()
                .map(entry -> ParticipationRateDto.builder()
                        .playerId(entry.getKey())
                        .playerName(playerNames.getOrDefault(entry.getKey(), "不明"))
                        .participatedMatches(entry.getValue())
                        .totalScheduledMatches(totalScheduledMatches)
                        .rate((double) entry.getValue() / totalScheduledMatches)
                        .build())
                .collect(Collectors.toList());
    }
}
