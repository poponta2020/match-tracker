package com.karuta.matchtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.karuta.matchtracker.dto.AdminEditParticipantsRequest;
import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.dto.ConfirmLotteryResponse;
import com.karuta.matchtracker.dto.DensukeWriteResult;
import com.karuta.matchtracker.dto.LotteryResultDto;
import com.karuta.matchtracker.dto.MonthlyApplicantDto;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionStatus;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Venue;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
 *    a. 前試合落選者（低優先度）とその他を分離
 *    b. その他の参加者で優先当選/一般抽選を実施
 *    c. 余り枠があれば前試合落選者を当選させる（定員超過時は優先的に落選）
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
    private final LineNotificationService lineNotificationService;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final DensukeWriteService densukeWriteService;
    private final ObjectMapper objectMapper;
    private final LotteryQueryService lotteryQueryService;
    private final PlayerOrganizationRepository playerOrganizationRepository;
    private final TransactionTemplate transactionTemplate;

    // details JSON 用の内部レコード
    record LotteryDetails(List<SessionDetail> sessions) {}
    record SessionDetail(Long sessionId, LocalDate date, List<MatchDetail> matches) {}
    record MatchDetail(int match, int applicants, int winners, int waitlisted) {}
    record ReLotteryDetails(Long sessionId, int promotedKept, int relotteryTargets, long seed, int priorityPlayerCount) {}
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
    public LotteryExecution executeLottery(int year, int month, Long executedBy, ExecutionType type, Long organizationId, Long seed, List<Long> priorityPlayerIds) {
        log.info("Starting lottery for {}-{} (type: {})", year, month, type);

        LotteryExecution execution = LotteryExecution.builder()
                .targetYear(year)
                .targetMonth(month)
                .executionType(type)
                .executedBy(executedBy)
                .executedAt(JstDateTimeUtil.now())
                .status(ExecutionStatus.SUCCESS)
                .organizationId(organizationId)
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
                execution.setPriorityPlayerIds(priorityPlayerIds);
                return lotteryExecutionRepository.save(execution);
            }

            // 月内の落選者を追跡する（セッション跨ぎの優先当選判定用）
            Set<Long> monthlyLosers = new HashSet<>();
            List<SessionDetail> sessionDetails = new ArrayList<>();
            Random random = new Random(seed);
            Set<Long> adminPrioritySet = priorityPlayerIds != null ? new HashSet<>(priorityPlayerIds) : Set.of();

            for (PracticeSession session : sessions) {
                SessionDetail sessionDetail = processSession(session, monthlyLosers, execution.getId(), adminPrioritySet, true, random);
                sessionDetails.add(sessionDetail);
            }

            execution.setDetails(toJson(new LotteryDetails(sessionDetails)));
            execution.setPriorityPlayerIds(priorityPlayerIds);

            log.info("Lottery completed for {}-{}: {} sessions processed", year, month, sessions.size());

        } catch (Exception e) {
            log.error("Lottery failed for {}-{}", year, month, e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setDetails(toJson(new ErrorDetail(e.getMessage())));
        }

        return lotteryExecutionRepository.save(execution);
    }

    /**
     * 抽選を実行し、即座に確定する（プレビュー後の確定用）。
     * executeLottery + confirmLottery を1回の抽選実行で行う。
     *
     * 抽選自体が失敗した場合は {@code densukeWriteSucceeded = true}（書き戻し未実施）で返す。
     * 抽選成功・伝助書き戻し失敗時は確定 DB は維持し、{@code densukeWriteSucceeded = false}
     * とエラーメッセージをレスポンスに含めて呼び出し元に伝搬する。
     *
     * 本メソッドは意図的に {@code @Transactional} を付けない。抽選結果の DB 反映と確定情報の保存は
     * {@link TransactionTemplate} で囲んだブロック内で確実にコミットさせ、コミット後に
     * {@link DensukeWriteService#writeAllForLotteryConfirmation} を呼び出す。
     * これにより {@code REQUIRES_NEW} で開始される伝助書き戻し側の別トランザクションが、
     * 確定済みの {@code WON} / {@code WAITLISTED} レコードを参照できることを保証する。
     */
    public ConfirmLotteryResponse executeAndConfirmLottery(int year, int month, Long executedBy, Long organizationId, long seed, List<Long> priorityPlayerIds) {
        // 抽選実行 + 確定情報保存を1つのトランザクション内でコミットさせる。
        // 同一クラス内の self-invocation では @Transactional が効かないため、
        // TransactionTemplate を用いて明示的に外側トランザクションを構築する。
        LotteryExecution execution = transactionTemplate.execute(status -> {
            LotteryExecution exec = executeLottery(
                    year, month, executedBy, ExecutionType.MANUAL, organizationId, seed, priorityPlayerIds);
            if (exec.getStatus() != ExecutionStatus.SUCCESS) {
                return exec;
            }
            exec.setPriorityPlayerIds(priorityPlayerIds);
            exec.setConfirmedAt(JstDateTimeUtil.now());
            exec.setConfirmedBy(executedBy);
            return lotteryExecutionRepository.save(exec);
        });

        if (execution == null || execution.getStatus() != ExecutionStatus.SUCCESS) {
            return ConfirmLotteryResponse.builder()
                    .execution(execution)
                    .densukeWriteSucceeded(true)
                    .build();
        }

        log.info("Lottery executed and confirmed for {}-{} by user {}", year, month, executedBy);

        // 伝助への一括書き戻し
        // 書き戻し失敗（HTTP 4xx/5xx、メンバーID取得失敗、リストページ取得失敗 等）は
        // DensukeWriteResult として返ってくるので、densukeWriteSucceeded に反映する。
        // この時点では外側 TransactionTemplate のトランザクションは既にコミット済みのため、
        // writeAllForLotteryConfirmation 側 (REQUIRES_NEW) は最新の WON/WAITLISTED を読み取れる。
        boolean densukeWriteSucceeded = true;
        String densukeWriteError = null;
        if (organizationId != null) {
            try {
                DensukeWriteResult result = densukeWriteService.writeAllForLotteryConfirmation(organizationId, year, month);
                if (!result.isSuccess()) {
                    log.warn("Densuke write-back returned failures after lottery confirmation: {}", result.getErrors());
                    densukeWriteSucceeded = false;
                    densukeWriteError = String.join("; ", result.getErrors());
                }
            } catch (Exception e) {
                log.error("Failed to write all to densuke after lottery confirmation: {}", e.getMessage(), e);
                densukeWriteSucceeded = false;
                densukeWriteError = e.getMessage();
            }
        }

        return ConfirmLotteryResponse.builder()
                .execution(execution)
                .densukeWriteSucceeded(densukeWriteSucceeded)
                .densukeWriteError(densukeWriteError)
                .build();
    }

    /**
     * 1セッション（1日）の全試合を処理する
     */
    private SessionDetail processSession(PracticeSession session, Set<Long> monthlyLosers, Long lotteryId, Set<Long> adminPriorityPlayers, boolean saveResults, Random random) {
        // セッションに定員が未設定の場合、会場の定員にフォールバック
        if (session.getCapacity() == null && session.getVenueId() != null) {
            venueRepository.findById(session.getVenueId())
                    .ifPresent(venue -> session.setCapacity(venue.getCapacity()));
        }

        log.debug("Processing session: {} (date: {}, capacity: {})",
                session.getId(), session.getSessionDate(), session.getCapacity());

        // このセッション内の落選者を追跡（連鎖落選用）
        Set<Long> sessionLosers = new HashSet<>();

        // このセッションの全PENDING参加者を取得（ID順でシード再現性を確保）
        List<PracticeParticipant> allParticipants = practiceParticipantRepository
                .findBySessionIdAndStatus(session.getId(), ParticipantStatus.PENDING);
        allParticipants.sort(Comparator.comparingLong(PracticeParticipant::getId));

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
                    sessionLosers, monthlyLosers, lotteryId, inheritedOrder, currentWaitlistOrder, adminPriorityPlayers, saveResults, random);
            matchDetails.add(detail);

            prevWaitlistOrder = currentWaitlistOrder;
            prevMatchNumber = matchNumber;
        }

        return new SessionDetail(session.getId(), session.getSessionDate(), matchDetails);
    }

    /**
     * 1試合の抽選を処理する（3層優先: 管理者優先 > 連続落選救済 > 一般）
     *
     * package-private にすることでユニットテストから直接呼び出し可能にしている。
     */
    MatchDetail processMatch(PracticeSession session, int matchNumber,
                                List<PracticeParticipant> applicants,
                                Set<Long> sessionLosers, Set<Long> monthlyLosers,
                                Long lotteryId,
                                Map<Long, Integer> previousMatchWaitlistOrder,
                                Map<Long, Integer> currentMatchWaitlistOrder,
                                Set<Long> adminPriorityPlayers,
                                boolean saveResults, Random random) {

        Integer capacity = session.getCapacity();
        int totalApplicants = applicants.size();

        // 定員未設定 or 定員以下 → 全員当選
        if (capacity == null || totalApplicants <= capacity) {
            for (PracticeParticipant p : applicants) {
                p.setStatus(ParticipantStatus.WON);
                p.setDirty(true);
                p.setLotteryId(lotteryId);
            }
            if (saveResults) {
                practiceParticipantRepository.saveAll(applicants);
            }

            log.debug("Match {}: all {} applicants win (capacity: {})",
                    matchNumber, totalApplicants, capacity);
            return new MatchDetail(matchNumber, totalApplicants, totalApplicants, 0);
        }

        // 定員超過 → 抽選が必要
        log.debug("Match {}: {} applicants for {} capacity - lottery required",
                matchNumber, totalApplicants, capacity);

        // Step 1: 前試合落選者（低優先度）とその他を分離
        List<PracticeParticipant> cascadeCandidates = new ArrayList<>();
        List<PracticeParticipant> remaining = new ArrayList<>();

        for (PracticeParticipant p : applicants) {
            if (sessionLosers.contains(p.getPlayerId())) {
                cascadeCandidates.add(p);
            } else {
                remaining.add(p);
            }
        }

        log.debug("Match {}: {} cascade candidates (low priority), {} remaining",
                matchNumber, cascadeCandidates.size(), remaining.size());

        // Step 2: remaining を3層（管理者優先・連続落選救済・一般）に分類して抽選
        List<PracticeParticipant> winners = new ArrayList<>();
        List<PracticeParticipant> adminLosers = new ArrayList<>();
        List<PracticeParticipant> rescueLosers = new ArrayList<>();
        List<PracticeParticipant> generalLosers = new ArrayList<>();

        if (remaining.size() <= capacity) {
            winners.addAll(remaining);
        } else {
            List<PracticeParticipant> adminPriorityApplicants = new ArrayList<>();
            List<PracticeParticipant> rescueApplicants = new ArrayList<>();
            List<PracticeParticipant> generalApplicants = new ArrayList<>();

            for (PracticeParticipant p : remaining) {
                if (adminPriorityPlayers.contains(p.getPlayerId())) {
                    adminPriorityApplicants.add(p);
                } else if (monthlyLosers.contains(p.getPlayerId())) {
                    rescueApplicants.add(p);
                } else {
                    generalApplicants.add(p);
                }
            }

            log.debug("Match {}: {} admin-priority, {} rescue, {} general applicants",
                    matchNumber, adminPriorityApplicants.size(), rescueApplicants.size(), generalApplicants.size());

            // Step 2a: 管理者優先枠（最高優先度）から埋める
            if (adminPriorityApplicants.size() <= capacity) {
                winners.addAll(adminPriorityApplicants);
            } else {
                Collections.shuffle(adminPriorityApplicants, random);
                winners.addAll(adminPriorityApplicants.subList(0, capacity));
                adminLosers.addAll(adminPriorityApplicants.subList(capacity, adminPriorityApplicants.size()));
            }

            // Step 2b: 残り枠で連続落選救済 + 一般を2層抽選
            int slotsAfterAdmin = capacity - winners.size();
            if (slotsAfterAdmin > 0) {
                if (rescueApplicants.size() + generalApplicants.size() <= slotsAfterAdmin) {
                    winners.addAll(rescueApplicants);
                    winners.addAll(generalApplicants);
                } else {
                    // 一般枠最低保証を残り枠ベースで計算
                    int normalReservePercent = systemSettingService.getLotteryNormalReservePercent(session.getOrganizationId());
                    int normalReserve = 0;
                    if (normalReservePercent > 0 && !generalApplicants.isEmpty() && !rescueApplicants.isEmpty()) {
                        normalReserve = Math.max(1, (int) Math.ceil(slotsAfterAdmin * normalReservePercent / 100.0));
                        normalReserve = Math.min(normalReserve, generalApplicants.size());
                        normalReserve = Math.min(normalReserve, slotsAfterAdmin);
                    }
                    int rescueSlots = slotsAfterAdmin - normalReserve;

                    if (rescueApplicants.size() >= rescueSlots && normalReserve > 0) {
                        // 救済枠 + 一般枠に分けて抽選
                        Collections.shuffle(rescueApplicants, random);
                        winners.addAll(rescueApplicants.subList(0, rescueSlots));
                        rescueLosers.addAll(rescueApplicants.subList(rescueSlots, rescueApplicants.size()));

                        Collections.shuffle(generalApplicants, random);
                        winners.addAll(generalApplicants.subList(0, normalReserve));
                        if (generalApplicants.size() > normalReserve) {
                            generalLosers.addAll(generalApplicants.subList(normalReserve, generalApplicants.size()));
                        }
                    } else if (rescueApplicants.size() >= slotsAfterAdmin) {
                        // 一般申込者0 or 設定0% で救済者が残り枠以上
                        Collections.shuffle(rescueApplicants, random);
                        winners.addAll(rescueApplicants.subList(0, slotsAfterAdmin));
                        rescueLosers.addAll(rescueApplicants.subList(slotsAfterAdmin, rescueApplicants.size()));
                        generalLosers.addAll(generalApplicants);
                    } else {
                        // 救済者全員当選 + 残り枠を一般からランダム
                        winners.addAll(rescueApplicants);
                        int remainingForGeneral = slotsAfterAdmin - rescueApplicants.size();

                        Collections.shuffle(generalApplicants, random);
                        winners.addAll(generalApplicants.subList(0, Math.min(remainingForGeneral, generalApplicants.size())));
                        if (generalApplicants.size() > remainingForGeneral) {
                            generalLosers.addAll(generalApplicants.subList(remainingForGeneral, generalApplicants.size()));
                        }
                    }
                }
            } else {
                // 残り枠なし → 救済・一般は全員落選
                rescueLosers.addAll(rescueApplicants);
                generalLosers.addAll(generalApplicants);
            }
        }

        // Step 2c: 余り枠があれば前試合落選者を当選させる
        List<PracticeParticipant> cascadeLosers = new ArrayList<>();
        int leftoverSlots = capacity - winners.size();

        if (leftoverSlots > 0 && !cascadeCandidates.isEmpty()) {
            if (cascadeCandidates.size() <= leftoverSlots) {
                winners.addAll(cascadeCandidates);
                log.debug("Match {}: all {} cascade candidates win (leftover slots: {})",
                        matchNumber, cascadeCandidates.size(), leftoverSlots);
            } else {
                Collections.shuffle(cascadeCandidates, random);
                winners.addAll(cascadeCandidates.subList(0, leftoverSlots));
                cascadeLosers.addAll(cascadeCandidates.subList(leftoverSlots, cascadeCandidates.size()));
                log.debug("Match {}: {} cascade candidates win, {} lose (leftover slots: {})",
                        matchNumber, leftoverSlots, cascadeLosers.size(), leftoverSlots);
            }
        } else {
            cascadeLosers.addAll(cascadeCandidates);
            if (!cascadeCandidates.isEmpty()) {
                log.debug("Match {}: all {} cascade candidates lose (no leftover slots)",
                        matchNumber, cascadeCandidates.size());
            }
        }

        // Step 3: ステータス更新 - 当選者
        for (PracticeParticipant p : winners) {
            p.setStatus(ParticipantStatus.WON);
            p.setDirty(true);
            p.setLotteryId(lotteryId);
        }

        // Step 4: 落選者にキャンセル待ち番号を付与
        // 管理者優先落選 → 救済落選 → 一般落選 → 連鎖落選 の順で優先
        List<PracticeParticipant> allLosers = new ArrayList<>();
        allLosers.addAll(adminLosers);
        allLosers.addAll(rescueLosers);
        allLosers.addAll(generalLosers);
        allLosers.addAll(cascadeLosers);

        // キャンセル待ち番号を割り当て（連続試合では前試合の順番を引き継ぐ）
        // 前試合の順番を持つ選手は維持、新規落選は優先度順かつグループ内ランダム
        List<PracticeParticipant> withPrevOrder = new ArrayList<>();
        List<PracticeParticipant> newAdminLosers = new ArrayList<>();
        List<PracticeParticipant> newRescueLosers = new ArrayList<>();
        List<PracticeParticipant> newGeneralLosers = new ArrayList<>();
        List<PracticeParticipant> newCascadeLosers = new ArrayList<>();

        for (PracticeParticipant p : adminLosers) {
            if (previousMatchWaitlistOrder.containsKey(p.getPlayerId())) withPrevOrder.add(p);
            else newAdminLosers.add(p);
        }
        for (PracticeParticipant p : rescueLosers) {
            if (previousMatchWaitlistOrder.containsKey(p.getPlayerId())) withPrevOrder.add(p);
            else newRescueLosers.add(p);
        }
        for (PracticeParticipant p : generalLosers) {
            if (previousMatchWaitlistOrder.containsKey(p.getPlayerId())) withPrevOrder.add(p);
            else newGeneralLosers.add(p);
        }
        for (PracticeParticipant p : cascadeLosers) {
            if (previousMatchWaitlistOrder.containsKey(p.getPlayerId())) withPrevOrder.add(p);
            else newCascadeLosers.add(p);
        }

        withPrevOrder.sort(Comparator.comparingInt(p -> previousMatchWaitlistOrder.get(p.getPlayerId())));
        Collections.shuffle(newAdminLosers, random);
        Collections.shuffle(newRescueLosers, random);
        Collections.shuffle(newGeneralLosers, random);
        Collections.shuffle(newCascadeLosers, random);

        List<PracticeParticipant> orderedLosers = new ArrayList<>();
        orderedLosers.addAll(withPrevOrder);
        orderedLosers.addAll(newAdminLosers);
        orderedLosers.addAll(newRescueLosers);
        orderedLosers.addAll(newGeneralLosers);
        orderedLosers.addAll(newCascadeLosers);

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
        if (saveResults) {
            practiceParticipantRepository.saveAll(all);
        }

        log.info("Match {}: {} winners, {} waitlisted (from {} applicants, capacity {})",
                matchNumber, winners.size(), allLosers.size(), totalApplicants, capacity);

        return new MatchDetail(matchNumber, totalApplicants, winners.size(), allLosers.size());
    }

    /**
     * 指定年月の抽選をプレビュー実行する（DB保存なし）
     *
     * @param year           対象年
     * @param month          対象月
     * @param organizationId 団体ID（nullの場合は全団体）
     * @return セッションごとの抽選結果プレビュー
     */
    /**
     * プレビュー結果とシードを保持するレコード
     */
    public record LotteryPreviewResult(List<LotteryResultDto> results, long seed) {}

    @Transactional(readOnly = true)
    public LotteryPreviewResult previewLottery(int year, int month, Long organizationId, List<Long> priorityPlayerIds) {
        long seed = new Random().nextLong();
        log.info("Previewing lottery for {}-{} (orgId: {}, seed: {})", year, month, organizationId, seed);

        // 対象月のセッションを日付昇順で取得
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
            return new LotteryPreviewResult(List.of(), seed);
        }

        // 月内の落選者を追跡する（セッション跨ぎの優先当選判定用）
        Set<Long> monthlyLosers = new HashSet<>();
        // セッションごとに処理された参加者を保持（DTO組み立て用）
        Map<Long, List<PracticeParticipant>> participantsBySession = new LinkedHashMap<>();
        Random random = new Random(seed);
        Set<Long> adminPrioritySet = priorityPlayerIds != null ? new HashSet<>(priorityPlayerIds) : Set.of();

        for (PracticeSession session : sessions) {
            // PENDING参加者を取得
            List<PracticeParticipant> participants = practiceParticipantRepository
                    .findBySessionIdAndStatus(session.getId(), ParticipantStatus.PENDING);
            participants.sort(Comparator.comparingLong(PracticeParticipant::getId));

            // 抽選アルゴリズムを実行（DB保存なし）
            processSession(session, monthlyLosers, null, adminPrioritySet, false, random);

            // 処理後の参加者（ステータスがインメモリで更新済み）を保持
            participantsBySession.put(session.getId(), participants);
        }

        // インメモリの参加者データからLotteryResultDtoを組み立て
        List<LotteryResultDto> results = new ArrayList<>();
        for (PracticeSession session : sessions) {
            List<PracticeParticipant> participants = participantsBySession.get(session.getId());
            if (participants == null || participants.isEmpty()) {
                continue;
            }
            results.add(buildLotteryResultFromMemory(session, participants));
        }

        log.info("Lottery preview completed for {}-{}: {} sessions", year, month, results.size());
        return new LotteryPreviewResult(results, seed);
    }

    /**
     * 対象月・団体で参加希望を出している選手の一意リストを級順で取得する。
     *
     * 優先選手指定UI用の参照データ。集計対象は「実質的に参加希望を維持している」ステータス
     * （PENDING / WON / WAITLISTED / OFFERED）で、CANCELLED / DECLINED / WAITLIST_DECLINED は除外する。
     *
     * @param year           対象年
     * @param month          対象月
     * @param organizationId 団体ID（nullの場合は全団体）
     * @return 一意な選手DTOのリスト（級位→段位→あいうえお順）
     */
    @Transactional(readOnly = true)
    public List<MonthlyApplicantDto> getMonthlyApplicants(int year, int month, Long organizationId) {
        Set<Long> applicantPlayerIds = loadApplicantPlayerIds(year, month, organizationId);
        if (applicantPlayerIds.isEmpty()) {
            return List.of();
        }

        List<Player> players = playerRepository.findAllById(applicantPlayerIds);
        players.sort(PlayerSortHelper.playerComparator());

        return players.stream()
                .map(p -> MonthlyApplicantDto.builder()
                        .playerId(p.getId())
                        .name(p.getName())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 優先選手指定リクエストのバリデーション。
     *
     * - 指定選手が対象団体に所属していない場合は {@link ForbiddenException}（403）
     * - 指定選手が対象月・団体で参加希望を出していない場合は {@link IllegalArgumentException}（400）
     *
     * 団体所属の判定は {@link PlayerOrganizationRepository} の中間テーブルで行う。
     * SUPER_ADMIN が全団体対象で呼び出す場合（organizationId == null）は団体所属チェックを省略し、
     * 参加希望チェックのみを行う。
     *
     * @param ids            優先指定された選手ID一覧
     * @param year           対象年
     * @param month          対象月
     * @param organizationId 対象団体ID（nullの場合は全団体モード）
     * @throws ForbiddenException       指定選手に他団体所属のものが含まれる場合
     * @throws IllegalArgumentException 指定選手に参加希望未提出のものが含まれる場合
     */
    @Transactional(readOnly = true)
    public void validatePriorityPlayerIds(List<Long> ids, int year, int month, Long organizationId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        // 1) 団体所属チェック（他団体所属の選手は403）
        if (organizationId != null) {
            List<Long> foreignIds = ids.stream()
                    .filter(id -> !playerOrganizationRepository
                            .existsByPlayerIdAndOrganizationId(id, organizationId))
                    .collect(Collectors.toList());
            if (!foreignIds.isEmpty()) {
                throw new ForbiddenException(
                        "他団体の選手を優先選手に指定することはできません: playerIds=" + foreignIds);
            }
        }

        // 2) 参加希望チェック（希望未提出の選手は400）
        Set<Long> applicantPlayerIds = loadApplicantPlayerIds(year, month, organizationId);
        List<Long> missingIds = ids.stream()
                .filter(id -> !applicantPlayerIds.contains(id))
                .collect(Collectors.toList());
        if (!missingIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "参加希望を出していない選手が含まれています: playerIds=" + missingIds);
        }
    }

    /**
     * 対象月・団体で参加希望を出している選手IDの集合を取得する。
     * 有効な参加希望ステータスは PENDING / WON / WAITLISTED / OFFERED のみ。
     */
    private Set<Long> loadApplicantPlayerIds(int year, int month, Long organizationId) {
        List<PracticeSession> sessions = organizationId != null
                ? practiceSessionRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                : practiceSessionRepository.findByYearAndMonth(year, month);

        if (sessions.isEmpty()) {
            return Set.of();
        }

        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());

        return practiceParticipantRepository.findBySessionIdIn(sessionIds).stream()
                .filter(p -> {
                    ParticipantStatus s = p.getStatus();
                    return s == ParticipantStatus.PENDING
                            || s == ParticipantStatus.WON
                            || s == ParticipantStatus.WAITLISTED
                            || s == ParticipantStatus.OFFERED;
                })
                .map(PracticeParticipant::getPlayerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * インメモリの参加者データからLotteryResultDtoを組み立てる（プレビュー用）
     */
    private LotteryResultDto buildLotteryResultFromMemory(PracticeSession session,
                                                           List<PracticeParticipant> participants) {
        // プレイヤー情報を取得
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
                .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber,
                        TreeMap::new, Collectors.toList()));

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
                } else if (p.getStatus() == ParticipantStatus.WAITLISTED) {
                    waitlisted.add(result);
                }
            }

            waitlisted.sort(Comparator.comparing(r ->
                    r.getWaitlistNumber() != null ? r.getWaitlistNumber() : Integer.MAX_VALUE));

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
     * セッション単位で再抽選を実行する
     *
     * - キャンセル待ちから繰り上がった人（WON + respondedAt != null）は維持
     * - それ以外の当選者・キャンセル待ちをリセットして再抽選
     * - 同月内の他セッションの落選者は優先当選の対象
     */
    /**
     * @param priorityPlayerIds 優先選手IDリスト。null の場合は直近の抽選実行から引き継ぐ。空リストは明示的なクリア。
     */
    @Transactional
    public LotteryExecution reExecuteLottery(Long sessionId, Long executedBy, List<Long> priorityPlayerIds) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        int year = session.getSessionDate().getYear();
        int month = session.getSessionDate().getMonthValue();
        Long orgId = session.getOrganizationId();

        log.info("Re-executing lottery for session {} (date: {})", sessionId, session.getSessionDate());

        // 優先選手IDの解決：null = 直近の抽選から引き継ぐ（save前に実行してself除外）
        List<Long> resolvedPriorityPlayerIds;
        if (priorityPlayerIds != null) {
            // 空リストは明示的クリア、非空リストはバリデーションして使用
            if (!priorityPlayerIds.isEmpty()) {
                validatePriorityPlayerIds(priorityPlayerIds, year, month, orgId);
            }
            resolvedPriorityPlayerIds = priorityPlayerIds;
        } else {
            // 直近の成功した抽選実行から priorityPlayerIds を引き継ぐ
            LotteryExecution latest = orgId != null
                    ? lotteryExecutionRepository
                            .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                                    year, month, orgId, ExecutionStatus.SUCCESS)
                            .orElse(null)
                    : lotteryExecutionRepository
                            .findTopByTargetYearAndTargetMonthAndOrganizationIdIsNullAndStatusOrderByExecutedAtDesc(
                                    year, month, ExecutionStatus.SUCCESS)
                            .orElse(null);
            resolvedPriorityPlayerIds = latest != null ? latest.getPriorityPlayerIds() : List.of();
            log.debug("Re-lottery inheriting {} priority players from latest execution",
                    resolvedPriorityPlayerIds.size());
        }
        Set<Long> adminPrioritySet = new HashSet<>(resolvedPriorityPlayerIds);

        LotteryExecution execution = LotteryExecution.builder()
                .targetYear(year)
                .targetMonth(month)
                .executionType(ExecutionType.MANUAL_RELOTTERY)
                .sessionId(sessionId)
                .organizationId(orgId)
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

            // 月内の他セッションでの落選者を取得（優先当選判定用、団体スコープ内）
            Set<Long> monthlyLosers = new HashSet<>(
                    practiceParticipantRepository.findMonthlyLoserPlayerIds(year, month, sessionId, orgId));

            // セッション内落選者を追跡
            Set<Long> sessionLosers = new HashSet<>();
            long seed = new Random().nextLong();
            Random random = new Random(seed);

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
                        inheritedOrder, currentWaitlistOrder, adminPrioritySet, true, random);

                prevWaitlistOrder = currentWaitlistOrder;
                prevMatchNumber = matchNumber;

                // 定員を戻す
                session.setCapacity(originalCapacity);
            }

            execution.setPriorityPlayerIds(resolvedPriorityPlayerIds);
            execution.setDetails(toJson(new ReLotteryDetails(
                    sessionId, promoted.size(), reLotteryTargets.size(), seed, resolvedPriorityPlayerIds.size())));

            log.info("Re-lottery completed for session {}: {} promoted kept, {} re-lotteried, {} priority players",
                    sessionId, promoted.size(), reLotteryTargets.size(), resolvedPriorityPlayerIds.size());

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

                if (p.getStatus().isActive()) {
                    winners.add(result);
                } else if (p.getStatus().isWaitlisted()) {
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
     * - ステータス変更（WON→CANCELLEDの場合は通常キャンセル経路と同じ三分岐に委譲）
     * - キャンセル待ち順番変更
     *
     * <p>WON→CANCELLED時は {@code cancelParticipationSuppressed} に委譲することで、
     * 通常キャンセル経路（{@code /api/lottery/cancel}）と完全に同じロジックで分岐する：
     * <ul>
     *   <li>当日12:00前 / 当日でない → 通常繰り上げ + 管理者バッチ通知 + プレイヤー向けオファー統合通知</li>
     *   <li>当日12:00以降 → {@code SameDayCancelContext} 経由で当日補充フローが afterCommit 登録される</li>
     * </ul>
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
        List<AdminWaitlistNotificationData> promotionDataList = new ArrayList<>();
        if (request.getStatusChanges() != null) {
            for (AdminEditParticipantsRequest.StatusChange change : request.getStatusChanges()) {
                PracticeParticipant p = practiceParticipantRepository.findById(change.getParticipantId())
                        .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", change.getParticipantId()));
                ParticipantStatus oldStatus = p.getStatus();

                // WON → CANCELLED は通常キャンセル経路に委譲し、当日12:00分岐・通常繰り上げを統一する。
                // 委譲側で setStatus(CANCELLED) / setDirty / save と昇格判定を行うため、ここでは何もしない。
                if (oldStatus == ParticipantStatus.WON && change.getNewStatus() == ParticipantStatus.CANCELLED) {
                    AdminWaitlistNotificationData notifData = waitlistPromotionService
                            .cancelParticipationSuppressed(change.getParticipantId(), null, null);
                    if (notifData != null) {
                        promotionDataList.add(notifData);
                    }
                    continue;
                }

                p.setStatus(change.getNewStatus());
                p.setDirty(true);
                if (change.getWaitlistNumber() != null) {
                    p.setWaitlistNumber(change.getWaitlistNumber());
                }
                practiceParticipantRepository.save(p);
            }
        }

        // 当日キャンセル分は dispatchSameDayCancelNotifications 内で afterCommit 登録され、
        // 戻り値には通常繰り上げ分（同期送信対象）のみが残る。
        List<AdminWaitlistNotificationData> normalNotifications =
                waitlistPromotionService.dispatchSameDayCancelNotifications(promotionDataList);

        // 管理者通知＋プレイヤー向けオファー統合通知をバッチ送信
        if (!normalNotifications.isEmpty()) {
            normalNotifications.stream()
                    .collect(Collectors.groupingBy(AdminWaitlistNotificationData::getSessionId))
                    .forEach((sid, dataList) -> {
                        PracticeSession session = practiceSessionRepository.findById(sid).orElse(null);
                        if (session != null) {
                            waitlistPromotionService.sendBatchedAdminWaitlistNotifications(dataList, session);

                            // プレイヤー向けオファー統合通知
                            dataList.stream()
                                    .filter(d -> d.getPromotedParticipant() != null)
                                    .collect(Collectors.groupingBy(d -> d.getPromotedParticipant().getPlayerId()))
                                    .forEach((offeredPlayerId, playerDataList) -> {
                                        List<PracticeParticipant> offeredParticipants = playerDataList.stream()
                                                .map(AdminWaitlistNotificationData::getPromotedParticipant)
                                                .collect(Collectors.toList());
                                        AdminWaitlistNotificationData first = playerDataList.get(0);
                                        lineNotificationService.sendConsolidatedWaitlistOfferNotification(
                                                offeredParticipants, session, first.getTriggerAction(),
                                                first.getTriggerPlayerId());
                                    });
                        }
                    });
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

    /**
     * 抽選結果を確定する。
     * confirmedAt/confirmedBy を設定し、伝助への一括書き戻しをトリガーする。
     */
    @Transactional
    public LotteryExecution confirmLottery(int year, int month, Long confirmedBy, Long organizationId) {
        LotteryExecution execution;
        if (organizationId != null) {
            execution = lotteryExecutionRepository
                    .findTopByTargetYearAndTargetMonthAndOrganizationIdAndStatusOrderByExecutedAtDesc(
                            year, month, organizationId, ExecutionStatus.SUCCESS)
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("%d年%d月の成功した抽選実行が見つかりません", year, month)));
        } else {
            execution = lotteryExecutionRepository
                    .findTopByTargetYearAndTargetMonthAndStatusOrderByExecutedAtDesc(
                            year, month, ExecutionStatus.SUCCESS)
                    .orElseThrow(() -> new IllegalStateException(
                            String.format("%d年%d月の成功した抽選実行が見つかりません", year, month)));
        }

        if (execution.getConfirmedAt() != null) {
            throw new IllegalStateException(
                    String.format("%d年%d月の抽選結果は既に確定済みです", year, month));
        }

        execution.setConfirmedAt(JstDateTimeUtil.now());
        execution.setConfirmedBy(confirmedBy);
        lotteryExecutionRepository.save(execution);

        log.info("Lottery confirmed for {}-{} by user {}", year, month, confirmedBy);

        // 伝助への一括書き戻し（失敗はログのみ・呼び出し元には伝搬しない）
        if (organizationId != null) {
            try {
                DensukeWriteResult result = densukeWriteService.writeAllForLotteryConfirmation(organizationId, year, month);
                if (!result.isSuccess()) {
                    log.warn("Densuke write-back returned failures after lottery confirmation (confirmLottery path): {}", result.getErrors());
                }
            } catch (Exception e) {
                log.error("Failed to write all to densuke after lottery confirmation: {}", e.getMessage(), e);
            }
        }

        return execution;
    }

    /**
     * 指定年月・団体の抽選が確定済みかどうかを返す
     */
    public boolean isLotteryConfirmed(int year, int month, Long organizationId) {
        return lotteryQueryService.isLotteryConfirmed(year, month, organizationId);
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
