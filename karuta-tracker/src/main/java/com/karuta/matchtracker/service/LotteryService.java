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
import com.karuta.matchtracker.entity.PlayerOrganization;
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
 * 抽選アルゴリズム（公平化・2ルール方式）:
 * 1. 対象セッション群から公平抽選トラッカー（{@link LotteryFairShareTracker}）を構築
 *    （直近30日の既存 WON をベースラインとしてロード）
 * 2. セッションを日付昇順・試合を番号昇順で処理
 * 3. 各試合について（定員超過時）:
 *    a. 管理者優先プールとその他プールに分割（各プール内は ID 昇順）
 *    b. 各プール内で「ルール1: そのセッションでまだ取れていない人（todayTaken 最小）に絞り、
 *       ルール2: 直近30日の取得数（recentTaken）が少ないほど有利な重み付き抽選」で座席を確定
 *    c. 落選者は同じ選抜手続きの続行で引かれた順にキャンセル待ち番号を付与
 *       （バケット順=管理者優先プール→その他プール）
 * 当選確定のたびに tracker に recordWin して以降の試合・セッションに反映する。
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

            // 公平抽選トラッカーを対象セッション群のベースライン（直近30日の既存WON）から構築
            LotteryFairShareTracker tracker = buildTracker(sessions, organizationId);
            List<SessionDetail> sessionDetails = new ArrayList<>();
            Random random = new Random(seed);
            Set<Long> adminPrioritySet = priorityPlayerIds != null ? new HashSet<>(priorityPlayerIds) : Set.of();

            for (PracticeSession session : sessions) {
                SessionDetail sessionDetail = processSession(session, tracker, execution.getId(), adminPrioritySet, true, random);
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
    public ConfirmLotteryResponse executeAndConfirmLottery(int year, int month, Long executedBy, Long organizationId, long seed, List<Long> priorityPlayerIds, String expectedPopulationSignature) {
        // 抽選実行 + 確定情報保存を1つのトランザクション内でコミットさせる。
        // 同一クラス内の self-invocation では @Transactional が効かないため、
        // TransactionTemplate を用いて明示的に外側トランザクションを構築する。
        LotteryExecution execution = transactionTemplate.execute(status -> {
            // B-2: 母集団シグネチャ照合を確定トランザクション内（PENDING 読取と原子的）で行う。
            // Controller 側の事前照合だけだと照合〜実際の PENDING 読取の間に母集団が変わりうるため、
            // executeLottery が PENDING を読む直前に再照合し、不一致なら 409（再プレビュー要）で確定を中止する。
            // 後方互換: シグネチャ未送信なら検証スキップ。
            if (expectedPopulationSignature != null && !expectedPopulationSignature.isBlank()) {
                String currentSignature = computePopulationSignature(year, month, organizationId);
                if (!currentSignature.equals(expectedPopulationSignature)) {
                    throw new com.karuta.matchtracker.exception.ConflictStateException(
                            "参加状況が変わったため再プレビューが必要です。最新の参加状況で抽選をやり直してください。");
                }
            }
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
        List<String> densukeDiffs = List.of();
        if (organizationId != null) {
            try {
                DensukeWriteResult result = densukeWriteService.writeAllForLotteryConfirmation(organizationId, year, month);
                if (!result.isSuccess()) {
                    log.warn("Densuke write-back returned failures after lottery confirmation: {}", result.getErrors());
                    densukeWriteSucceeded = false;
                    densukeWriteError = String.join("; ", result.getErrors());
                }
                // A-3: 確定書き戻し直前の伝助差分（○書き戻し予定 vs 伝助×）を管理者へ通知しレスポンスに含める。
                // 差分自体は確定をブロックしない（確定DBは維持）。
                densukeDiffs = result.getDensukeDiffs() != null ? result.getDensukeDiffs() : List.of();
                if (!densukeDiffs.isEmpty()) {
                    log.warn("A-3: {} pre-confirm densuke reversal-risk diffs for org {}: {}",
                            densukeDiffs.size(), organizationId, densukeDiffs);
                    lineNotificationService.sendPreConfirmDensukeDiffNotification(
                            organizationId, densukeDiffs, densukeWriteSucceeded);
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
                .densukeDiffs(densukeDiffs)
                .build();
    }

    /**
     * 対象セッション群のベースライン（直近30日の既存WON）から公平抽選トラッカーを構築する。
     *
     * <p>ロード範囲は {@code [最早セッション日-30, 最遅セッション日]}（半開区間 {@code [from, to)} で
     * {@code to = 最遅セッション日+1日}）。最遅セッション日を含めることで、当日すでに存在する WON
     * （管理者手動追加・繰り上げ承諾者など）が {@code todayTaken} に反映される。抽選対象は PENDING の
     * ため WON クエリには含まれず二重計上されない。organizationId が null の場合は全団体を対象にする。
     */
    private LotteryFairShareTracker buildTracker(List<PracticeSession> sessions, Long organizationId) {
        LotteryFairShareTracker tracker = new LotteryFairShareTracker();
        if (sessions.isEmpty()) {
            return tracker;
        }
        LocalDate earliest = sessions.stream()
                .map(PracticeSession::getSessionDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate latest = sessions.stream()
                .map(PracticeSession::getSessionDate).max(Comparator.naturalOrder()).orElse(null);
        if (earliest == null || latest == null) {
            return tracker;
        }
        LocalDate from = earliest.minusDays(LotteryFairShareTracker.RECENT_WINDOW_DAYS);
        LocalDate to = latest.plusDays(1); // 最遅セッション日を含める（半開 [from, to)）
        for (PracticeParticipantRepository.PlayerWonDate w :
                practiceParticipantRepository.findWonPlayerDates(organizationId, from, to)) {
            tracker.recordWin(w.getPlayerId(), w.getSessionDate());
        }
        return tracker;
    }

    /**
     * 1セッション（1日）の全試合を処理する。試合番号昇順で各試合を2ルール方式で選抜する。
     * 当選確定は tracker に反映され、以降の試合（todayTaken）・後続セッション（recentTaken）に効く。
     */
    private SessionDetail processSession(PracticeSession session, LotteryFairShareTracker tracker,
                                         Long lotteryId, Set<Long> adminPriorityPlayers,
                                         boolean saveResults, Random random) {
        // セッションに定員が未設定の場合、会場の定員にフォールバック
        if (session.getCapacity() == null && session.getVenueId() != null) {
            venueRepository.findById(session.getVenueId())
                    .ifPresent(venue -> session.setCapacity(venue.getCapacity()));
        }

        log.debug("Processing session: {} (date: {}, capacity: {})",
                session.getId(), session.getSessionDate(), session.getCapacity());

        // パーセンタイル設定は団体別に読む（プレビュー時と確定時で同一団体の同一値が読まれる）
        int capPercentile = systemSettingService.getLotteryWeightCapPercentile(session.getOrganizationId());

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
        for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
            MatchDetail detail = processMatch(session, entry.getKey(), entry.getValue(),
                    lotteryId, tracker, capPercentile, adminPriorityPlayers, saveResults, random);
            matchDetails.add(detail);
        }

        return new SessionDetail(session.getId(), session.getSessionDate(), matchDetails);
    }

    /**
     * 1試合の抽選を処理する（2ルール方式）。
     *
     * <p>定員超過時は、まず管理者優先プールとその他プールに分割し（各プール内は ID 昇順）、
     * 各プール内で「ルール1: そのセッションでまだ取れていない人（{@code todayTaken} 最小）に絞り、
     * ルール2: 直近30日の取得数（{@code recentTaken}）が少ないほど有利な重み付き抽選」を続行して
     * 座席を確定する。落選者は同じ選抜手続きの続行で引かれた順にキャンセル待ち番号を付与する
     * （バケット順=管理者優先プール→その他プール）。当選確定は {@code tracker} に反映する。
     *
     * <p>package-private にすることでユニットテストから直接呼び出し可能にしている。
     */
    MatchDetail processMatch(PracticeSession session, int matchNumber,
                                List<PracticeParticipant> applicants,
                                Long lotteryId,
                                LotteryFairShareTracker tracker,
                                int capPercentile,
                                Set<Long> adminPriorityPlayers,
                                boolean saveResults, Random random) {

        LocalDate sessionDate = session.getSessionDate();
        Integer capacity = session.getCapacity();
        int totalApplicants = applicants.size();

        // A-2 防御的措置: 抽選前に既に埋まっている枠（WON/OFFERED）を定員から差し引く。
        // 管理者手動追加・試合記録に伴う自動参加登録・締切後即WON等で抽選前に WON/OFFERED が
        // 存在しても、合計当選が定員を超えないようにする。差し引き後の残枠で選抜を実施。
        // （再抽選経路も本差し引きに一本化。繰り上がり承諾者=WON が残枠から控除される）
        if (capacity != null) {
            long alreadyFilled =
                    practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                            session.getId(), matchNumber, ParticipantStatus.WON)
                    + practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                            session.getId(), matchNumber, ParticipantStatus.OFFERED);
            capacity = (int) Math.max(0, capacity - alreadyFilled);
        }

        // 定員未設定 or 定員以下 → 全員当選
        if (capacity == null || totalApplicants <= capacity) {
            for (PracticeParticipant p : applicants) {
                p.setStatus(ParticipantStatus.WON);
                p.setDirty(true);
                p.setLotteryId(lotteryId);
                tracker.recordWin(p.getPlayerId(), sessionDate);
            }
            if (saveResults) {
                practiceParticipantRepository.saveAll(applicants);
            }

            log.debug("Match {}: all {} applicants win (capacity: {})",
                    matchNumber, totalApplicants, capacity);
            return new MatchDetail(matchNumber, totalApplicants, totalApplicants, 0);
        }

        // 定員超過 → 2ルールで選抜
        log.debug("Match {}: {} applicants for {} capacity - lottery required",
                matchNumber, totalApplicants, capacity);

        // 候補を ID 昇順に固定（シード再現性）し、管理者優先プールとその他プールに分割
        List<PracticeParticipant> sorted = new ArrayList<>(applicants);
        sorted.sort(Comparator.comparingLong(PracticeParticipant::getId));

        List<PracticeParticipant> adminPool = new ArrayList<>();
        List<PracticeParticipant> restPool = new ArrayList<>();
        for (PracticeParticipant p : sorted) {
            if (adminPriorityPlayers.contains(p.getPlayerId())) {
                adminPool.add(p);
            } else {
                restPool.add(p);
            }
        }

        List<PracticeParticipant> winners = new ArrayList<>();
        List<PracticeParticipant> orderedLosers = new ArrayList<>();

        // 管理者優先プール → その他プールの順に定員まで座席を埋める。
        // 各プールで座席数を超えた分の引きは、そのプールの落選者として引かれた順（=キャンセル待ち順）で確定。
        int adminSeats = Math.min(capacity, adminPool.size());
        selectFromPool(adminPool, adminSeats, sessionDate, capPercentile, tracker, random,
                winners, orderedLosers);
        int restSeats = capacity - winners.size();
        selectFromPool(restPool, restSeats, sessionDate, capPercentile, tracker, random,
                winners, orderedLosers);

        // ステータス更新 - 当選者
        for (PracticeParticipant p : winners) {
            p.setStatus(ParticipantStatus.WON);
            p.setDirty(true);
            p.setLotteryId(lotteryId);
        }
        // 落選者に引かれた順でキャンセル待ち番号を付与
        for (int i = 0; i < orderedLosers.size(); i++) {
            PracticeParticipant p = orderedLosers.get(i);
            p.setStatus(ParticipantStatus.WAITLISTED);
            p.setDirty(true);
            p.setWaitlistNumber(i + 1);
            p.setLotteryId(lotteryId);
        }

        if (saveResults) {
            List<PracticeParticipant> all = new ArrayList<>(winners.size() + orderedLosers.size());
            all.addAll(winners);
            all.addAll(orderedLosers);
            practiceParticipantRepository.saveAll(all);
        }

        log.info("Match {}: {} winners, {} waitlisted (from {} applicants, capacity {})",
                matchNumber, winners.size(), orderedLosers.size(), totalApplicants, capacity);

        return new MatchDetail(matchNumber, totalApplicants, winners.size(), orderedLosers.size());
    }

    /**
     * プール内で「ルール1（todayTaken 最小に絞る）→ ルール2（重み付き抽選）」を繰り返す。
     * 先頭 {@code seats} 人を当選（tracker に recordWin）、それ以降に引かれた者を落選として、
     * 引かれた順に {@code losersOut} へ積む（=キャンセル待ち順）。tracker への当選記録は当選者のみ。
     */
    private void selectFromPool(List<PracticeParticipant> pool, int seats, LocalDate sessionDate,
                                int capPercentile, LotteryFairShareTracker tracker, Random random,
                                List<PracticeParticipant> winnersOut,
                                List<PracticeParticipant> losersOut) {
        List<PracticeParticipant> working = new ArrayList<>(pool); // ID 昇順を維持
        int drawn = 0;
        while (!working.isEmpty()) {
            PracticeParticipant chosen = drawOne(working, sessionDate, capPercentile, tracker, random);
            working.remove(chosen);
            if (drawn < seats) {
                winnersOut.add(chosen);
                tracker.recordWin(chosen.getPlayerId(), sessionDate);
            } else {
                losersOut.add(chosen);
            }
            drawn++;
        }
    }

    /**
     * ルール1で todayTaken 最小の候補に絞り、ルール2で1人選ぶ（候補1人なら確定、複数なら重み付き抽選）。
     * {@code pool} は ID 昇順前提。
     */
    private PracticeParticipant drawOne(List<PracticeParticipant> pool, LocalDate sessionDate,
                                        int capPercentile, LotteryFairShareTracker tracker,
                                        Random random) {
        int minToday = Integer.MAX_VALUE;
        for (PracticeParticipant p : pool) {
            minToday = Math.min(minToday, tracker.todayTaken(p.getPlayerId(), sessionDate));
        }
        List<PracticeParticipant> candidates = new ArrayList<>();
        for (PracticeParticipant p : pool) {
            if (tracker.todayTaken(p.getPlayerId(), sessionDate) == minToday) {
                candidates.add(p);
            }
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        List<Long> candidateIds = new ArrayList<>(candidates.size());
        for (PracticeParticipant p : candidates) {
            candidateIds.add(p.getPlayerId());
        }
        Long chosenId = tracker.pickWeighted(candidateIds, sessionDate, capPercentile, random);
        for (PracticeParticipant p : candidates) {
            if (p.getPlayerId().equals(chosenId)) {
                return p;
            }
        }
        return candidates.get(0); // フォールバック（理論上到達しない）
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
    public record LotteryPreviewResult(List<LotteryResultDto> results, long seed, String populationSignature) {}

    /**
     * B-2: 母集団シグネチャを算出する。対象月・団体のセッション群に属する PENDING 参加者の
     * (participant id) 集合を昇順に並べて SHA-256 でハッシュ化する。プレビュー時と確定時で
     * 同一なら「プレビューで見た母集団と確定時の母集団が一致」とみなせる。
     * 5分同期での新規○取り込み・キャンセル等で母集団が変わるとシグネチャが変化する。
     */
    @Transactional(readOnly = true)
    public String computePopulationSignature(int year, int month, Long organizationId) {
        List<PracticeSession> sessions = (organizationId != null)
                ? practiceSessionRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                : practiceSessionRepository.findByYearAndMonth(year, month);
        List<Long> sessionIds = sessions.stream().map(PracticeSession::getId).sorted().collect(Collectors.toList());

        List<Long> pendingIds = new ArrayList<>();
        for (Long sid : sessionIds) {
            practiceParticipantRepository.findBySessionIdAndStatus(sid, ParticipantStatus.PENDING)
                    .forEach(p -> pendingIds.add(p.getId()));
        }
        pendingIds.sort(Comparator.naturalOrder());

        String raw = "s=" + sessionIds.stream().map(String::valueOf).collect(Collectors.joining(","))
                + "|p=" + pendingIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-256 は必ず存在する。万一なければ raw の hashCode にフォールバック。
            return "h" + Integer.toHexString(raw.hashCode());
        }
    }

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

        String populationSignature = computePopulationSignature(year, month, organizationId);

        if (sessions.isEmpty()) {
            return new LotteryPreviewResult(List.of(), seed, populationSignature);
        }

        // 公平抽選トラッカーを対象セッション群のベースラインから構築（確定経路と同一手順で AC-R3 を担保）
        LotteryFairShareTracker tracker = buildTracker(sessions, organizationId);
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
            processSession(session, tracker, null, adminPrioritySet, false, random);

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
        return new LotteryPreviewResult(results, seed, populationSignature);
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

        // 1) 団体所属チェック（対象団体に所属していない選手は403）
        // 複数団体に所属している選手の場合、いずれか1つに対象団体が含まれていれば許可する。
        if (organizationId != null) {
            Set<Long> playersBelongingToOrg = playerOrganizationRepository.findByPlayerIdIn(ids).stream()
                    .filter(po -> organizationId.equals(po.getOrganizationId()))
                    .map(PlayerOrganization::getPlayerId)
                    .collect(Collectors.toSet());
            List<Long> foreignIds = ids.stream()
                    .filter(id -> !playersBelongingToOrg.contains(id))
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

            // このセッションのベースライン（直近30日の既存WON）から公平抽選トラッカーを構築。
            // 直前の saveAll でリセット対象は PENDING になっており（auto-flush で反映）、WON クエリには
            // 含まれない。繰り上げ承諾者（当日 WON のまま維持）は当日 WON として todayTaken に算入される。
            LotteryFairShareTracker tracker = buildTracker(List.of(session), orgId);
            int capPercentile = systemSettingService.getLotteryWeightCapPercentile(orgId);

            long seed = new Random().nextLong();
            Random random = new Random(seed);

            // 試合番号でグループ化し再抽選
            Map<Integer, List<PracticeParticipant>> byMatch = reLotteryTargets.stream()
                    .filter(p -> p.getMatchNumber() != null && p.getStatus() == ParticipantStatus.PENDING)
                    .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber,
                            TreeMap::new, Collectors.toList()));

            // 繰り上がり承諾者（WON のまま維持）分の枠差し引きは processMatch 内の
            // WON/OFFERED 控除（A-2）に一本化する。ここでの手動 capacity 調整は不要
            // （手動調整と併用すると二重に差し引かれるため撤廃）。

            for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
                List<PracticeParticipant> matchApplicants = new ArrayList<>(entry.getValue());
                matchApplicants.sort(Comparator.comparingLong(PracticeParticipant::getId));
                processMatch(session, entry.getKey(), matchApplicants,
                        execution.getId(), tracker, capPercentile, adminPrioritySet, true, random);
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
                // 権限境界: 対象参加者が request の sessionId/matchNumber に属することを検証する
                // （Controller のスコープ検証は request.sessionId のみのため、別セッション/別試合/別団体の
                //  participantId を混ぜられると他レコードを更新できてしまう IDOR を防ぐ）
                PracticeParticipant p = findScopedParticipant(change.getParticipantId(), request);
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

                boolean promoteToWon = change.getNewStatus() == ParticipantStatus.WON
                        && (oldStatus == ParticipantStatus.WAITLISTED || oldStatus == ParticipantStatus.OFFERED);
                // WAITLISTED→WON は当選総数が増えるため、繰り上げ前に空き枠を確認して定員超過を防ぐ
                // （OFFERED→WON は既に定員に算入済みで総数が変わらないためチェック不要）。
                // 定員拡張が必要な場合は会場拡張フロー（隣室予約）で capacity を増やしてから繰り上げる。
                if (promoteToWon && oldStatus == ParticipantStatus.WAITLISTED) {
                    ensureVacancyForManualPromotion(request);
                }

                p.setStatus(change.getNewStatus());
                p.setDirty(true);

                if (promoteToWon) {
                    // キャンセル待ち/オファー中 → 当選 への管理者手動繰り上げ。
                    // 当該者の待ち番号を消し、オファー関連フィールドもクリアして期限切れ処理の対象から外す。
                    p.setWaitlistNumber(null);
                    p.setOfferedAt(null);
                    p.setOfferDeadline(null);
                    practiceParticipantRepository.save(p);
                    // 残存キューを 1..N で再採番（WAITLISTED だけでなく OFFERED も含めるため、
                    // decrement 方式ではなく renumberRemainingWaitlist を使い欠番/重複を防ぐ）。
                    waitlistPromotionService.renumberRemainingWaitlist(p.getSessionId(), p.getMatchNumber());
                } else {
                    if (change.getWaitlistNumber() != null) {
                        p.setWaitlistNumber(change.getWaitlistNumber());
                    }
                    practiceParticipantRepository.save(p);
                }
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
                PracticeParticipant p = findScopedParticipant(reorder.getParticipantId(), request);
                p.setWaitlistNumber(reorder.getNewWaitlistNumber());
                practiceParticipantRepository.save(p);
            }
        }
    }

    /**
     * 参加者を取得し、{@code request} の sessionId / matchNumber に属することを検証する。
     * Controller のスコープ検証は {@code request.sessionId} のみを対象とするため、
     * 別セッション・別試合（＝別団体を含む）の participantId を送られて他レコードを
     * 更新される IDOR を防ぐ。属さない場合は 400（不正リクエスト）として拒否する。
     */
    private PracticeParticipant findScopedParticipant(Long participantId, AdminEditParticipantsRequest request) {
        PracticeParticipant p = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));
        if (!java.util.Objects.equals(p.getSessionId(), request.getSessionId())
                || !java.util.Objects.equals(p.getMatchNumber(), request.getMatchNumber())) {
            throw new IllegalArgumentException(
                    "参加者(id=" + participantId + ")は指定された練習日・試合に属していません");
        }
        return p;
    }

    /**
     * 管理者手動繰り上げ（WAITLISTED→WON）時の定員ガード。
     * 既に {@code WON + OFFERED >= capacity}（他経路と揃えた空き判定）なら定員超過となるため拒否する。
     * capacity 未設定（定員なし）の場合は制限しない。
     */
    private void ensureVacancyForManualPromotion(AdminEditParticipantsRequest request) {
        PracticeSession session = practiceSessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", request.getSessionId()));
        Integer capacity = session.getCapacity();
        if (capacity == null) {
            return;
        }
        long won = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                request.getSessionId(), request.getMatchNumber(), ParticipantStatus.WON);
        long offered = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                request.getSessionId(), request.getMatchNumber(), ParticipantStatus.OFFERED);
        if (won + offered >= capacity) {
            throw new IllegalArgumentException(
                    "定員に空きがないため繰り上げできません（定員拡張が必要です）");
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
                // A-3: 確定書き戻し直前の伝助差分を管理者へ通知（この経路の戻り値には差分を載せないが通知は行う）
                List<String> densukeDiffs = result.getDensukeDiffs() != null ? result.getDensukeDiffs() : List.of();
                if (!densukeDiffs.isEmpty()) {
                    log.warn("A-3: {} pre-confirm densuke reversal-risk diffs for org {} (confirmLottery path): {}",
                            densukeDiffs.size(), organizationId, densukeDiffs);
                    lineNotificationService.sendPreConfirmDensukeDiffNotification(
                            organizationId, densukeDiffs, result.isSuccess());
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

    /**
     * 抽選成功・未確定（admin の確認待ち窓）かどうかを返す
     */
    public boolean hasUnconfirmedExecution(int year, int month, Long organizationId) {
        return lotteryQueryService.hasUnconfirmedExecution(year, month, organizationId);
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
