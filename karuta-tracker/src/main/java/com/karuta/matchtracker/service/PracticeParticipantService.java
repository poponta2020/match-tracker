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
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.entity.PlayerOrganization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 練習参加者管理サービス
 *
 * 参加者の登録・削除・照会・統計を担当する。
 * PracticeSessionServiceから委譲される。
 */
@Service
@Slf4j
public class PracticeParticipantService {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PlayerRepository playerRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final DensukeSyncService densukeSyncService;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final LineNotificationService lineNotificationService;
    private final OrganizationService organizationService;

    public PracticeParticipantService(
            PracticeParticipantRepository practiceParticipantRepository,
            PracticeSessionRepository practiceSessionRepository,
            PlayerRepository playerRepository,
            LotteryExecutionRepository lotteryExecutionRepository,
            LotteryDeadlineHelper lotteryDeadlineHelper,
            @Lazy DensukeSyncService densukeSyncService,
            PlayerOrganizationRepository playerOrganizationRepository,
            LineNotificationService lineNotificationService,
            OrganizationService organizationService) {
        this.practiceParticipantRepository = practiceParticipantRepository;
        this.practiceSessionRepository = practiceSessionRepository;
        this.playerRepository = playerRepository;
        this.lotteryExecutionRepository = lotteryExecutionRepository;
        this.lotteryDeadlineHelper = lotteryDeadlineHelper;
        this.densukeSyncService = densukeSyncService;
        this.playerOrganizationRepository = playerOrganizationRepository;
        this.lineNotificationService = lineNotificationService;
        this.organizationService = organizationService;
    }

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

        List<Long> uniquePlayerIds = playerIds == null ? List.of() : playerIds.stream().distinct().toList();
        if (!uniquePlayerIds.isEmpty()) {
            List<Player> players = playerRepository.findAllById(uniquePlayerIds);
            if (players.size() != uniquePlayerIds.size()) {
                throw new ResourceNotFoundException("Some players not found");
            }
        }

        practiceParticipantRepository.softDeleteBySessionIdAndMatchNumber(sessionId, matchNumber, JstDateTimeUtil.now());
        practiceParticipantRepository.flush();

        if (!uniquePlayerIds.isEmpty()) {
            for (Long playerId : uniquePlayerIds) {
                saveOrReuseParticipant(sessionId, playerId, matchNumber, ParticipantStatus.WON, null);
            }
        }

        log.info("Successfully set {} participants for session: {}, match: {}",
                uniquePlayerIds.size(), sessionId, matchNumber);
        densukeSyncService.triggerWriteAsync();
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

        List<PracticeSession> sessions = List.of();
        if (!requestSessionIds.isEmpty()) {
            sessions = practiceSessionRepository.findAllById(requestSessionIds);
            if (sessions.size() != requestSessionIds.size()) {
                throw new ResourceNotFoundException("Some practice sessions not found");
            }
        }

        // セッションからorganizationIdとDeadlineTypeを取得
        Long organizationId = null;
        if (!sessions.isEmpty()) {
            organizationId = sessions.get(0).getOrganizationId();
        }

        // リクエスト内の全団体に対して未所属であれば自動的に所属させる
        sessions.stream()
                .map(PracticeSession::getOrganizationId)
                .distinct()
                .forEach(orgId -> organizationService.ensurePlayerBelongsToOrganization(request.getPlayerId(), orgId));

        com.karuta.matchtracker.entity.DeadlineType deadlineType = lotteryDeadlineHelper.getDeadlineType(organizationId);

