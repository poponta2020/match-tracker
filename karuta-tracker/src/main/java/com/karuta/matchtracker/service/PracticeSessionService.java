package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.VenueMatchScheduleRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 練習日管理サービス
 *
 * セッション（練習日）のCRUDおよびセッション情報のエンリッチメントを担当。
 * 参加者管理は {@link PracticeParticipantService} に委譲。
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PracticeSessionService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PlayerRepository playerRepository;
    private final MatchRepository matchRepository;
    private final VenueRepository venueRepository;
    private final VenueMatchScheduleRepository venueMatchScheduleRepository;

    /**
     * 全ての練習日を取得（降順）
     */
    public List<PracticeSessionDto> findAllSessions() {
        log.debug("Finding all practice sessions");
        List<PracticeSession> sessions = practiceSessionRepository.findAllOrderBySessionDateDesc();
        return enrichSessionsWithParticipants(sessions);
    }

    /**
     * IDで練習日を取得
     */
    public PracticeSessionDto findById(Long id) {
        log.debug("Finding practice session by id: {}", id);
        PracticeSession session = practiceSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", id));
        return enrichSessionWithParticipants(session);
    }

    /**
     * 日付で練習日を取得
     */
    public PracticeSessionDto findByDate(LocalDate date) {
        log.debug("Finding practice session by date: {}", date);
        PracticeSession session = practiceSessionRepository.findBySessionDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", "sessionDate", date));
        return PracticeSessionDto.fromEntity(session);
    }

    /**
     * 日付で練習日を取得（参加者情報付き）
     */
    public PracticeSessionDto findByDateWithParticipants(LocalDate date) {
        log.debug("Finding practice session with participants by date: {}", date);
        PracticeSession session = practiceSessionRepository.findBySessionDate(date)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", "sessionDate", date));
        return enrichSessionWithParticipants(session);
    }

    /**
     * 期間内の練習日を取得
     */
    public List<PracticeSessionDto> findSessionsInRange(LocalDate startDate, LocalDate endDate) {
        log.debug("Finding practice sessions between {} and {}", startDate, endDate);
        return practiceSessionRepository.findByDateRange(startDate, endDate)
                .stream()
                .map(PracticeSessionDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 特定の年月の練習日を取得
     */
    public List<PracticeSessionDto> findSessionsByYearMonth(int year, int month) {
        log.debug("Finding practice sessions for {}-{}", year, month);
        YearMonth yearMonth = YearMonth.of(year, month);
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(yearMonth.getYear(), yearMonth.getMonthValue());
        return enrichSessionsWithParticipants(sessions);
    }

    /**
     * 特定の年月の練習日サマリーを取得（カレンダー表示用・軽量）
     * 参加者詳細情報なし、会場名のみ付与
     */
    @Transactional(readOnly = true)
    public List<PracticeSessionDto> findSessionSummariesByYearMonth(int year, int month) {
        log.debug("Finding practice session summaries for {}-{}", year, month);
        YearMonth yearMonth = YearMonth.of(year, month);
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(yearMonth.getYear(), yearMonth.getMonthValue());

        List<Long> venueIds = sessions.stream()
                .map(PracticeSession::getVenueId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> venueNameMap = venueIds.isEmpty()
                ? Map.of()
                : venueRepository.findAllById(venueIds).stream()
                    .collect(Collectors.toMap(v -> v.getId(), v -> v.getName()));

        return sessions.stream().map(session -> {
            PracticeSessionDto dto = PracticeSessionDto.fromEntity(session);
            if (session.getVenueId() != null) {
                dto.setVenueName(venueNameMap.get(session.getVenueId()));
            }
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 指定日以降の練習日を取得
     */
    public List<PracticeSessionDto> findUpcomingSessions(LocalDate fromDate) {
        log.debug("Finding upcoming practice sessions from {}", fromDate);
        List<PracticeSession> sessions = practiceSessionRepository.findUpcomingSessions(fromDate);
        return enrichSessionsWithParticipants(sessions);
    }

    /**
     * 指定日以降の練習日の日付リストのみ取得（軽量）
     */
    @Transactional(readOnly = true)
    public List<LocalDate> findSessionDates(LocalDate fromDate) {
        log.debug("Finding session dates from {}", fromDate);
        return practiceSessionRepository.findSessionDates(fromDate);
    }

    /**
     * 次の参加予定練習を取得（ホーム画面用・軽量）
     */
    @Transactional(readOnly = true)
    public NextParticipationDto findNextParticipation(Long playerId) {
        LocalDate today = LocalDate.now();
        log.debug("Finding next participation for player {} from {}", playerId, today);

        List<PracticeSession> upcomingSessions = practiceSessionRepository.findUpcomingSessions(today);
        if (upcomingSessions.isEmpty()) {
            return null;
        }
        PracticeSession nextSession = upcomingSessions.get(0);

        List<PracticeParticipant> myParticipations = practiceParticipantRepository
                .findUpcomingParticipations(playerId, today).stream()
                .filter(p -> p.getSessionId().equals(nextSession.getId()))
                .toList();
        boolean registered = !myParticipations.isEmpty();
        List<Integer> matchNumbers = myParticipations.stream()
                .map(PracticeParticipant::getMatchNumber)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();

        String venueName = null;
        if (nextSession.getVenueId() != null) {
            venueName = venueRepository.findById(nextSession.getVenueId())
                    .map(Venue::getName)
                    .orElse(null);
        }

        List<PracticeParticipant> sessionParticipants = practiceParticipantRepository
                .findBySessionId(nextSession.getId());
        List<Long> participantPlayerIds = sessionParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .toList();
        List<NextParticipationDto.ParticipantInfo> participantInfos = new java.util.ArrayList<>();
        if (!participantPlayerIds.isEmpty()) {
            playerRepository.findAllById(participantPlayerIds).forEach(p ->
                participantInfos.add(NextParticipationDto.ParticipantInfo.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .kyuRank(p.getKyuRank())
                        .danRank(p.getDanRank())
                        .build())
            );
        }
        participantInfos.sort(PlayerSortHelper.participantInfoComparator());

        return NextParticipationDto.builder()
                .sessionDate(nextSession.getSessionDate())
                .startTime(nextSession.getStartTime())
                .endTime(nextSession.getEndTime())
                .venueName(venueName)
                .matchNumbers(matchNumbers)
                .isToday(nextSession.getSessionDate().equals(today))
                .registered(registered)
                .participants(participantInfos)
                .build();
    }

    /**
     * 日付が練習日として登録されているか確認
     */
    public boolean existsSessionOnDate(LocalDate date) {
        log.debug("Checking if practice session exists on {}", date);
        return practiceSessionRepository.existsBySessionDate(date);
    }

    /**
     * 練習日を新規登録
     */
    @Transactional
    public PracticeSessionDto createSession(PracticeSessionCreateRequest request, Long currentUserId) {
        List<Long> participantIds = request.getParticipantIds() != null ? request.getParticipantIds() : List.of();
        log.info("Creating new practice session on {} with {} participants",
                request.getSessionDate(), participantIds.size());

        if (practiceSessionRepository.existsBySessionDate(request.getSessionDate())) {
            throw new DuplicateResourceException("PracticeSession", "sessionDate", request.getSessionDate());
        }

        if (!participantIds.isEmpty()) {
            List<Player> participants = playerRepository.findAllById(participantIds);
            if (participants.size() != participantIds.size()) {
                throw new ResourceNotFoundException("Some players not found");
            }
        }

        PracticeSession session = PracticeSession.builder()
                .sessionDate(request.getSessionDate())
                .totalMatches(request.getTotalMatches())
                .venueId(request.getVenueId())
                .notes(request.getNotes())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .capacity(request.getCapacity())
                .createdBy(currentUserId)
                .updatedBy(currentUserId)
                .build();

        PracticeSession saved = practiceSessionRepository.save(session);

        if (!participantIds.isEmpty()) {
            saveParticipantsForAllMatches(saved.getId(), participantIds,
                    request.getTotalMatches() != null ? request.getTotalMatches() : 7);
        }

        log.info("Successfully created practice session with id: {} and {} participants",
                saved.getId(), participantIds.size());

        return enrichSessionWithParticipants(saved);
    }

    /**
     * 総試合数を更新
     */
    @Transactional
    public PracticeSessionDto updateTotalMatches(Long id, Integer totalMatches) {
        log.info("Updating total matches for practice session id: {}", id);

        PracticeSession session = practiceSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", id));

        if (totalMatches < 0) {
            throw new IllegalArgumentException("Total matches cannot be negative");
        }

        session.setTotalMatches(totalMatches);
        PracticeSession updated = practiceSessionRepository.save(session);

        log.info("Successfully updated total matches for practice session id: {}", id);
        return PracticeSessionDto.fromEntity(updated);
    }

    /**
     * 練習セッションを更新
     */
    @Transactional
    public PracticeSessionDto updateSession(Long id, PracticeSessionUpdateRequest request, Long currentUserId) {
        log.info("Updating practice session id: {}", id);

        PracticeSession session = practiceSessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", id));

        if (!session.getSessionDate().equals(request.getSessionDate()) &&
                practiceSessionRepository.existsBySessionDate(request.getSessionDate())) {
            throw new DuplicateResourceException("PracticeSession", "sessionDate", request.getSessionDate());
        }

        List<Long> participantIds = request.getParticipantIds() != null ? request.getParticipantIds() : List.of();

        if (!participantIds.isEmpty()) {
            List<Player> participants = playerRepository.findAllById(participantIds);
            if (participants.size() != participantIds.size()) {
                throw new ResourceNotFoundException("Some players not found");
            }
        }

        session.setSessionDate(request.getSessionDate());
        session.setTotalMatches(request.getTotalMatches());
        session.setVenueId(request.getVenueId());
        session.setNotes(request.getNotes());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setCapacity(request.getCapacity());
        session.setUpdatedBy(currentUserId);

        PracticeSession updated = practiceSessionRepository.save(session);

        practiceParticipantRepository.deleteBySessionId(id);
        practiceParticipantRepository.flush();

        if (!participantIds.isEmpty()) {
            saveParticipantsForAllMatches(id, participantIds,
                    request.getTotalMatches() != null ? request.getTotalMatches() : 7);
        }

        log.info("Successfully updated practice session id: {}", id);
        return enrichSessionWithParticipants(updated);
    }

    /**
     * 練習日を削除
     */
    @Transactional
    public void deleteSession(Long id) {
        log.info("Deleting practice session with id: {}", id);

        if (!practiceSessionRepository.existsById(id)) {
            throw new ResourceNotFoundException("PracticeSession", id);
        }

        practiceParticipantRepository.deleteBySessionId(id);
        practiceSessionRepository.deleteById(id);

        log.info("Successfully deleted practice session with id: {}", id);
    }

    /**
     * 全試合に参加者を登録する共通処理
     */
    private void saveParticipantsForAllMatches(Long sessionId, List<Long> participantIds, int totalMatches) {
        List<PracticeParticipant> practiceParticipants = new java.util.ArrayList<>();
        for (Long playerId : participantIds) {
            for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
                practiceParticipants.add(PracticeParticipant.builder()
                        .sessionId(sessionId)
                        .playerId(playerId)
                        .matchNumber(matchNumber)
                        .build());
            }
        }
        practiceParticipantRepository.saveAll(practiceParticipants);
    }

    /**
     * 練習セッションに参加者情報を付与（単一）
     */
    private PracticeSessionDto enrichSessionWithParticipants(PracticeSession session) {
        PracticeSessionDto dto = PracticeSessionDto.fromEntity(session);

        if (session.getVenueId() != null) {
            venueRepository.findById(session.getVenueId()).ifPresent(venue -> {
                dto.setVenueName(venue.getName());
                List<VenueMatchSchedule> schedules = venueMatchScheduleRepository
                        .findByVenueIdOrderByMatchNumberAsc(venue.getId());
                List<VenueMatchScheduleDto> scheduleDtos = schedules.stream()
                        .map(VenueMatchScheduleDto::fromEntity)
                        .collect(Collectors.toList());
                dto.setVenueSchedules(scheduleDtos);
            });
        }

        List<Long> playerIds = practiceParticipantRepository.findPlayerIdsBySessionId(session.getId());
        List<Player> players = playerRepository.findAllById(playerIds);
        List<PlayerDto> playerDtos = players.stream()
                .map(PlayerDto::fromEntity)
                .collect(Collectors.toList());

        dto.setParticipants(playerDtos);

        long completedMatches = matchRepository.countByMatchDate(session.getSessionDate());
        dto.setCompletedMatches((int) completedMatches);

        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionId(session.getId());
        enrichDtoWithMatchDetails(dto, session, allParticipants, players);

        return dto;
    }

    /**
     * 練習セッションリストに参加者情報を付与
     */
    private List<PracticeSessionDto> enrichSessionsWithParticipants(List<PracticeSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);

        Map<Long, List<Long>> sessionParticipantsMap = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        PracticeParticipant::getSessionId,
                        Collectors.mapping(PracticeParticipant::getPlayerId, Collectors.toList())
                ));

        List<Long> allPlayerIds = allParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Player> playerMap = playerRepository.findAllById(allPlayerIds).stream()
                .collect(Collectors.toMap(Player::getId, player -> player));

        List<Long> allVenueIds = sessions.stream()
                .map(PracticeSession::getVenueId)
                .filter(venueId -> venueId != null)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Venue> venueMap = new java.util.HashMap<>();
        Map<Long, List<VenueMatchSchedule>> venueScheduleMap = new java.util.HashMap<>();

        if (!allVenueIds.isEmpty()) {
            venueMap = venueRepository.findAllById(allVenueIds).stream()
                    .collect(Collectors.toMap(Venue::getId, venue -> venue));
            venueScheduleMap = venueMatchScheduleRepository.findByVenueIdIn(allVenueIds).stream()
                    .collect(Collectors.groupingBy(VenueMatchSchedule::getVenueId));
        }

        List<LocalDate> allSessionDates = sessions.stream()
                .map(PracticeSession::getSessionDate)
                .distinct()
                .collect(Collectors.toList());
        Map<LocalDate, Long> completedMatchesMap = matchRepository.countByMatchDateIn(allSessionDates).stream()
                .collect(Collectors.toMap(
                        row -> (LocalDate) row[0],
                        row -> (Long) row[1]
                ));

        final Map<Long, Venue> finalVenueMap = venueMap;
        final Map<Long, List<VenueMatchSchedule>> finalVenueScheduleMap = venueScheduleMap;

        return sessions.stream()
                .map(session -> {
                    PracticeSessionDto dto = PracticeSessionDto.fromEntity(session);

                    List<Long> playerIds = sessionParticipantsMap.getOrDefault(session.getId(), List.of());
                    List<PlayerDto> playerDtos = playerIds.stream()
                            .map(playerMap::get)
                            .filter(player -> player != null)
                            .map(PlayerDto::fromEntity)
                            .collect(Collectors.toList());

                    dto.setParticipants(playerDtos);

                    List<PracticeParticipant> sessionParts = allParticipants.stream()
                            .filter(p -> p.getSessionId().equals(session.getId()))
                            .collect(Collectors.toList());

                    List<Player> sessionPlayers = playerIds.stream()
                            .map(playerMap::get)
                            .filter(p -> p != null)
                            .collect(Collectors.toList());

                    enrichDtoWithMatchDetails(dto, session, sessionParts, sessionPlayers);

                    long completedMatches = completedMatchesMap.getOrDefault(session.getSessionDate(), 0L);
                    dto.setCompletedMatches((int) completedMatches);

                    if (session.getVenueId() != null) {
                        Venue venue = finalVenueMap.get(session.getVenueId());
                        if (venue != null) {
                            dto.setVenueName(venue.getName());
                            List<VenueMatchSchedule> schedules = finalVenueScheduleMap.get(venue.getId());
                            if (schedules != null) {
                                List<VenueMatchScheduleDto> scheduleDtos = schedules.stream()
                                        .map(VenueMatchScheduleDto::fromEntity)
                                        .collect(Collectors.toList());
                                dto.setVenueSchedules(scheduleDtos);
                            }
                        }
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * DTOに試合ごとの参加者詳細を付与する共通処理
     * enrichSessionWithParticipantsとenrichSessionsWithParticipantsの重複ロジックを統合
     */
    private void enrichDtoWithMatchDetails(PracticeSessionDto dto, PracticeSession session,
                                           List<PracticeParticipant> participants, List<Player> players) {
        dto.setParticipantCount((int) participants.stream()
                .filter(p -> p.getStatus() != ParticipantStatus.WAITLISTED
                        && p.getStatus() != ParticipantStatus.DECLINED
                        && p.getStatus() != ParticipantStatus.CANCELLED)
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .count());

        Map<Integer, Integer> matchCounts = new java.util.HashMap<>();
        Map<Integer, List<PracticeSessionDto.MatchParticipantInfo>> matchParticipants = new java.util.HashMap<>();

        for (int i = 1; i <= (session.getTotalMatches() != null ? session.getTotalMatches() : 7); i++) {
            matchCounts.put(i, 0);
            matchParticipants.put(i, new java.util.ArrayList<>());
        }

        Map<Long, Player> playerByIdMap = players.stream()
                .collect(Collectors.toMap(Player::getId, p -> p, (existing, replacement) -> existing));

        record ParticipantWithPlayer(PracticeParticipant participant, Player player) {}
        Map<Integer, List<ParticipantWithPlayer>> matchPlayersList = new java.util.HashMap<>();

        for (PracticeParticipant participant : participants) {
            if (participant.getMatchNumber() != null) {
                ParticipantStatus status = participant.getStatus();
                if (status != ParticipantStatus.WAITLISTED
                        && status != ParticipantStatus.DECLINED
                        && status != ParticipantStatus.CANCELLED) {
                    matchCounts.merge(participant.getMatchNumber(), 1, Integer::sum);
                }
                Player player = playerByIdMap.get(participant.getPlayerId());
                if (player != null) {
                    matchPlayersList.computeIfAbsent(participant.getMatchNumber(), k -> new java.util.ArrayList<>())
                            .add(new ParticipantWithPlayer(participant, player));
                }
            }
        }

        Comparator<Player> comp = PlayerSortHelper.playerComparator();
        for (Map.Entry<Integer, List<ParticipantWithPlayer>> entry : matchPlayersList.entrySet()) {
            entry.getValue().sort((a, b) -> comp.compare(a.player(), b.player()));
            matchParticipants.put(entry.getKey(),
                    entry.getValue().stream()
                            .map(pp -> PracticeSessionDto.MatchParticipantInfo.builder()
                                    .name(pp.player().getName())
                                    .kyuRank(pp.player().getKyuRank())
                                    .role(pp.player().getRole())
                                    .status(pp.participant().getStatus())
                                    .waitlistNumber(pp.participant().getWaitlistNumber())
                                    .build())
                            .collect(Collectors.toList()));
        }

        dto.setMatchParticipantCounts(matchCounts);
        dto.setMatchParticipants(matchParticipants);
    }
}
