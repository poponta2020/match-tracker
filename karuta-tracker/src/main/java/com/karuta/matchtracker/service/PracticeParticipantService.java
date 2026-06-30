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
import java.time.LocalDateTime;
import java.util.EnumSet;
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

    private static final Set<ParticipantStatus> INACTIVE_STATUSES = EnumSet.of(
            ParticipantStatus.CANCELLED,
            ParticipantStatus.DECLINED,
            ParticipantStatus.WAITLIST_DECLINED);

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

        // 当月扱いの月では既存アクティブ登録の解除を参加登録APIで受け付けない（理由付きキャンセル経由に誘導）。
        // フロントエンド側でも resolveAttendanceMode により同じ判定を行いチェック外しを禁止しているが、
        // API 直叩きでの理由なしキャンセル回避を防ぐためサーバー側にも同等の検証を入れる。
        validateAttendanceModeCancellation(request);

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

        // リクエスト内セッションを団体ごとにグループ化
        Map<Long, List<PracticeSession>> sessionsByOrg = sessions.stream()
                .collect(Collectors.groupingBy(PracticeSession::getOrganizationId));

        // プレイヤーが既にアクティブな参加登録を持つ団体IDを取得（全解除時のクリア対象特定用）
        Set<Long> existingOrgIds = findPlayerActiveOrgIds(request.getPlayerId(), request.getYear(), request.getMonth());

        // リクエストの団体 + 既存参加の団体を処理対象とする
        Set<Long> allOrgIds = new HashSet<>(sessionsByOrg.keySet());
        allOrgIds.addAll(existingOrgIds);

        for (Long orgId : allOrgIds) {
            // この団体に属するセッションIDを特定
            Set<Long> orgSessionIds = sessionsByOrg.getOrDefault(orgId, List.of()).stream()
                    .map(PracticeSession::getId).collect(Collectors.toSet());

            // この団体のparticipationsだけをフィルタしてサブリクエストを構築
            List<PracticeParticipationRequest.SessionMatchParticipation> orgParticipations =
                    request.getParticipations().stream()
                            .filter(p -> orgSessionIds.contains(p.getSessionId()))
                            .collect(Collectors.toList());

            PracticeParticipationRequest orgRequest = PracticeParticipationRequest.builder()
                    .playerId(request.getPlayerId())
                    .year(request.getYear())
                    .month(request.getMonth())
                    .participations(orgParticipations)
                    .build();

            // 未所属であれば自動的に所属させる（新規参加がある場合のみ）
            if (!orgParticipations.isEmpty()) {
                organizationService.ensurePlayerBelongsToOrganization(request.getPlayerId(), orgId);
            }

            com.karuta.matchtracker.entity.DeadlineType deadlineType = lotteryDeadlineHelper.getDeadlineType(orgId);

            if (deadlineType == com.karuta.matchtracker.entity.DeadlineType.SAME_DAY) {
                registerSameDay(orgRequest, orgId);
            } else if (lotteryDeadlineHelper.isBeforeDeadline(request.getYear(), request.getMonth(), orgId)) {
                registerBeforeDeadline(orgRequest, orgId);
            } else if (!orgParticipations.isEmpty()) {
                // 締切後は追加登録のみ（既存クリアなし）。新規参加がなければスキップ
                registerAfterDeadline(orgRequest);
            }
        }
        densukeSyncService.triggerWriteAsync();
    }

    /**
     * 「当月扱い」の月かどうかを判定する。判定ロジックはフロントエンドの
     * {@code resolveAttendanceMode} と同一：
     * <ul>
     *   <li>対象年月 == 現在年月（JST） → 当月扱い</li>
     *   <li>対象年月 &gt; 現在年月 で月内に抽選確定済み（SUCCESS）が1つでもあれば → 当月扱い</li>
     *   <li>その他（過去月／未来月で抽選未実施） → 当月扱いではない（来月扱い／過去月）</li>
     * </ul>
     *
     * 月内 SUCCESS の判定は {@link LotteryExecutionRepository#existsByTargetYearAndTargetMonthAndStatus}
     * で行う。月次抽選レコード（{@code sessionId=null}）もセッション単位の再抽選レコードも
     * いずれも {@code target_year}/{@code target_month} を持つため、このクエリ1回で両方をカバーする。
     */
    private boolean isCurrentMonthAttendanceMode(int year, int month) {
        LocalDate today = JstDateTimeUtil.today();
        int targetIndex = year * 12 + (month - 1);
        int nowIndex = today.getYear() * 12 + (today.getMonthValue() - 1);

        if (targetIndex < nowIndex) return false;
        if (targetIndex == nowIndex) return true;
        return lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                year, month, LotteryExecution.ExecutionStatus.SUCCESS);
    }

    /**
     * 当月扱いの月では、参加登録APIで既存アクティブ参加の解除を受け付けない。
     * 解除したいときはキャンセル画面（{@code /api/lottery/cancel}）で
     * 理由付きキャンセルを実施するよう誘導する。
     *
     * チェック内容：リクエスト年月が「当月扱い」と判定された場合、月内の既存アクティブ
     * 参加（CANCELLED/DECLINED/WAITLIST_DECLINED 以外）の (sessionId, matchNumber)
     * がリクエストに含まれていなければ {@link IllegalArgumentException} をスローする。
     */
    private void validateAttendanceModeCancellation(PracticeParticipationRequest request) {
        if (!isCurrentMonthAttendanceMode(request.getYear(), request.getMonth())) return;

        List<PracticeSession> monthSessions = practiceSessionRepository
                .findByYearAndMonth(request.getYear(), request.getMonth());
        if (monthSessions.isEmpty()) return;

        List<Long> monthSessionIds = monthSessions.stream()
                .map(PracticeSession::getId).collect(Collectors.toList());

        Set<String> requestKeys = request.getParticipations().stream()
                .map(p -> participationKey(p.getSessionId(), p.getMatchNumber()))
                .collect(Collectors.toSet());

        List<PracticeParticipant> existingActive = practiceParticipantRepository
                .findByPlayerIdAndSessionIds(request.getPlayerId(), monthSessionIds).stream()
                .filter(p -> p.getMatchNumber() != null && !INACTIVE_STATUSES.contains(p.getStatus()))
                .collect(Collectors.toList());

        for (PracticeParticipant existing : existingActive) {
            String key = participationKey(existing.getSessionId(), existing.getMatchNumber());
            if (!requestKeys.contains(key)) {
                throw new IllegalArgumentException(
                        "Current-month attendance cannot be canceled via participation registration. "
                                + "Use /api/lottery/cancel for reason-based cancellation. "
                                + "Missing entry: sessionId=" + existing.getSessionId()
                                + ", matchNumber=" + existing.getMatchNumber());
            }
        }
    }

    /**
     * プレイヤーが指定月にアクティブな参加登録を持つ団体IDの集合を返す。
     * 空リクエスト時にクリア対象の団体を特定するために使用する。
     */
    private Set<Long> findPlayerActiveOrgIds(Long playerId, int year, int month) {
        List<PracticeSession> allMonthSessions = practiceSessionRepository.findByYearAndMonth(year, month);
        if (allMonthSessions.isEmpty()) return Set.of();

        List<Long> allMonthSessionIds = allMonthSessions.stream()
                .map(PracticeSession::getId).collect(Collectors.toList());
        Map<Long, Long> sessionIdToOrgId = allMonthSessions.stream()
                .collect(Collectors.toMap(PracticeSession::getId, PracticeSession::getOrganizationId));

        return practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, allMonthSessionIds).stream()
                .filter(p -> p.getStatus() != null
                        && p.getStatus() != ParticipantStatus.CANCELLED
                        && p.getStatus() != ParticipantStatus.DECLINED
                        && p.getStatus() != ParticipantStatus.WAITLIST_DECLINED)
                .map(p -> sessionIdToOrgId.get(p.getSessionId()))
                .filter(orgId -> orgId != null)
                .collect(Collectors.toSet());
    }

    /**
     * SAME_DAYタイプ（わすらもち会）の参加登録
     * 抽選なし・先着順: 空きあり→即WON、定員超過→即WAITLISTED
     */
    private void registerSameDay(PracticeParticipationRequest request, Long organizationId) {
        Long playerId = request.getPlayerId();

        // 対象団体の月内セッションIDを取得
        List<Long> allMonthSessionIds = practiceSessionRepository
                .findByYearAndMonthAndOrganizationId(request.getYear(), request.getMonth(), organizationId).stream()
                .map(PracticeSession::getId).collect(Collectors.toList());

        // 月内の既存アクティブ参加（CANCELLED/DECLINED/WAITLIST_DECLINED を除く）を (sessionId, matchNumber) でマップ化。
        // 差分処理用: 「リクエストにあり既存とも一致」は no-op（dirty化・waitlistNumber再採番・通知発火を避ける）。
        Map<String, PracticeParticipant> existingActiveByKey = new HashMap<>();
        if (!allMonthSessionIds.isEmpty()) {
            practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, allMonthSessionIds).stream()
                    .filter(p -> p.getMatchNumber() != null && !INACTIVE_STATUSES.contains(p.getStatus()))
                    .forEach(p -> existingActiveByKey.put(participationKey(p.getSessionId(), p.getMatchNumber()), p));
        }

        Map<Long, PracticeSession> sessionsMap = practiceSessionRepository.findAllById(
                request.getParticipations().stream()
                        .map(PracticeParticipationRequest.SessionMatchParticipation::getSessionId)
                        .distinct().collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(PracticeSession::getId, s -> s));

        int registered = 0, waitlisted = 0, unchanged = 0;
        Set<String> requestKeys = new HashSet<>();
        Set<String> processedKeys = new HashSet<>();
        for (var participation : request.getParticipations()) {
            Long sessionId = participation.getSessionId();
            Integer matchNumber = participation.getMatchNumber();
            String key = participationKey(sessionId, matchNumber);
            if (!processedKeys.add(key)) {
                continue;
            }
            requestKeys.add(key);

            // 既存アクティブ参加と一致するキーは触らない（副作用を出さない）
            if (existingActiveByKey.containsKey(key)) {
                unchanged++;
                continue;
            }

            // 新規、または CANCELLED/DECLINED/WAITLIST_DECLINED からの復活
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

        // リクエストに含まれない既存アクティブを個別にキャンセル
        int cancelled = 0;
        LocalDateTime now = JstDateTimeUtil.now();
        for (Map.Entry<String, PracticeParticipant> entry : existingActiveByKey.entrySet()) {
            if (requestKeys.contains(entry.getKey())) continue;
            PracticeParticipant existing = entry.getValue();
            existing.setStatus(ParticipantStatus.CANCELLED);
            existing.setDirty(true);
            existing.setCancelledAt(now);
            practiceParticipantRepository.save(existing);
            cancelled++;
        }
        if (cancelled > 0) {
            practiceParticipantRepository.flush();
        }

        log.info("SAME_DAY: registered {} won, {} waitlisted, {} unchanged, {} cancelled for player {}",
                registered, waitlisted, unchanged, cancelled, playerId);
    }

    private void registerBeforeDeadline(PracticeParticipationRequest request, Long organizationId) {
        List<Long> allMonthSessionIds = practiceSessionRepository
                .findByYearAndMonthAndOrganizationId(request.getYear(), request.getMonth(), organizationId).stream()
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
        long offeredCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                session.getId(), matchNumber, ParticipantStatus.OFFERED);
        if (wonCount + offeredCount >= session.getCapacity()) return false;
        return !practiceParticipantRepository.existsBySessionIdAndMatchNumberAndStatus(
                session.getId(), matchNumber, ParticipantStatus.WAITLISTED);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<Integer>> getPlayerParticipationsByMonth(Long playerId, int year, int month) {
        List<Long> sessionIds = practiceSessionRepository.findByYearAndMonth(year, month).stream()
                .map(PracticeSession::getId).collect(Collectors.toList());
        if (sessionIds.isEmpty()) return Map.of();
        return practiceParticipantRepository.findByPlayerIdAndSessionIds(playerId, sessionIds).stream()
                .filter(p -> p.getMatchNumber() != null)
                .filter(p -> !INACTIVE_STATUSES.contains(p.getStatus()))
                .collect(Collectors.groupingBy(PracticeParticipant::getSessionId,
                        Collectors.mapping(PracticeParticipant::getMatchNumber, Collectors.toList())));
    }

    @Transactional(readOnly = true)
    public PlayerParticipationStatusDto getPlayerParticipationStatusByMonth(Long playerId, int year, int month) {
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        if (sessionIds.isEmpty()) {
            // 月内にセッションがない場合でも、月次抽選レコード（sessionId=null）が
            // 存在しうるため hasAnyExecutedLotteryInMonth は別途判定する。
            boolean monthlyExecutedNoSessions = lotteryExecutionRepository
                    .existsByTargetYearAndTargetMonthAndStatus(
                            year, month, LotteryExecution.ExecutionStatus.SUCCESS);
            return PlayerParticipationStatusDto.builder()
                    .participations(Map.of())
                    .lotteryExecuted(Map.of())
                    .hasAnyExecutedLotteryInMonth(monthlyExecutedNoSessions)
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

        // 月内の SUCCESS な LotteryExecution を取得し、セッション単位の lotteryExecuted を構築する。
        // - sessionId 紐づきレコード（再抽選など） → 当該セッションのみ true
        // - 月次抽選レコード（sessionId=null）
        //   - organizationId=null → 月内の全セッションを true（全団体一括抽選）
        //   - organizationId 指定 → 同じ organizationId のセッションのみ true（混在月で他団体を巻き込まない）
        // これにより要件「抽選実行済みセッションはステータス表示固定」を月次抽選にも適用する。
        List<LotteryExecution> monthSuccessLotteries = lotteryExecutionRepository
                .findByTargetYearAndTargetMonth(year, month).stream()
                .filter(exec -> exec.getStatus() == LotteryExecution.ExecutionStatus.SUCCESS)
                .collect(Collectors.toList());

        Map<Long, Boolean> lotteryMap = new HashMap<>();
        sessionIds.forEach(sid -> lotteryMap.put(sid, false));
        for (LotteryExecution exec : monthSuccessLotteries) {
            if (exec.getSessionId() != null) {
                lotteryMap.put(exec.getSessionId(), true);
            } else {
                Long execOrgId = exec.getOrganizationId();
                for (PracticeSession s : sessions) {
                    if (execOrgId == null || execOrgId.equals(s.getOrganizationId())) {
                        lotteryMap.put(s.getId(), true);
                    }
                }
            }
        }

        // セッションからorganizationIdを取得
        Long orgId = sessions.isEmpty() ? null : sessions.get(0).getOrganizationId();
        boolean beforeDeadline = lotteryDeadlineHelper.isBeforeDeadline(year, month, orgId);

        // 月単位の「当月扱い」判定用：月内に1つでも SUCCESS な LotteryExecution があれば true
        boolean hasAnyExecutedLotteryInMonth = !monthSuccessLotteries.isEmpty();

        return PlayerParticipationStatusDto.builder()
                .participations(participationMap)
                .lotteryExecuted(lotteryMap)
                .hasAnyExecutedLotteryInMonth(hasAnyExecutedLotteryInMonth)
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

    /**
     * 試合記録に伴う対戦選手の自動参加登録（サーバ内部用・ガードなし・冪等）。
     *
     * 試合保存（{@link MatchService#createMatch}）の副作用として、対戦に関与した登録済み選手が
     * 当日セッションの当該試合に未参加なら WON で参加登録する。
     * 「対戦したなら参加者」というデータ整合を保ち、結果閲覧・一括入力・参加者一覧に相手が現れるようにする。
     *
     * 直接の参加登録API（{@code POST /participations}）の「PLAYER は自分のみ」ガードはあくまで温存し、
     * ここではサーバ主導の副作用としてガードなしで登録する。
     *
     * 設計上の判断:
     * <ul>
     *   <li>status は {@link ParticipantStatus#WON}（試合が成立した＝確定参加。
     *       管理者の {@link #addParticipantToMatch} と同じ扱い）</li>
     *   <li>冪等: 既にアクティブ参加（CANCELLED/DECLINED/WAITLIST_DECLINED 以外）なら何もしない</li>
     *   <li>densuke 同期はここではトリガーしない。試合保存のホットパスで毎回外部書き込みを
     *       誘発しないため。参加者の伝助反映は別経路（参加登録・編集）に委ねる</li>
     * </ul>
     *
     * @return 新規にアクティブ参加として登録した場合 true、no-op の場合 false
     */
    @Transactional
    public boolean autoRegisterMatchParticipant(Long sessionId, Long playerId, Integer matchNumber) {
        if (sessionId == null || playerId == null || playerId == 0L || matchNumber == null) {
            return false;
        }
        if (practiceParticipantRepository
                .existsActiveBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber)) {
            return false; // 既に参加済み（冪等）
        }
        saveOrReuseParticipant(sessionId, playerId, matchNumber, ParticipantStatus.WON, null);
        log.info("試合記録に伴う自動参加登録: sessionId={}, playerId={}, matchNumber={}",
                sessionId, playerId, matchNumber);
        return true;
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

        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);
        return buildParticipationRates(sessions, allParticipants, null);
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

        Set<Long> memberPlayerIds = playerOrganizationRepository.findByOrganizationId(organizationId).stream()
                .map(PlayerOrganization::getPlayerId).collect(Collectors.toSet());

        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);
        return buildParticipationRates(sessions, allParticipants, memberPlayerIds);
    }

    private List<ParticipationRateDto> computeAllParticipationRates(int year, int month, List<Long> organizationIds) {
        LocalDate today = JstDateTimeUtil.today();
        List<PracticeSession> sessions = practiceSessionRepository.findByOrganizationIdInAndYearAndMonth(organizationIds, year, month).stream()
                .filter(s -> !s.getSessionDate().isAfter(today)).collect(Collectors.toList());
        if (sessions.isEmpty()) return List.of();

        Set<Long> memberPlayerIds = organizationIds.stream()
                .flatMap(orgId -> playerOrganizationRepository.findByOrganizationId(orgId).stream())
                .map(PlayerOrganization::getPlayerId)
                .collect(Collectors.toSet());

        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).collect(Collectors.toList());
        List<PracticeParticipant> allParticipants = practiceParticipantRepository.findBySessionIdIn(sessionIds);
        return buildParticipationRates(sessions, allParticipants, memberPlayerIds);
    }

    /**
     * セッション群と参加レコードから参加率DTOリストを構築する共通ロジック。
     *
     * 分子（participatedMatches）の数え方:
     * <ul>
     *   <li>ステータスが {@link ParticipantStatus#isActive()}（WON/PENDING）の行のみカウントする。
     *       CANCELLED/DECLINED/WAITLISTED/OFFERED/WAITLIST_DECLINED は「参加」に含めない。
     *       （status が null の legacy 行は WON 扱いとし、本サービス内の他処理と整合させる）</li>
     *   <li>各セッションで参加数を totalMatches で上限キャップしてから合算する。
     *       これにより matchNumber=null の抜け番行（仕様上カウント対象）を含めても、
     *       分子が分母（Σ totalMatches）を超えず、参加率が 100% を超えない。</li>
     * </ul>
     *
     * @param memberPlayerIds 対象プレイヤーのフィルタ。null の場合は全プレイヤーを対象とする。
     */
    private List<ParticipationRateDto> buildParticipationRates(
            List<PracticeSession> sessions,
            List<PracticeParticipant> allParticipants,
            Set<Long> memberPlayerIds) {

        int totalScheduledMatches = sessions.stream()
                .mapToInt(s -> s.getTotalMatches() != null ? s.getTotalMatches() : 0).sum();
        if (totalScheduledMatches == 0) return List.of();

        // セッションごとの予定試合数（参加数の上限値）
        Map<Long, Integer> sessionCap = sessions.stream()
                .collect(Collectors.toMap(PracticeSession::getId,
                        s -> s.getTotalMatches() != null ? s.getTotalMatches() : 0));

        // playerId -> (sessionId -> 有効参加数) を集計
        Map<Long, Map<Long, Integer>> perPlayerPerSession = new HashMap<>();
        allParticipants.stream()
                .filter(pp -> memberPlayerIds == null || memberPlayerIds.contains(pp.getPlayerId()))
                .filter(pp -> pp.getStatus() == null || pp.getStatus().isActive())
                .forEach(pp -> perPlayerPerSession
                        .computeIfAbsent(pp.getPlayerId(), k -> new HashMap<>())
                        .merge(pp.getSessionId(), 1, Integer::sum));

        Map<Long, String> names = playerRepository.findAllActive().stream()
                .collect(Collectors.toMap(Player::getId, Player::getName));

        return perPlayerPerSession.entrySet().stream()
                .map(entry -> {
                    Long playerId = entry.getKey();
                    // 各セッションで totalMatches を上限にキャップしてから合算
                    int participated = entry.getValue().entrySet().stream()
                            .mapToInt(e -> Math.min(e.getValue(), sessionCap.getOrDefault(e.getKey(), 0)))
                            .sum();
                    return ParticipationRateDto.builder()
                            .playerId(playerId)
                            .playerName(names.getOrDefault(playerId, "不明"))
                            .participatedMatches(participated)
                            .totalScheduledMatches(totalScheduledMatches)
                            .rate((double) participated / totalScheduledMatches)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
