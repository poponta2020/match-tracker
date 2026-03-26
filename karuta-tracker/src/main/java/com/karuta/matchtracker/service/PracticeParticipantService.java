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

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 練習参加者管理サービス
 *
 * 参加者の登録・削除・照会・統計を担当する。
 * PracticeSessionServiceから委譲される。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PracticeParticipantService {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PlayerRepository playerRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;

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

    @Transactional(readOnly = true)
    public List<PlayerDto> getParticipants(Long sessionId) {
        if (!practiceSessionRepository.existsById(sessionId)) {
            throw new ResourceNotFoundException("PracticeSession", sessionId);
        }
        List<Long> playerIds = practiceParticipantRepository.findPlayerIdsBySessionId(sessionId);
        return playerRepository.findAllById(playerIds).stream()
                .map(PlayerDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void registerParticipations(PracticeParticipationRequest request) {
        log.info("Registering participations for player {} in {}-{}", request.getPlayerId(), request.getYear(), request.getMonth());

        if (!playerRepository.existsById(request.getPlayerId())) {
            throw new ResourceNotFoundException("Player", request.getPlayerId());
        }

        List<Long> requestSessionIds = request.getParticipations().stream()
                .map(PracticeParticipationRequest.SessionMatchParticipation::getSessionId)
                .distinct().collect(Collectors.toList());

        if (!requestSessionIds.isEmpty()) {
            List<PracticeSession> sessions = practiceSessionRepository.findAllById(requestSessionIds);
            if (sessions.size() != requestSessionIds.size()) {
                throw new ResourceNotFoundException("Some practice sessions not found");
            }
        }

        if (lotteryDeadlineHelper.isBeforeDeadline(request.getYear(), request.getMonth())) {
            registerBeforeDeadline(request);
        } else {
            registerAfterDeadline(request);
        }
    }

    private void registerBeforeDeadline(PracticeParticipationRequest request) {
        List<Long> allMonthSessionIds = practiceSessionRepository
                .findByYearAndMonth(request.getYear(), request.getMonth()).stream()
                .map(PracticeSession::getId).collect(Collectors.toList());

        if (!allMonthSessionIds.isEmpty()) {
            practiceParticipantRepository.deleteByPlayerIdAndSessionIds(request.getPlayerId(), allMonthSessionIds);
            practiceParticipantRepository.flush();
        }

        List<PracticeParticipant> participants = request.getParticipations().stream()
                .map(p -> PracticeParticipant.builder()
                        .sessionId(p.getSessionId()).playerId(request.getPlayerId())
                        .matchNumber(p.getMatchNumber()).status(ParticipantStatus.PENDING).build())
                .collect(Collectors.toList());
        practiceParticipantRepository.saveAll(participants);

        log.info("Pre-deadline: registered {} participations (PENDING) for player {}", participants.size(), request.getPlayerId());
    }

    private void registerAfterDeadline(PracticeParticipationRequest request) {
        Long playerId = request.getPlayerId();
        Map<Long, PracticeSession> sessionsMap = practiceSessionRepository.findAllById(
                request.getParticipations().stream()
                        .map(PracticeParticipationRequest.SessionMatchParticipation::getSessionId)
                        .distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(PracticeSession::getId, s -> s));

        // 抽選実行済みかチェック
        boolean lotteryExecuted = lotteryExecutionRepository
                .existsByTargetYearAndTargetMonthAndStatus(
                        request.getYear(), request.getMonth(),
                        LotteryExecution.ExecutionStatus.SUCCESS);

        int registered = 0, waitlisted = 0, skipped = 0;
        for (var participation : request.getParticipations()) {
            Long sessionId = participation.getSessionId();
            Integer matchNumber = participation.getMatchNumber();

            if (practiceParticipantRepository.existsBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber)) {
                skipped++; continue;
            }

            if (isFreeRegistrationOpen(sessionsMap.get(sessionId), matchNumber)) {
                // 空きあり → WON
                practiceParticipantRepository.save(PracticeParticipant.builder()
                        .sessionId(sessionId).playerId(playerId).matchNumber(matchNumber)
                        .status(ParticipantStatus.WON).build());
                registered++;
            } else if (lotteryExecuted) {
                // 抽選実行済み＋定員超過 → WAITLISTED（最後尾）
                int maxNumber = practiceParticipantRepository
                        .findMaxWaitlistNumber(sessionId, matchNumber).orElse(0);
                practiceParticipantRepository.save(PracticeParticipant.builder()
                        .sessionId(sessionId).playerId(playerId).matchNumber(matchNumber)
                        .status(ParticipantStatus.WAITLISTED)
                        .waitlistNumber(maxNumber + 1).build());
                waitlisted++;
            } else {
                skipped++;
            }
        }
        log.info("Post-deadline: registered {} won, {} waitlisted (skipped {}) for player {}",
                registered, waitlisted, skipped, playerId);
    }

    public boolean isFreeRegistrationOpen(PracticeSession session, Integer matchNumber) {
        if (session == null) return false;
        if (session.getCapacity() == null) return true;
        long wonCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                session.getId(), matchNumber, ParticipantStatus.WON);
        if (wonCount >= session.getCapacity()) return false;
        return !practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WAITLISTED)
            && !practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.OFFERED);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Integer>> getPlayerParticipationsByMonth(Long playerId, int year, int month) {
        List<Long> sessionIds = practiceSessionRepository.findByYearAndMonth(year, month).stream()
                .map(PracticeSession::getId).collect(Collectors.toList());
        if (sessionIds.isEmpty()) return Map.of();
        return practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds).stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getSessionId,
                        Collectors.mapping(PracticeParticipant::getMatchNumber, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public PlayerParticipationStatusDto getPlayerParticipationStatusByMonth(Long playerId, int year, int month) {
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        if (sessionIds.isEmpty()) {
            return PlayerParticipationStatusDto.builder().participations(Map.of()).lotteryExecuted(Map.of()).build();
        }

        Map<Long, List<PlayerParticipationStatusDto.MatchParticipation>> participationMap =
                practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds).stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getSessionId,
                        Collectors.mapping(p -> PlayerParticipationStatusDto.MatchParticipation.builder()
                                .participantId(p.getId()).matchNumber(p.getMatchNumber())
                                .status(p.getStatus() != null ? p.getStatus() : ParticipantStatus.WON)
                                .waitlistNumber(p.getWaitlistNumber()).build(), Collectors.toList())));

        Map<Long, Boolean> lotteryMap = new HashMap<>();
        sessionIds.forEach(sid -> lotteryMap.put(sid, false));
        lotteryExecutionRepository.findBySessionIdIn(sessionIds).forEach(exec -> {
            if (exec.getSessionId() != null) lotteryMap.put(exec.getSessionId(), true);
        });

        return PlayerParticipationStatusDto.builder().participations(participationMap).lotteryExecuted(lotteryMap).build();
    }

    @Transactional
    public void addParticipantToMatch(LocalDate sessionDate, Integer matchNumber, Long playerId) {
        PracticeSession session = practiceSessionRepository.findBySessionDate(sessionDate)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", "sessionDate", sessionDate));
        if (matchNumber < 1 || matchNumber > session.getTotalMatches()) {
            throw new IllegalArgumentException("Invalid match number: " + matchNumber);
        }
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Player", playerId);
        }
        if (!practiceParticipantRepository.findBySessionIdAndPlayerIdAndMatchNumber(session.getId(), playerId, matchNumber).isEmpty()) {
            return;
        }
        practiceParticipantRepository.save(PracticeParticipant.builder()
                .sessionId(session.getId()).playerId(playerId).matchNumber(matchNumber).build());
    }

    @Transactional
    public void removeParticipantFromMatch(Long sessionId, Integer matchNumber, Long playerId) {
        practiceParticipantRepository.deleteBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber);
    }

    @Transactional(readOnly = true)
    public List<ParticipationRateDto> getParticipationRateTop3(int year, int month) {
        return computeAllParticipationRates(year, month).stream()
                .sorted(java.util.Comparator.comparingDouble(ParticipationRateDto::getRate).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(ParticipationRateDto::getParticipatedMatches).reversed()))
                .limit(3).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ParticipationRateDto getPlayerParticipationRate(Long playerId, int year, int month) {
        return computeAllParticipationRates(year, month).stream()
                .filter(r -> r.getPlayerId().equals(playerId)).findFirst().orElse(null);
    }

    private List<ParticipationRateDto> computeAllParticipationRates(int year, int month) {
        LocalDate today = JstDateTimeUtil.today();
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month).stream()
                .filter(s -> !s.getSessionDate().isAfter(today)).collect(Collectors.toList());
        if (sessions.isEmpty()) return List.of();

        int totalScheduledMatches = sessions.stream()
                .mapToInt(s -> s.getTotalMatches() != null ? s.getTotalMatches() : 0).sum();
        if (totalScheduledMatches == 0) return List.of();

        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);
        List<Player> allPlayers = playerRepository.findAllActive();

        Map<Long, Integer> counts = new HashMap<>();
        allParticipants.forEach(pp -> counts.merge(pp.getPlayerId(), 1, Integer::sum));
        Map<Long, String> names = allPlayers.stream().collect(Collectors.toMap(Player::getId, Player::getName));

        return counts.entrySet().stream()
                .map(e -> ParticipationRateDto.builder()
                        .playerId(e.getKey()).playerName(names.getOrDefault(e.getKey(), "不明"))
                        .participatedMatches(e.getValue()).totalScheduledMatches(totalScheduledMatches)
                        .rate((double) e.getValue() / totalScheduledMatches).build())
                .collect(Collectors.toList());
    }
}