        if (deadlineType == com.karuta.matchtracker.entity.DeadlineType.SAME_DAY) {
            // わすらもち会: 抽選なし、常に先着順（即WON/WAITLISTED）
            registerSameDay(request);
        } else if (lotteryDeadlineHelper.isBeforeDeadline(request.getYear(), request.getMonth(), organizationId)) {
            // 北大: 締切前はPENDING
            registerBeforeDeadline(request);
        } else {
            // 北大: 締切後はWON/WAITLISTED
            registerAfterDeadline(request);
        }
        densukeSyncService.triggerWriteAsync();
    }

    /**
     * SAME_DAYタイプ（わすらもち会）の参加登録
     * 抽選なし・先着順: 空きあり→即WON、定員超過→即WAITLISTED
     */
    private void registerSameDay(PracticeParticipationRequest request) {
        Long playerId = request.getPlayerId();

        // 対象月の全セッションIDを取得して既存登録を削除（月単位の一括更新）
        List<Long> allMonthSessionIds = practiceSessionRepository
                .findByYearAndMonth(request.getYear(), request.getMonth()).stream()
                .map(PracticeSession::getId).collect(Collectors.toList());

        if (!allMonthSessionIds.isEmpty()) {
            practiceParticipantRepository.softDeleteByPlayerIdAndSessionIds(
                    playerId, allMonthSessionIds, JstDateTimeUtil.now());
            practiceParticipantRepository.flush();
        }

        Map<Long, PracticeSession> sessionsMap = practiceSessionRepository.findAllById(
                request.getParticipations().stream()
                        .map(PracticeParticipationRequest.SessionMatchParticipation::getSessionId)
                        .distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(PracticeSession::getId, s -> s));

        int registered = 0, waitlisted = 0;
        Set<String> processedKeys = new HashSet<>();
        for (var participation : request.getParticipations()) {
            Long sessionId = participation.getSessionId();
            Integer matchNumber = participation.getMatchNumber();
            if (!processedKeys.add(participationKey(sessionId, matchNumber))) {
                continue;
            }

            if (isFreeRegistrationOpen(sessionsMap.get(sessionId), matchNumber)) {
                saveOrReuseParticipant(sessionId, playerId, matchNumber, ParticipantStatus.WON, null);
                notifySameDayJoinIfApplicable(sessionsMap.get(sessionId), matchNumber, playerId);
                registered++;
            } else {
                int maxNumber = practiceParticipantRepository
                        .findMaxWaitlistNumber(sessionId, matchNumber).orElse(0);
                saveOrReuseParticipant(sessionId, playerId, matchNumber, ParticipantStatus.WAITLISTED, maxNumber + 1);
                waitlisted++;
            }
        }
        log.info("SAME_DAY: registered {} won, {} waitlisted for player {}", registered, waitlisted, playerId);
    }

    private void registerBeforeDeadline(PracticeParticipationRequest request) {
        List<Long> allMonthSessionIds = practiceSessionRepository
                .findByYearAndMonth(request.getYear(), request.getMonth()).stream()
                .map(PracticeSession::getId).collect(Collectors.toList());

        if (!allMonthSessionIds.isEmpty()) {
            practiceParticipantRepository.softDeleteByPlayerIdAndSessionIds(
                    request.getPlayerId(), allMonthSessionIds, JstDateTimeUtil.now());
            practiceParticipantRepository.flush();
        }

        int registered = 0;
        Set<String> processedKeys = new HashSet<>();
        for (var participation : request.getParticipations()) {
            Long sessionId = participation.getSessionId();
            Integer matchNumber = participation.getMatchNumber();
            if (!processedKeys.add(participationKey(sessionId, matchNumber))) {
                continue;
            }
            saveOrReuseParticipant(sessionId, request.getPlayerId(), matchNumber, ParticipantStatus.PENDING, null);
            registered++;
        }

        log.info("Pre-deadline: registered {} participations (PENDING) for player {}", registered, request.getPlayerId());
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
        Set<String> processedKeys = new HashSet<>();
        for (var participation : request.getParticipations()) {
            Long sessionId = participation.getSessionId();
            Integer matchNumber = participation.getMatchNumber();
            if (!processedKeys.add(participationKey(sessionId, matchNumber))) {
                skipped++;
                continue;
            }

            if (practiceParticipantRepository.existsActiveBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber)) {
                skipped++; continue;
            }

            if (isFreeRegistrationOpen(sessionsMap.get(sessionId), matchNumber)) {
                // 空きあり → WON
                saveOrReuseParticipant(sessionId, playerId, matchNumber, ParticipantStatus.WON, null);
                notifySameDayJoinIfApplicable(sessionsMap.get(sessionId), matchNumber, playerId);
                registered++;
            } else if (lotteryExecuted) {
                // 抽選実行済み＋定員超過 → WAITLISTED（最後尾）
                int maxNumber = practiceParticipantRepository
                        .findMaxWaitlistNumber(sessionId, matchNumber).orElse(0);
                saveOrReuseParticipant(sessionId, playerId, matchNumber, ParticipantStatus.WAITLISTED, maxNumber + 1);
                waitlisted++;
            } else {
                skipped++;
            }
        }
        log.info("Post-deadline: registered {} won, {} waitlisted (skipped {}) for player {}",
                registered, waitlisted, skipped, playerId);
    }

    /**
     * 12:00以降にアプリ経由でWON登録された場合、その試合のWONメンバーに参加通知を送信する。
     */
    private void notifySameDayJoinIfApplicable(PracticeSession session, int matchNumber, Long playerId) {
        if (session == null) return;
        if (!lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())) return;

        Player player = playerRepository.findById(playerId).orElse(null);
        String playerName = player != null ? player.getName() : "不明";

        lineNotificationService.sendSameDayJoinNotification(session, matchNumber, playerName, playerId);
    }

    // NOTE:
    // This method performs "find then save", so concurrent requests for the same
    // (session_id, player_id, match_number) may still race and one request can hit
    // the unique constraint. In that case, DB consistency is still protected by
    // uk_session_player_match.
    private void saveOrReuseParticipant(Long sessionId, Long playerId, Integer matchNumber,
                                        ParticipantStatus status, Integer waitlistNumber) {
        PracticeParticipant participant = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber)
                .stream()
                .findFirst()
                .orElseGet(() -> PracticeParticipant.builder()
                        .sessionId(sessionId)
                        .playerId(playerId)
                        .matchNumber(matchNumber)
                        .build());

        resetParticipationForReregistration(participant);
        participant.setStatus(status);
        participant.setWaitlistNumber(waitlistNumber);
        participant.setDirty(true);
        practiceParticipantRepository.save(participant);
    }

    private void resetParticipationForReregistration(PracticeParticipant participant) {
        participant.setWaitlistNumber(null);
        participant.setLotteryId(null);
        participant.setCancelReason(null);
        participant.setCancelReasonDetail(null);
        participant.setCancelledAt(null);
        participant.setOfferedAt(null);
        participant.setOfferDeadline(null);
        participant.setRespondedAt(null);
    }

    private String participationKey(Long sessionId, Integer matchNumber) {
        return sessionId + ":" + matchNumber;
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
            return PlayerParticipationStatusDto.builder()
                    .participations(Map.of())
                    .lotteryExecuted(Map.of())
                    .beforeDeadline(lotteryDeadlineHelper.isBeforeDeadline(year, month, null))
                    .build();
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

        // セッションからorganizationIdを取得
        Long orgId = sessions.isEmpty() ? null : sessions.get(0).getOrganizationId();
        boolean beforeDeadline = lotteryDeadlineHelper.isBeforeDeadline(year, month, orgId);

        return PlayerParticipationStatusDto.builder()
                .participations(participationMap)
                .lotteryExecuted(lotteryMap)
                .beforeDeadline(beforeDeadline)
                .build();
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
        if (practiceParticipantRepository.existsActiveBySessionIdAndPlayerIdAndMatchNumber(session.getId(), playerId, matchNumber)) {
            return;
        }
        saveOrReuseParticipant(session.getId(), playerId, matchNumber, ParticipantStatus.WON, null);
        densukeSyncService.triggerWriteAsync();
    }

    @Transactional
    public void removeParticipantFromMatch(Long sessionId, Integer matchNumber, Long playerId) {
        List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber);
        for (PracticeParticipant p : participants) {
            p.setStatus(ParticipantStatus.CANCELLED);
            p.setDirty(true);
            p.setCancelledAt(JstDateTimeUtil.now());
        }
        practiceParticipantRepository.saveAll(participants);
        densukeSyncService.triggerWriteAsync();
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

    // === 団体フィルタ対応メソッド ===

    @Transactional(readOnly = true)
    public List<ParticipationRateDto> getParticipationRateTop3(int year, int month, Long organizationId) {
        return computeAllParticipationRates(year, month, organizationId).stream()
                .sorted(java.util.Comparator.comparingDouble(ParticipationRateDto::getRate).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(ParticipationRateDto::getParticipatedMatches).reversed()))
                .limit(3).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ParticipationRateDto> getParticipationRateTop3(int year, int month, List<Long> organizationIds) {
        return computeAllParticipationRates(year, month, organizationIds).stream()
                .sorted(java.util.Comparator.comparingDouble(ParticipationRateDto::getRate).reversed()
                        .thenComparing(java.util.Comparator.comparingInt(ParticipationRateDto::getParticipatedMatches).reversed()))
                .limit(3).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ParticipationRateDto getPlayerParticipationRate(Long playerId, int year, int month, Long organizationId) {
        return computeAllParticipationRates(year, month, organizationId).stream()
                .filter(r -> r.getPlayerId().equals(playerId)).findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public ParticipationRateDto getPlayerParticipationRate(Long playerId, int year, int month, List<Long> organizationIds) {
        return computeAllParticipationRates(year, month, organizationIds).stream()
                .filter(r -> r.getPlayerId().equals(playerId)).findFirst().orElse(null);
    }

    private List<ParticipationRateDto> computeAllParticipationRates(int year, int month, Long organizationId) {
        LocalDate today = JstDateTimeUtil.today();
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId).stream()
                .filter(s -> !s.getSessionDate().isAfter(today)).collect(Collectors.toList());
        if (sessions.isEmpty()) return List.of();

        int totalScheduledMatches = sessions.stream()
                .mapToInt(s -> s.getTotalMatches() != null ? s.getTotalMatches() : 0).sum();
        if (totalScheduledMatches == 0) return List.of();

        Set<Long> memberPlayerIds = playerOrganizationRepository.findByOrganizationId(organizationId).stream()
                .map(PlayerOrganization::getPlayerId).collect(Collectors.toSet());

        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);
        Map<Long, String> names = playerRepository.findAllActive().stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));

        Map<Long, Integer> counts = new HashMap<>();
        allParticipants.stream()
                .filter(pp -> memberPlayerIds.contains(pp.getPlayerId()))
                .forEach(pp -> counts.merge(pp.getPlayerId(), 1, Integer::sum));

        return counts.entrySet().stream()
                .map(e -> ParticipationRateDto.builder()
                        .playerId(e.getKey()).playerName(names.getOrDefault(e.getKey(), "不明"))
                        .participatedMatches(e.getValue()).totalScheduledMatches(totalScheduledMatches)
                        .rate((double) e.getValue() / totalScheduledMatches).build())
                .collect(Collectors.toList());
    }

    private List<ParticipationRateDto> computeAllParticipationRates(int year, int month, List<Long> organizationIds) {
        LocalDate today = JstDateTimeUtil.today();
        List<PracticeSession> sessions = practiceSessionRepository.findByOrganizationIdInAndYearAndMonth(organizationIds, year, month).stream()
                .filter(s -> !s.getSessionDate().isAfter(today)).collect(Collectors.toList());
        if (sessions.isEmpty()) return List.of();

        int totalScheduledMatches = sessions.stream()
                .mapToInt(s -> s.getTotalMatches() != null ? s.getTotalMatches() : 0).sum();
        if (totalScheduledMatches == 0) return List.of();

        Set<Long> memberPlayerIds = organizationIds.stream()
                .flatMap(orgId -> playerOrganizationRepository.findByOrganizationId(orgId).stream())
                .map(PlayerOrganization::getPlayerId)
                .collect(Collectors.toSet());

        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);
        Map<Long, String> names = playerRepository.findAllActive().stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));

        Map<Long, Integer> counts = new HashMap<>();
        allParticipants.stream()
                .filter(pp -> memberPlayerIds.contains(pp.getPlayerId()))
                .forEach(pp -> counts.merge(pp.getPlayerId(), 1, Integer::sum));

        return counts.entrySet().stream()
                .map(e -> ParticipationRateDto.builder()
                        .playerId(e.getKey()).playerName(names.getOrDefault(e.getKey(), "不明"))
                        .participatedMatches(e.getValue()).totalScheduledMatches(totalScheduledMatches)
                        .rate((double) e.getValue() / totalScheduledMatches).build())
                .collect(Collectors.toList());
    }
}
