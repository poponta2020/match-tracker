package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
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

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

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

        // 会場名だけ付与（参加者のenrichmentはスキップ）
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

        // 今日以降の参加レコードを日付順で一括取得
        List<PracticeParticipant> allParticipations = practiceParticipantRepository
                .findUpcomingParticipations(playerId, today);
        if (allParticipations.isEmpty()) {
            return null;
        }

        // 最初の1件のセッションIDが最も近い練習日
        Long nextSessionId = allParticipations.get(0).getSessionId();
        PracticeSession nextSession = practiceSessionRepository.findById(nextSessionId).orElse(null);
        if (nextSession == null) {
            return null;
        }

        // そのセッションの試合番号を抽出
        List<Integer> matchNumbers = allParticipations.stream()
                .filter(p -> p.getSessionId().equals(nextSessionId))
                .map(PracticeParticipant::getMatchNumber)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();

        // 会場名を取得
        String venueName = null;
        if (nextSession.getVenueId() != null) {
            venueName = venueRepository.findById(nextSession.getVenueId())
                    .map(venue -> venue.getName())
                    .orElse(null);
        }

        // そのセッションの全参加者を取得
        List<PracticeParticipant> sessionParticipants = practiceParticipantRepository
                .findBySessionId(nextSessionId);
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

        // 日付の重複チェック
        if (practiceSessionRepository.existsBySessionDate(request.getSessionDate())) {
            throw new DuplicateResourceException("PracticeSession", "sessionDate", request.getSessionDate());
        }

        // 参加者が実在するか確認（参加者がいる場合のみ）
        if (!participantIds.isEmpty()) {
            List<Player> participants = playerRepository.findAllById(participantIds);
            if (participants.size() != participantIds.size()) {
                throw new ResourceNotFoundException("Some players not found");
            }
        }

        // 練習セッションを保存
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

        // 参加者を保存（参加者がいる場合のみ）
        // 管理者が登録した参加者は全試合に参加するものとして登録
        if (!participantIds.isEmpty()) {
            int totalMatches = request.getTotalMatches() != null ? request.getTotalMatches() : 7;
            List<PracticeParticipant> practiceParticipants = new java.util.ArrayList<>();

            for (Long playerId : participantIds) {
                for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
                    practiceParticipants.add(PracticeParticipant.builder()
                            .sessionId(saved.getId())
                            .playerId(playerId)
                            .matchNumber(matchNumber)
                            .build());
                }
            }

            practiceParticipantRepository.saveAll(practiceParticipants);
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

        // 日付変更時の重複チェック
        if (!session.getSessionDate().equals(request.getSessionDate()) &&
                practiceSessionRepository.existsBySessionDate(request.getSessionDate())) {
            throw new DuplicateResourceException("PracticeSession", "sessionDate", request.getSessionDate());
        }

        List<Long> participantIds = request.getParticipantIds() != null ? request.getParticipantIds() : List.of();

        // 参加者が実在するか確認（参加者がいる場合のみ）
        if (!participantIds.isEmpty()) {
            List<Player> participants = playerRepository.findAllById(participantIds);
            if (participants.size() != participantIds.size()) {
                throw new ResourceNotFoundException("Some players not found");
            }
        }

        // セッション情報を更新
        session.setSessionDate(request.getSessionDate());
        session.setTotalMatches(request.getTotalMatches());
        session.setVenueId(request.getVenueId());
        session.setNotes(request.getNotes());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setCapacity(request.getCapacity());
        session.setUpdatedBy(currentUserId);

        PracticeSession updated = practiceSessionRepository.save(session);

        // 既存の参加者を削除して新しい参加者を登録
        practiceParticipantRepository.deleteBySessionId(id);
        practiceParticipantRepository.flush(); // 削除を即座に反映

        // 管理者が登録した参加者は全試合に参加するものとして登録
        if (!participantIds.isEmpty()) {
            int totalMatches = request.getTotalMatches() != null ? request.getTotalMatches() : 7;
            List<PracticeParticipant> newParticipants = new java.util.ArrayList<>();

            for (Long playerId : participantIds) {
                for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
                    newParticipants.add(PracticeParticipant.builder()
                            .sessionId(id)
                            .playerId(playerId)
                            .matchNumber(matchNumber)
                            .build());
                }
            }

            practiceParticipantRepository.saveAll(newParticipants);
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

        // 参加者も一緒に削除
        practiceParticipantRepository.deleteBySessionId(id);
        practiceSessionRepository.deleteById(id);

        log.info("Successfully deleted practice session with id: {}", id);
    }

    /**
     * 特定の試合の参加者を設定（管理者用）
     */
    @Transactional
    public void setMatchParticipants(Long sessionId, Integer matchNumber, List<Long> playerIds) {
        log.info("Setting match participants for session: {}, match: {}", sessionId, matchNumber);

        // 1. セッションの存在確認
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        // 2. 試合番号の妥当性チェック
        if (matchNumber < 1 || matchNumber > session.getTotalMatches()) {
            throw new IllegalArgumentException(
                "Invalid match number: " + matchNumber + ". Must be between 1 and " + session.getTotalMatches()
            );
        }

        // 3. 選手の存在確認
        if (playerIds != null && !playerIds.isEmpty()) {
            List<Player> players = playerRepository.findAllById(playerIds);
            if (players.size() != playerIds.size()) {
                throw new ResourceNotFoundException("Some players not found");
            }
        }

        // 4. その試合の既存参加者を削除
        practiceParticipantRepository.deleteBySessionIdAndMatchNumber(sessionId, matchNumber);
        practiceParticipantRepository.flush();

        // 5. 新しい参加者を登録
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
     * 練習セッションに参加者情報を付与（単一）
     */
    private PracticeSessionDto enrichSessionWithParticipants(PracticeSession session) {
        PracticeSessionDto dto = PracticeSessionDto.fromEntity(session);

        // 会場情報を取得
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

        // 参加者リストを取得
        List<Long> playerIds = practiceParticipantRepository.findPlayerIdsBySessionId(session.getId());
        List<Player> players = playerRepository.findAllById(playerIds);
        List<PlayerDto> playerDtos = players.stream()
                .map(PlayerDto::fromEntity)
                .collect(Collectors.toList());

        dto.setParticipants(playerDtos);
        dto.setParticipantCount(playerDtos.size());

        // その日の実施済み試合数を取得
        long completedMatches = matchRepository.countByMatchDate(session.getSessionDate());
        dto.setCompletedMatches((int) completedMatches);

        // 試合ごとの参加人数を集計
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionId(session.getId());
        Map<Integer, Integer> matchCounts = new java.util.HashMap<>();
        Map<Integer, List<PracticeSessionDto.MatchParticipantInfo>> matchParticipants = new java.util.HashMap<>();

        for (int i = 1; i <= (session.getTotalMatches() != null ? session.getTotalMatches() : 7); i++) {
            matchCounts.put(i, 0);
            matchParticipants.put(i, new java.util.ArrayList<>());
        }

        // プレイヤーIDとPlayerのマップを作成
        Map<Long, Player> playerByIdMap = players.stream()
                .collect(Collectors.toMap(Player::getId, p -> p, (existing, replacement) -> existing));
        Map<Integer, List<Player>> matchParticipantPlayers = new java.util.HashMap<>();

        for (PracticeParticipant participant : allParticipants) {
            if (participant.getMatchNumber() != null) {
                matchCounts.merge(participant.getMatchNumber(), 1, Integer::sum);
                Player player = playerByIdMap.get(participant.getPlayerId());
                if (player != null) {
                    matchParticipantPlayers.computeIfAbsent(participant.getMatchNumber(), k -> new java.util.ArrayList<>())
                            .add(player);
                }
            }
        }

        // 級位→段位→名前順でソートしてParticipantInfoリストに変換
        Comparator<Player> playerComp = PlayerSortHelper.playerComparator();
        for (Map.Entry<Integer, List<Player>> entry : matchParticipantPlayers.entrySet()) {
            entry.getValue().sort(playerComp);
            matchParticipants.put(entry.getKey(),
                    entry.getValue().stream()
                            .map(p -> PracticeSessionDto.MatchParticipantInfo.builder()
                                    .name(p.getName())
                                    .kyuRank(p.getKyuRank())
                                    .build())
                            .collect(Collectors.toList()));
        }

        dto.setMatchParticipantCounts(matchCounts);
        dto.setMatchParticipants(matchParticipants);

        return dto;
    }

    /**
     * 練習セッションリストに参加者情報を付与
     */
    private List<PracticeSessionDto> enrichSessionsWithParticipants(List<PracticeSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }

        // 全セッションIDを収集
        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        // 全参加者を一括取得（N+1解消: N回→1回）
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);

        // セッションIDごとの参加者IDマップを作成
        Map<Long, List<Long>> sessionParticipantsMap = allParticipants.stream()
                .collect(Collectors.groupingBy(
                        PracticeParticipant::getSessionId,
                        Collectors.mapping(PracticeParticipant::getPlayerId, Collectors.toList())
                ));

        // 全選手IDを収集して一括取得
        List<Long> allPlayerIds = allParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Player> playerMap = playerRepository.findAllById(allPlayerIds).stream()
                .collect(Collectors.toMap(Player::getId, player -> player));

        // 全会場IDを収集して一括取得
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

            // 会場スケジュールを一括取得（N+1解消: N回→1回）
            venueScheduleMap = venueMatchScheduleRepository.findByVenueIdIn(allVenueIds).stream()
                    .collect(Collectors.groupingBy(VenueMatchSchedule::getVenueId));
        }

        // 全セッション日付の実施済み試合数を一括取得（N+1解消: N回→1回）
        List<LocalDate> allSessionDates = sessions.stream()
                .map(PracticeSession::getSessionDate)
                .distinct()
                .collect(Collectors.toList());
        Map<LocalDate, Long> completedMatchesMap = matchRepository.countByMatchDateIn(allSessionDates).stream()
                .collect(Collectors.toMap(
                        row -> (LocalDate) row[0],
                        row -> (Long) row[1]
                ));

        // 各セッションに参加者情報と会場情報を付与
        final Map<Long, Venue> finalVenueMap = venueMap;
        final Map<Long, List<VenueMatchSchedule>> finalVenueScheduleMap = venueScheduleMap;

        return sessions.stream()
                .map(session -> {
                    PracticeSessionDto dto = PracticeSessionDto.fromEntity(session);

                    // 参加者リスト
                    List<Long> playerIds = sessionParticipantsMap.getOrDefault(session.getId(), List.of());
                    List<PlayerDto> playerDtos = playerIds.stream()
                            .map(playerMap::get)
                            .filter(player -> player != null)
                            .map(PlayerDto::fromEntity)
                            .collect(Collectors.toList());

                    dto.setParticipants(playerDtos);
                    dto.setParticipantCount(playerDtos.size());

                    // その日の実施済み試合数（事前一括取得済みのマップから参照）
                    long completedMatches = completedMatchesMap.getOrDefault(session.getSessionDate(), 0L);
                    dto.setCompletedMatches((int) completedMatches);

                    // 試合ごとの参加人数と参加者名を集計
                    List<PracticeParticipant> sessionParticipants = allParticipants.stream()
                            .filter(p -> p.getSessionId().equals(session.getId()))
                            .collect(Collectors.toList());
                    Map<Integer, Integer> matchCounts = new java.util.HashMap<>();
                    Map<Integer, List<PracticeSessionDto.MatchParticipantInfo>> matchParticipants = new java.util.HashMap<>();

                    for (int i = 1; i <= (session.getTotalMatches() != null ? session.getTotalMatches() : 7); i++) {
                        matchCounts.put(i, 0);
                        matchParticipants.put(i, new java.util.ArrayList<>());
                    }

                    // プレイヤーを試合番号ごとに集計（全プレイヤーマップから）
                    Map<Integer, List<Player>> matchPlayersList = new java.util.HashMap<>();
                    for (PracticeParticipant participant : sessionParticipants) {
                        if (participant.getMatchNumber() != null) {
                            matchCounts.merge(participant.getMatchNumber(), 1, Integer::sum);
                            Player player = playerMap.get(participant.getPlayerId());
                            if (player != null) {
                                matchPlayersList.computeIfAbsent(participant.getMatchNumber(), k -> new java.util.ArrayList<>())
                                        .add(player);
                            }
                        }
                    }

                    // 級位→段位→名前順でソートしてParticipantInfoリストに変換
                    Comparator<Player> comp = PlayerSortHelper.playerComparator();
                    for (Map.Entry<Integer, List<Player>> entry : matchPlayersList.entrySet()) {
                        entry.getValue().sort(comp);
                        matchParticipants.put(entry.getKey(),
                                entry.getValue().stream()
                                        .map(p -> PracticeSessionDto.MatchParticipantInfo.builder()
                                                .name(p.getName())
                                                .kyuRank(p.getKyuRank())
                                                .build())
                                        .collect(Collectors.toList()));
                    }

                    dto.setMatchParticipantCounts(matchCounts);
                    dto.setMatchParticipants(matchParticipants);

                    // 会場情報を取得
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
     * 選手の練習参加を一括登録（月単位）
     */
    @Transactional
    public void registerParticipations(PracticeParticipationRequest request) {
        log.info("Registering participations for player {} in {}-{} with {} participations",
                request.getPlayerId(), request.getYear(), request.getMonth(),
                request.getParticipations().size());

        // 選手が存在するか確認
        if (!playerRepository.existsById(request.getPlayerId())) {
            throw new ResourceNotFoundException("Player", request.getPlayerId());
        }

        // その月の全セッションを取得
        List<PracticeSessionDto> monthSessions = findSessionsByYearMonth(request.getYear(), request.getMonth());
        List<Long> allMonthSessionIds = monthSessions.stream()
                .map(PracticeSessionDto::getId)
                .collect(Collectors.toList());

        // リクエストに含まれる各セッションが存在するか確認
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

        // その月の全セッションから該当選手の既存参加記録を削除
        if (!allMonthSessionIds.isEmpty()) {
            log.debug("Deleting existing participations for player {} in sessions: {}",
                    request.getPlayerId(), allMonthSessionIds);

            // カスタム削除クエリを使用して一括削除
            practiceParticipantRepository.deleteByPlayerIdAndSessionIds(
                    request.getPlayerId(), allMonthSessionIds);

            // 削除を確実にコミットするためにflush
            entityManager.flush();

            log.debug("Successfully deleted existing participations for player {} in {}-{}",
                    request.getPlayerId(), request.getYear(), request.getMonth());
        }

        // 新しい参加記録を登録
        List<PracticeParticipant> participants = request.getParticipations().stream()
                .map(participation -> PracticeParticipant.builder()
                        .sessionId(participation.getSessionId())
                        .playerId(request.getPlayerId())
                        .matchNumber(participation.getMatchNumber())
                        .build())
                .collect(Collectors.toList());

        practiceParticipantRepository.saveAll(participants);

        log.info("Successfully registered {} participations for player {}",
                participants.size(), request.getPlayerId());
    }

    /**
     * 選手の特定月の参加状況を取得
     */
    public Map<Long, List<Integer>> getPlayerParticipationsByMonth(Long playerId, int year, int month) {
        log.debug("Getting participations for player {} in {}-{}", playerId, year, month);

        // セッションIDのみ取得（エンリッチメント不要）
        List<Long> sessionIds = practiceSessionRepository.findByYearAndMonth(year, month).stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        if (sessionIds.isEmpty()) {
            return Map.of();
        }

        // 選手の参加記録を取得
        List<PracticeParticipant> participations =
                practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds);

        // セッションIDごとに試合番号をグルーピング
        return participations.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(
                        PracticeParticipant::getSessionId,
                        Collectors.mapping(PracticeParticipant::getMatchNumber, Collectors.toList())
                ));
    }

    /**
     * 特定の試合に参加者を1名追加
     */
    @Transactional
    public void addParticipantToMatch(LocalDate sessionDate, Integer matchNumber, Long playerId) {
        log.info("Adding participant {} to match {} on {}", playerId, matchNumber, sessionDate);

        // 1. セッションの存在確認
        PracticeSession session = practiceSessionRepository.findBySessionDate(sessionDate)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", "sessionDate", sessionDate));

        // 2. 試合番号の妥当性チェック
        if (matchNumber < 1 || matchNumber > session.getTotalMatches()) {
            throw new IllegalArgumentException(
                "Invalid match number: " + matchNumber + ". Must be between 1 and " + session.getTotalMatches()
            );
        }

        // 3. 選手の存在確認
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Player", playerId);
        }

        // 4. 既に参加登録されているかチェック
        List<PracticeParticipant> existing = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndMatchNumber(session.getId(), playerId, matchNumber);

        if (!existing.isEmpty()) {
            log.info("Player {} is already registered for match {} on {}", playerId, matchNumber, sessionDate);
            return; // 既に登録済みの場合は何もしない
        }

        // 5. 参加者を登録
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
}
