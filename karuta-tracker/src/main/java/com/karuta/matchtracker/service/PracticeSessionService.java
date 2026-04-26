package com.karuta.matchtracker.service;

import com.karuta.matchtracker.config.AdjacentRoomConfig;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.util.AdminScopeValidator;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.entity.VenueMatchSchedule;
import com.karuta.matchtracker.entity.DensukeUrl;
import com.karuta.matchtracker.repository.DensukeMemberMappingRepository;
import com.karuta.matchtracker.repository.DensukeRowIdRepository;
import com.karuta.matchtracker.repository.DensukeUrlRepository;
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

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final OrganizationService organizationService;
    private final DensukeUrlRepository densukeUrlRepository;
    private final DensukeRowIdRepository densukeRowIdRepository;
    private final DensukeMemberMappingRepository densukeMemberMappingRepository;
    private final DensukeSyncService densukeSyncService;
    private final AdjacentRoomService adjacentRoomService;
    private final WaitlistPromotionService waitlistPromotionService;

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
     * 特定の年月の練習日を取得
     */
    public List<PracticeSessionDto> findSessionsByYearMonth(int year, int month) {
        log.debug("Finding practice sessions for {}-{}", year, month);
        YearMonth yearMonth = YearMonth.of(year, month);
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(yearMonth.getYear(), yearMonth.getMonthValue());
        return enrichSessionsWithParticipants(sessions);
    }

    /**
     * ユーザーの参加団体に基づいて特定の年月の練習日を取得
     */
    public List<PracticeSessionDto> findSessionsByYearMonthAndPlayer(int year, int month, Long playerId) {
        List<Long> orgIds = organizationService.getPlayerOrganizationIds(playerId);
        if (orgIds.isEmpty()) return List.of();
        YearMonth yearMonth = YearMonth.of(year, month);
        List<PracticeSession> sessions = practiceSessionRepository.findByOrganizationIdInAndYearAndMonth(orgIds, yearMonth.getYear(), yearMonth.getMonthValue());
        return enrichSessionsWithParticipants(sessions);
    }

    /**
     * 特定の年月の練習日サマリーを取得（カレンダー表示用・軽量）
     * 参加者詳細情報なし、会場名のみ付与
     */
    @Transactional(readOnly = true)
    public List<PracticeSessionDto> findSessionSummariesByYearMonth(int year, int month, Long playerId) {
        log.debug("Finding practice session summaries for {}-{}", year, month);
        YearMonth yearMonth = YearMonth.of(year, month);
        List<PracticeSession> sessions;
        if (playerId != null) {
            List<Long> orgIds = organizationService.getPlayerOrganizationIds(playerId);
            if (orgIds.isEmpty()) return List.of();
            sessions = practiceSessionRepository.findByOrganizationIdInAndYearAndMonth(orgIds, yearMonth.getYear(), yearMonth.getMonthValue());
        } else {
            sessions = practiceSessionRepository.findByYearAndMonth(yearMonth.getYear(), yearMonth.getMonthValue());
        }

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
     * 指定日以降の練習日の日付リストのみ取得（軽量）
     * playerId が指定された場合はユーザーの参加団体でフィルタする
     */
    @Transactional(readOnly = true)
    public List<LocalDate> findSessionDates(LocalDate fromDate, Long playerId) {
        log.debug("Finding session dates from {} for player {}", fromDate, playerId);
        if (playerId != null) {
            List<Long> orgIds = organizationService.getPlayerOrganizationIds(playerId);
            if (orgIds.isEmpty()) return List.of();
            return practiceSessionRepository.findSessionDatesByOrganizationIdIn(orgIds, fromDate);
        }
        return practiceSessionRepository.findSessionDates(fromDate);
    }

    /**
     * プレイヤーの所属団体に基づいて、次の練習セッションを取得する。
     * 今日の練習が開始時間前ならその日、開始時間を過ぎていたら翌日以降の直近の練習。
     */
    public PracticeSession findNextSessionForPlayer(Long playerId) {
        List<Long> orgIds = organizationService.getPlayerOrganizationIds(playerId);
        if (orgIds.isEmpty()) {
            return null;
        }

        LocalDate today = JstDateTimeUtil.today();
        List<PracticeSession> upcomingSessions = practiceSessionRepository
                .findUpcomingSessionsByOrganizationIdIn(orgIds, today);

        for (PracticeSession session : upcomingSessions) {
            if (session.getSessionDate().isEqual(today)) {
                if (session.getStartTime() == null
                        || JstDateTimeUtil.now().isBefore(today.atTime(session.getStartTime()))) {
                    return session;
                }
            } else {
                return session;
            }
        }

        return null;
    }

    /**
     * 次の参加予定練習を取得（ホーム画面用・軽量）
     */
    @Transactional(readOnly = true)
    public NextParticipationDto findNextParticipation(Long playerId) {
        LocalDate today = JstDateTimeUtil.today();
        log.debug("Finding next participation for player {} from {}", playerId, today);

        // ユーザーの参加団体に基づいて直近の未来の練習日を取得
        List<Long> orgIds = organizationService.getPlayerOrganizationIds(playerId);
        if (orgIds.isEmpty()) {
            return null;
        }
        List<PracticeSession> upcomingSessions = practiceSessionRepository.findUpcomingSessionsByOrganizationIdIn(orgIds, today);
        if (upcomingSessions.isEmpty()) {
            return null;
        }
        PracticeSession nextSession = upcomingSessions.get(0);

        // そのセッションに自分が参加登録しているか確認
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

        // 会場名を取得
        String venueName = null;
        if (nextSession.getVenueId() != null) {
            venueName = venueRepository.findById(nextSession.getVenueId())
                    .map(venue -> venue.getName())
                    .orElse(null);
        }

        // そのセッションの全参加者を取得
        List<PracticeParticipant> sessionParticipants = practiceParticipantRepository
                .findBySessionId(nextSession.getId());
        List<Long> participantPlayerIds = sessionParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .toList();
        // playerId -> ステータスのマップを作成（WON/PENDINGがあれば優先）
        java.util.Map<Long, ParticipantStatus> statusByPlayerId = sessionParticipants.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PracticeParticipant::getPlayerId,
                        PracticeParticipant::getStatus,
                        (s1, s2) -> (s1 == ParticipantStatus.WON || s1 == ParticipantStatus.PENDING) ? s1
                                  : (s2 == ParticipantStatus.WON || s2 == ParticipantStatus.PENDING) ? s2
                                  : s1));
        List<NextParticipationDto.ParticipantInfo> participantInfos = new java.util.ArrayList<>();
        if (!participantPlayerIds.isEmpty()) {
            playerRepository.findAllById(participantPlayerIds).forEach(p -> {
                ParticipantStatus status = statusByPlayerId.get(p.getId());
                // DECLINED/WAITLIST_DECLINED は表示対象外
                if (status != null && status != ParticipantStatus.DECLINED && status != ParticipantStatus.WAITLIST_DECLINED) {
                    participantInfos.add(NextParticipationDto.ParticipantInfo.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .kyuRank(p.getKyuRank())
                            .danRank(p.getDanRank())
                            .status(status)
                            .build());
                }
            });
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

        // organizationId の決定
        Long organizationId = request.getOrganizationId();
        if (organizationId == null) {
            throw new IllegalArgumentException("団体IDは必須です");
        }

        // 同一団体・同一日付の重複チェック
        if (practiceSessionRepository.existsBySessionDateAndOrganizationId(request.getSessionDate(), organizationId)) {
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
                .organizationId(organizationId)
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

        // ADMIN スコープチェックは呼び出し元で実施（Controller → checkAdminScope）

        // 日付変更時の同一団体・同一日付の重複チェック
        if (!session.getSessionDate().equals(request.getSessionDate()) &&
                practiceSessionRepository.existsBySessionDateAndOrganizationId(request.getSessionDate(), session.getOrganizationId())) {
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

        // 容量変更検知のため変更前 capacity を保持
        Integer oldCapacity = session.getCapacity();
        Integer newCapacity = request.getCapacity();

        // セッション情報を更新
        session.setSessionDate(request.getSessionDate());
        session.setTotalMatches(request.getTotalMatches());
        session.setVenueId(request.getVenueId());
        session.setNotes(request.getNotes());
        session.setStartTime(request.getStartTime());
        session.setEndTime(request.getEndTime());
        session.setCapacity(newCapacity);
        session.setUpdatedBy(currentUserId);

        PracticeSession updated = practiceSessionRepository.save(session);

        // 差分更新: 既存参加者のdirty値を保持しつつ、参加者の追加・削除を行う
        int totalMatches = request.getTotalMatches() != null ? request.getTotalMatches() : 7;
        List<PracticeParticipant> existingParticipants = practiceParticipantRepository.findBySessionId(id);
        Set<Long> requestedPlayerIds = new java.util.HashSet<>(participantIds);
        Set<Long> existingPlayerIds = existingParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .collect(java.util.stream.Collectors.toSet());

        // 削除分: リクエストに含まれないプレイヤー → CANCELLED + dirty=true
        // BYE（matchNumber=null）は対戦組み合わせ（createBatch）で管理されるため除外
        for (PracticeParticipant p : existingParticipants) {
            if (p.getMatchNumber() == null) continue;
            if (!requestedPlayerIds.contains(p.getPlayerId())) {
                p.setStatus(ParticipantStatus.CANCELLED);
                p.setDirty(true);
                p.setCancelledAt(JstDateTimeUtil.now());
            }
        }

        // 継続分: totalMatches変更への対応
        for (PracticeParticipant p : existingParticipants) {
            if (requestedPlayerIds.contains(p.getPlayerId()) && p.getStatus() != ParticipantStatus.CANCELLED) {
                if (p.getMatchNumber() != null && p.getMatchNumber() > totalMatches) {
                    // 試合数が減った場合: 超過分を削除
                    practiceParticipantRepository.delete(p);
                }
            }
        }

        // 新規追加分 + 継続分の試合数増加分
        List<PracticeParticipant> newParticipants = new java.util.ArrayList<>();
        for (Long playerId : participantIds) {
            if (!existingPlayerIds.contains(playerId)) {
                // 新規追加: 全試合分を dirty=true で作成
                for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
                    newParticipants.add(PracticeParticipant.builder()
                            .sessionId(id).playerId(playerId).matchNumber(matchNumber).dirty(true).build());
                }
            } else {
                // 継続: 試合数が増えた場合、新しい試合番号分を追加
                Set<Integer> existingMatchNumbers = existingParticipants.stream()
                        .filter(p -> p.getPlayerId().equals(playerId) && p.getStatus() != ParticipantStatus.CANCELLED)
                        .map(PracticeParticipant::getMatchNumber)
                        .collect(java.util.stream.Collectors.toSet());
                for (int matchNumber = 1; matchNumber <= totalMatches; matchNumber++) {
                    if (!existingMatchNumbers.contains(matchNumber)) {
                        newParticipants.add(PracticeParticipant.builder()
                                .sessionId(id).playerId(playerId).matchNumber(matchNumber).dirty(true).build());
                    }
                }
            }
        }

        if (!newParticipants.isEmpty()) {
            practiceParticipantRepository.saveAll(newParticipants);
        }

        // 容量が拡張された場合は WAITLISTED を OFFERED に昇格（応答期限なし、定員までに制限）
        // 参加者の差分更新（キャンセル・削除・追加）の後に実行することで、
        // 最終状態の WON / OFFERED 数を基準に昇格数が決まる。
        if (isCapacityExpanded(oldCapacity, newCapacity)) {
            waitlistPromotionService.promoteWaitlistedAfterCapacityIncrease(id);
        }

        log.info("Successfully updated practice session id: {}", id);
        densukeSyncService.triggerWriteAsync();
        return enrichSessionWithParticipants(updated);
    }

    /**
     * capacity が拡張されたか（拡張時にキャンセル待ち昇格処理を呼ぶ判定）。
     * 「明示的な定員増加」が確認できる場合のみ true を返す（= 旧・新ともに非nullで増加した場合）。
     *
     * リクエストの capacity が null の場合は「未指定」とみなし拡張扱いしない。
     * 編集フォームから capacity を送らない既存ケース（PracticeForm の通常編集）でも
     * 意図せず昇格処理が走らないようにするため。
     *
     * 「制限解除（明示的に capacity を null にする）」を拡張扱いしたい場合は、
     * 未指定と明示 null を区別できる DTO（PATCH 用 DTO、JsonNullable、capacityUnlimited フラグ等）を
     * 導入してから判定する必要がある。
     */
    private boolean isCapacityExpanded(Integer oldCapacity, Integer newCapacity) {
        if (oldCapacity == null || newCapacity == null) return false;
        return newCapacity > oldCapacity;
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
     * ADMINが自団体の練習日のみ操作可能かチェック
     * SUPER_ADMINの場合はスキップ
     */
    public void checkAdminScope(Long sessionId, String role, Long adminOrganizationId) {
        if (!"ADMIN".equals(role)) return;

        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        AdminScopeValidator.validateScope(role, adminOrganizationId, session.getOrganizationId(),
                "他団体の練習日は編集できません");
    }

    /**
     * ADMINが自団体の練習日のみ操作可能かチェック（日付ベース）
     * SUPER_ADMINの場合はスキップ
     */
    public void checkAdminScopeByDate(LocalDate date, String role, Long adminOrganizationId) {
        if (!"ADMIN".equals(role)) return;

        practiceSessionRepository.findBySessionDateAndOrganizationId(date, adminOrganizationId)
                .orElseThrow(() -> new ForbiddenException("他団体の練習日は編集できません"));
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

        // 隣室空き状況を付与
        if (session.getVenueId() != null) {
            dto.setAdjacentRoomStatus(
                    adjacentRoomService.getAdjacentRoomAvailability(session.getVenueId(), session.getSessionDate()));
        }

        // 参加者リストを取得
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionId(session.getId());
        List<Long> playerIds = allParticipants.stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Player> playerMap = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, p -> p, (existing, replacement) -> existing));

        dto.setParticipants(playerMap.values().stream()
                .map(PlayerDto::fromEntity)
                .collect(Collectors.toList()));

        // その日の実施済み試合数を取得
        long completedMatches = matchRepository.countByMatchDate(session.getSessionDate());
        dto.setCompletedMatches((int) completedMatches);

        // 試合ごとの参加人数・参加者情報を集計
        enrichDtoWithMatchDetails(dto, session, allParticipants, playerMap);

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
                    List<PracticeParticipant> sessionParticipants = allParticipants.stream()
                            .filter(p -> p.getSessionId().equals(session.getId()))
                            .collect(Collectors.toList());
                    List<Long> playerIds = sessionParticipants.stream()
                            .map(PracticeParticipant::getPlayerId)
                            .distinct()
                            .collect(Collectors.toList());
                    List<PlayerDto> playerDtos = playerIds.stream()
                            .map(playerMap::get)
                            .filter(player -> player != null)
                            .map(PlayerDto::fromEntity)
                            .collect(Collectors.toList());

                    dto.setParticipants(playerDtos);

                    // その日の実施済み試合数（事前一括取得済みのマップから参照）
                    long completedMatches = completedMatchesMap.getOrDefault(session.getSessionDate(), 0L);
                    dto.setCompletedMatches((int) completedMatches);

                    // 試合ごとの参加人数・参加者情報を集計
                    enrichDtoWithMatchDetails(dto, session, sessionParticipants, playerMap);

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
                        // 隣室空き状況を付与
                        dto.setAdjacentRoomStatus(
                                adjacentRoomService.getAdjacentRoomAvailability(session.getVenueId(), session.getSessionDate()));
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * DTOに試合ごとの参加人数・参加者情報を付与する共通メソッド
     */
    private void enrichDtoWithMatchDetails(PracticeSessionDto dto, PracticeSession session,
            List<PracticeParticipant> sessionParticipants, Map<Long, Player> playerMap) {
        // 参加者数はキャンセル待ち・辞退・キャンセル済みを除外してカウント
        dto.setParticipantCount((int) sessionParticipants.stream()
                .filter(p -> p.getStatus().isActive())
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .count());

        Map<Integer, Integer> matchCounts = new java.util.HashMap<>();
        Map<Integer, List<PracticeSessionDto.MatchParticipantInfo>> matchParticipants = new java.util.HashMap<>();

        for (int i = 1; i <= (session.getTotalMatches() != null ? session.getTotalMatches() : 7); i++) {
            matchCounts.put(i, 0);
            matchParticipants.put(i, new java.util.ArrayList<>());
        }

        record ParticipantWithPlayer(PracticeParticipant participant, Player player) {}
        Map<Integer, List<ParticipantWithPlayer>> matchPlayersList = new java.util.HashMap<>();

        for (PracticeParticipant participant : sessionParticipants) {
            if (participant.getMatchNumber() != null) {
                // 人数カウントはアクティブ（WON/PENDING）のみ
                if (participant.getStatus().isActive()) {
                    matchCounts.merge(participant.getMatchNumber(), 1, Integer::sum);
                }
                // 表示対象: DECLINED/WAITLIST_DECLINED以外
                ParticipantStatus status = participant.getStatus();
                if (status != ParticipantStatus.DECLINED && status != ParticipantStatus.WAITLIST_DECLINED) {
                    Player player = playerMap.get(participant.getPlayerId());
                    if (player != null) {
                        matchPlayersList.computeIfAbsent(participant.getMatchNumber(), k -> new java.util.ArrayList<>())
                                .add(new ParticipantWithPlayer(participant, player));
                    }
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

    // ========== 伝助URL管理 ==========

    public java.util.Optional<DensukeUrl> getDensukeUrl(int year, int month, Long organizationId) {
        return densukeUrlRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId);
    }

    @Transactional
    public DensukeUrl saveDensukeUrl(int year, int month, String url, Long organizationId) {
        if (!url.startsWith("https://densuke.biz/")) {
            throw new IllegalArgumentException("伝助のURL（https://densuke.biz/）のみ登録できます");
        }
        DensukeUrl entity = densukeUrlRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                .orElse(DensukeUrl.builder().year(year).month(month).organizationId(organizationId).build());
        entity.setUrl(url);
        // 手動保存経路では sd（編集用シークレット）を持たない。自動作成済みレコードを手動 URL で
        // 上書きしたケースで旧 sd が残留して整合性が崩れないよう、明示的にクリアする。
        entity.setDensukeSd(null);
        return densukeUrlRepository.save(entity);
    }

    /**
     * 指定年月・団体の伝助URLレコードを削除する（作り直し用途）。
     *
     * densuke.biz 側の既存ページは削除できないため残存するが、アプリ側の densuke_urls 行を
     * 消すことで「作成可能な状態」に戻し、UI から再度 createPage を走らせられるようにする。
     * 自動作成 (densuke_sd あり) / 手動入力 (densuke_sd NULL) どちらのレコードでも同じ扱いで消せる
     * （権限チェックは Controller 層で実施）。
     *
     * @return 削除に成功した場合 true、該当レコードが存在しなかった場合 false
     */
    @Transactional
    public boolean deleteDensukeUrl(int year, int month, Long organizationId) {
        return densukeUrlRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                .map(entity -> {
                    // densuke_row_ids / densuke_member_mappings は densuke_url_id を外部キー参照しているため、
                    // 同期実績のある URL を作り直す場合、親より先に子を削除しないと FK 制約違反で 500 になる。
                    densukeRowIdRepository.deleteByDensukeUrlId(entity.getId());
                    densukeMemberMappingRepository.deleteByDensukeUrlId(entity.getId());
                    densukeUrlRepository.delete(entity);
                    return true;
                })
                .orElse(false);
    }
}
