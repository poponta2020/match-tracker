package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.karuta.matchtracker.dto.AdminWaitlistNotificationData;
import com.karuta.matchtracker.dto.ExpireOfferResult;
import com.karuta.matchtracker.dto.SameDayCancelContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * キャンセル・繰り上げサービス
 *
 * 当選者のキャンセル→キャンセル待ち1番への通知→承諾/辞退の処理を行う。
 * 当日キャンセルの場合は繰り上げフローを開始しない。
 */
@Service
@Slf4j
public class WaitlistPromotionService {

    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final PlayerRepository playerRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;
    private final NotificationService notificationService;
    private final LineNotificationService lineNotificationService;
    private final DensukeSyncService densukeSyncService;

    public WaitlistPromotionService(
            PracticeParticipantRepository practiceParticipantRepository,
            PracticeSessionRepository practiceSessionRepository,
            PlayerRepository playerRepository,
            LotteryDeadlineHelper lotteryDeadlineHelper,
            NotificationService notificationService,
            LineNotificationService lineNotificationService,
            @Lazy DensukeSyncService densukeSyncService) {
        this.practiceParticipantRepository = practiceParticipantRepository;
        this.practiceSessionRepository = practiceSessionRepository;
        this.playerRepository = playerRepository;
        this.lotteryDeadlineHelper = lotteryDeadlineHelper;
        this.notificationService = notificationService;
        this.lineNotificationService = lineNotificationService;
        this.densukeSyncService = densukeSyncService;
    }

    /**
     * WON → WAITLISTED（最後尾）に降格し、空いたWON枠の繰り上げフローを発動する。
     * 伝助で○→△に変更された場合（3-B2）に使用。
     *
     * @param participantId 参加者レコードID
     */
    @Transactional
    public void demoteToWaitlist(Long participantId) {
        AdminWaitlistNotificationData notifData = demoteToWaitlistInternal(participantId);

        PracticeParticipant participant = practiceParticipantRepository.findById(participantId).orElse(null);
        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        sendBatchedAdminWaitlistNotifications(List.of(notifData), session);
        densukeSyncService.triggerWriteAsync();
    }

    /**
     * WON → WAITLISTED（最後尾）に降格する（通知抑制版）。
     * 呼び出し元で複数件分をまとめて sendBatchedAdminWaitlistNotifications に渡す用途。
     *
     * @param participantId 参加者レコードID
     * @return 通知データ
     */
    @Transactional
    public AdminWaitlistNotificationData demoteToWaitlistSuppressed(Long participantId) {
        AdminWaitlistNotificationData notifData = demoteToWaitlistInternal(participantId);
        densukeSyncService.triggerWriteAsync();
        return notifData;
    }

    /**
     * 降格処理の内部実装。通知送信は行わず、通知データを返す。
     */
    private AdminWaitlistNotificationData demoteToWaitlistInternal(Long participantId) {
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.WON) {
            throw new IllegalStateException("WON状態のみ降格できます（現在: " + participant.getStatus() + "）");
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        // 最後尾のキャンセル待ち番号を取得（OFFEREDも含めて重複を防ぐ）
        int maxNumber = practiceParticipantRepository
                .findMaxWaitlistNumberIncludingOffered(participant.getSessionId(), participant.getMatchNumber())
                .orElse(0);

        // WON → WAITLISTED（最後尾）
        participant.setStatus(ParticipantStatus.WAITLISTED);
        participant.setDirty(true);
        participant.setWaitlistNumber(maxNumber + 1);
        practiceParticipantRepository.save(participant);

        log.info("Demoted player {} from WON to WAITLISTED #{} for session {} match {}",
                participant.getPlayerId(), maxNumber + 1, participant.getSessionId(), participant.getMatchNumber());

        // WON枠が空いたので繰り上げフローを発動（降格した本人は除外）
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate(), participant.getPlayerId());

        return AdminWaitlistNotificationData.builder()
                .triggerAction("降格")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.orElse(null))
                .build();
    }

    /**
     * 当選者が参加をキャンセルする（理由なし・後方互換）
     *
     * @param participantId 参加者レコードID
     * @return キャンセル後のステータス
     */
    @Transactional
    public ParticipantStatus cancelParticipation(Long participantId) {
        return cancelParticipation(participantId, null, null);
    }

    /**
     * 当選者が参加をキャンセルする（理由付き）
     *
     * @param participantId    参加者レコードID
     * @param cancelReason     キャンセル理由コード
     * @param cancelReasonDetail キャンセル理由詳細（その他の場合）
     * @return キャンセル後のステータス
     */
    @Transactional
    public ParticipantStatus cancelParticipation(Long participantId, String cancelReason, String cancelReasonDetail) {
        AdminWaitlistNotificationData notifData = cancelParticipationInternal(participantId, cancelReason, cancelReasonDetail);

        if (notifData != null) {
            if (notifData.getSameDayCancelContext() != null) {
                // 当日12:00以降キャンセル: afterCommit で統合通知フローを実行（1試合分）
                SameDayCancelContext ctx = notifData.getSameDayCancelContext();
                registerSameDayCancelAfterCommit(ctx.getSession(), ctx.getPlayerId(),
                        ctx.getPlayerName(), List.of(ctx.getMatchNumber()));
            } else {
                PracticeSession session = practiceSessionRepository.findById(notifData.getSessionId())
                        .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", notifData.getSessionId()));
                sendBatchedAdminWaitlistNotifications(List.of(notifData), session);
            }
        }

        densukeSyncService.triggerWriteAsync();
        return ParticipantStatus.CANCELLED;
    }

    /**
     * 当選者が参加をキャンセルする（通知抑制オプション付き）。
     * suppressNotification=true の場合、管理者通知を送信せず通知データを返す。
     * 呼び出し元で複数件分をまとめて sendBatchedAdminWaitlistNotifications に渡す用途。
     *
     * @param participantId      参加者レコードID
     * @param cancelReason       キャンセル理由コード
     * @param cancelReasonDetail キャンセル理由詳細
     * @param suppressNotification true=通知を送信しない
     * @return 通知データ（通知不要の場合はnull）
     */
    @Transactional
    public AdminWaitlistNotificationData cancelParticipationSuppressed(Long participantId,
                                                                        String cancelReason,
                                                                        String cancelReasonDetail) {
        AdminWaitlistNotificationData notifData = cancelParticipationInternal(participantId, cancelReason, cancelReasonDetail);
        densukeSyncService.triggerWriteAsync();
        return notifData;
    }

    /**
     * キャンセル処理の内部実装。通知送信は行わず、通知データを返す。
     */
    private AdminWaitlistNotificationData cancelParticipationInternal(Long participantId,
                                                                       String cancelReason,
                                                                       String cancelReasonDetail) {
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.WON) {
            throw new IllegalStateException("当選者のみキャンセルできます（現在のステータス: " + participant.getStatus() + "）");
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        // ステータスをCANCELLEDに変更
        participant.setStatus(ParticipantStatus.CANCELLED);
        participant.setDirty(true);
        participant.setCancelReason(cancelReason);
        participant.setCancelReasonDetail(cancelReasonDetail);
        participant.setCancelledAt(JstDateTimeUtil.now());
        practiceParticipantRepository.save(participant);

        log.info("Player {} cancelled participation in session {} match {} (reason: {})",
                participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber(), cancelReason);

        // 当日12:00以降のキャンセル → 新フロー（全体募集＋先着ボタン方式）
        if (lotteryDeadlineHelper.isAfterSameDayNoon(session.getSessionDate())) {
            log.info("Same-day after-noon cancel: marking for consolidated recruitment notification on session {} match {}",
                    session.getId(), participant.getMatchNumber());

            // 同一セッション×同一プレイヤーで複数試合キャンセルされた場合に
            // 呼び出し元で集約できるよう、通知に必要な情報を SameDayCancelContext として返す。
            // 通知送信は呼び出し元側（または cancelParticipation 単体版）が afterCommit で行う。
            Player cancelledPlayer = playerRepository.findById(participant.getPlayerId()).orElse(null);
            String playerName = cancelledPlayer != null ? cancelledPlayer.getName() : "不明";
            SameDayCancelContext ctx = SameDayCancelContext.builder()
                    .session(session)
                    .playerId(participant.getPlayerId())
                    .playerName(playerName)
                    .matchNumber(participant.getMatchNumber())
                    .build();
            return AdminWaitlistNotificationData.builder()
                    .triggerAction("キャンセル（当日補充）")
                    .triggerPlayerId(participant.getPlayerId())
                    .sessionId(participant.getSessionId())
                    .matchNumber(participant.getMatchNumber())
                    .promotedParticipant(null)
                    .sameDayCancelContext(ctx)
                    .build();
        }

        // 当日12:00より前のキャンセル → 従来通りwaitlistNumberに基づく繰り上げフロー
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

        // 定員未達（WAITLISTEDなし）の場合は通知不要
        if (promoted.isEmpty()) {
            return null;
        }

        return AdminWaitlistNotificationData.builder()
                .triggerAction("キャンセル")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.get())
                .build();
    }

    /**
     * 空き募集ボタンへの応答を処理する。
     * 先着1名のみWONに変更。2人目以降はエラー。練習開始時間を過ぎていたら無効。
     */
    @Transactional
    public synchronized void handleSameDayJoin(Long sessionId, int matchNumber, Long playerId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalStateException("セッションが見つかりません"));

        // セッション日付チェック（当日以外は参加不可）
        if (!session.getSessionDate().equals(JstDateTimeUtil.today())) {
            throw new IllegalStateException("当日以外のセッションには参加できません。");
        }

        // 練習開始時間チェック
        if (session.getStartTime() != null) {
            LocalDateTime practiceStart = session.getSessionDate().atTime(session.getStartTime());
            if (JstDateTimeUtil.now().isAfter(practiceStart)) {
                throw new IllegalStateException("練習開始時間を過ぎているため、参加登録できません。");
            }
        }

        // この試合で既にWONかどうかチェック
        List<PracticeParticipant> existingWon = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber);
        if (existingWon.stream().anyMatch(p -> p.getStatus() == ParticipantStatus.WON)) {
            throw new IllegalStateException("既にこの試合に参加登録済みです。");
        }

        // 空き枠チェック
        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        List<PracticeParticipant> currentWon = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatus(sessionId, matchNumber, ParticipantStatus.WON);
        if (currentWon.size() >= capacity) {
            throw new IllegalStateException("定員に達してしまいました...");
        }

        // 既存レコードがあればステータス更新、なければ新規作成
        Optional<PracticeParticipant> existing = existingWon.stream()
                .filter(p -> p.getStatus() != ParticipantStatus.WON)
                .findFirst();

        PracticeParticipant participant;
        if (existing.isPresent()) {
            participant = existing.get();
            participant.setStatus(ParticipantStatus.WON);
            participant.setDirty(true);
        } else {
            participant = PracticeParticipant.builder()
                    .sessionId(sessionId)
                    .playerId(playerId)
                    .matchNumber(matchNumber)
                    .status(ParticipantStatus.WON)
                    .dirty(true)
                    .build();
        }
        practiceParticipantRepository.save(participant);

        Player joinedPlayer = playerRepository.findById(playerId).orElse(null);
        String playerName = joinedPlayer != null ? joinedPlayer.getName() : "不明";

        log.info("Same-day join: player {} ({}) joined session {} match {}",
                playerId, playerName, sessionId, matchNumber);

        // 参加通知 + 枠状況通知
        lineNotificationService.sendSameDayJoinNotification(session, matchNumber, playerName, playerId);
        lineNotificationService.sendSameDayVacancyUpdateNotification(session, matchNumber, playerName, playerId);

        // 管理者向け空き枠通知
        int adminCapacity = session.getCapacity() != null ? session.getCapacity() : 0;
        List<PracticeParticipant> currentWonAfterJoin = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatus(sessionId, matchNumber, ParticipantStatus.WON);
        int adminVacancies = Math.max(0, adminCapacity - currentWonAfterJoin.size());
        lineNotificationService.sendConsolidatedAdminVacancyNotification(session,
                Map.of(matchNumber, adminVacancies));

        densukeSyncService.triggerWriteAsync();
    }

    /**
     * 当日補充で全試合に一括参加する。
     * 空き枠があり、まだWONでない試合に一括で参加登録する。
     *
     * @param sessionId    セッションID
     * @param playerId     プレイヤーID
     * @param matchNumbers 対象試合番号リスト（通知時点の空き試合）。nullの場合は全試合を対象とする。
     * @return 参加登録できた試合数
     */
    @Transactional
    public synchronized int handleSameDayJoinAll(Long sessionId, Long playerId, List<Integer> matchNumbers) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        // セッション日付チェック（当日以外は参加不可）
        if (!session.getSessionDate().equals(JstDateTimeUtil.today())) {
            throw new IllegalStateException("当日以外のセッションには参加できません。");
        }

        // 練習開始時間チェック（handleSameDayJoinと同じ判定）
        if (session.getStartTime() != null) {
            LocalDateTime practiceStart = session.getSessionDate().atTime(session.getStartTime());
            if (JstDateTimeUtil.now().isAfter(practiceStart)) {
                throw new IllegalStateException("練習開始時間を過ぎているため、参加登録できません。");
            }
        }

        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        int totalMatches = session.getTotalMatches() != null ? session.getTotalMatches() : 1;

        // 対象試合番号リストが指定されていない場合は全試合を対象とする
        List<Integer> targetMatches;
        if (matchNumbers == null || matchNumbers.isEmpty()) {
            targetMatches = new java.util.ArrayList<>();
            for (int i = 1; i <= totalMatches; i++) {
                targetMatches.add(i);
            }
        } else {
            // 重複除去 + 範囲検証（1 <= n <= totalMatches）
            targetMatches = matchNumbers.stream()
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            for (int n : targetMatches) {
                if (n < 1 || n > totalMatches) {
                    throw new IllegalStateException(
                            "不正な試合番号が含まれています: " + n + "（有効範囲: 1〜" + totalMatches + "）");
                }
            }
        }

        Player joinedPlayer = playerRepository.findById(playerId).orElse(null);
        String playerName = joinedPlayer != null ? joinedPlayer.getName() : "不明";

        int joinedCount = 0;
        List<Integer> joinedMatches = new java.util.ArrayList<>();
        Map<Integer, Integer> vacanciesByMatch = new java.util.LinkedHashMap<>();

        for (int matchNumber : targetMatches) {
            // 既にWONかどうかチェック
            List<PracticeParticipant> existingRecords = practiceParticipantRepository
                    .findBySessionIdAndPlayerIdAndMatchNumber(sessionId, playerId, matchNumber);
            if (existingRecords.stream().anyMatch(p -> p.getStatus() == ParticipantStatus.WON)) {
                continue;
            }

            // 空き枠チェック
            List<PracticeParticipant> currentWon = practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatus(sessionId, matchNumber, ParticipantStatus.WON);
            if (currentWon.size() >= capacity) {
                continue;
            }

            // 既存レコードがあればステータス更新、なければ新規作成
            Optional<PracticeParticipant> existing = existingRecords.stream()
                    .filter(p -> p.getStatus() != ParticipantStatus.WON)
                    .findFirst();

            PracticeParticipant participant;
            if (existing.isPresent()) {
                participant = existing.get();
                participant.setStatus(ParticipantStatus.WON);
                participant.setDirty(true);
            } else {
                participant = PracticeParticipant.builder()
                        .sessionId(sessionId)
                        .playerId(playerId)
                        .matchNumber(matchNumber)
                        .status(ParticipantStatus.WON)
                        .dirty(true)
                        .build();
            }
            practiceParticipantRepository.save(participant);

            joinedMatches.add(matchNumber);

            // 参加登録後の空き枠数を計算（save後なので+1された状態）
            int currentWonCount = currentWon.size() + 1; // 今登録した分を加算
            int vacancies = Math.max(0, capacity - currentWonCount);
            vacanciesByMatch.put(matchNumber, vacancies);

            joinedCount++;
            log.info("Same-day join all: player {} ({}) joined session {} match {}",
                    playerId, playerName, sessionId, matchNumber);
        }

        if (joinedCount > 0) {
            // 参加通知をセッション単位でまとめて送信
            lineNotificationService.sendConsolidatedSameDayJoinNotification(session, joinedMatches, playerName, playerId);

            // 空き枠通知をセッション単位でまとめて送信
            if (!vacanciesByMatch.isEmpty()) {
                lineNotificationService.sendConsolidatedSameDayVacancyNotification(session, vacanciesByMatch, playerId);
                lineNotificationService.sendConsolidatedAdminVacancyNotification(session, vacanciesByMatch);
            }

            densukeSyncService.triggerWriteAsync();
        }

        return joinedCount;
    }

    /**
     * 当日12:00以降のキャンセル時に、キャンセル発生通知＋空き募集通知＋管理者通知を
     * セッション×プレイヤー単位でまとめて送信する。
     *
     * @param session       対象セッション
     * @param playerId      キャンセルしたプレイヤーID
     * @param playerName    キャンセルしたプレイヤー名
     * @param matchNumbers  キャンセルした試合番号リスト（同一セッション×同一プレイヤー）
     */
    public void handleSameDayCancelAndRecruitBatch(PracticeSession session, Long playerId,
                                                    String playerName, List<Integer> matchNumbers) {
        if (matchNumbers == null || matchNumbers.isEmpty()) return;

        List<Integer> sorted = matchNumbers.stream().distinct().sorted().toList();

        // 1. 当日キャンセル発生通知（セッション統合）
        lineNotificationService.sendConsolidatedSameDayCancelNotification(
                session, sorted, playerName, playerId);

        // 2. 空き募集通知（セッション統合）
        // 同日内で別プレイヤーが連続してキャンセルするケースでも 2 件目以降が重複排除でスキップされないよう、
        // dedupe キーを「sessionId : cancelledPlayerId : sortedMatchNumbers」でイベント粒度に一意化する。
        Map<Integer, Integer> vacanciesByMatch = new LinkedHashMap<>();
        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;
        for (int matchNumber : sorted) {
            List<PracticeParticipant> currentWon = practiceParticipantRepository
                    .findBySessionIdAndMatchNumberAndStatus(session.getId(), matchNumber, ParticipantStatus.WON);
            int vacancies = Math.max(0, capacity - currentWon.size());
            if (vacancies > 0) {
                vacanciesByMatch.put(matchNumber, vacancies);
            }
        }
        if (!vacanciesByMatch.isEmpty()) {
            String matchNumbersJoined = sorted.stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
            String dedupeKey = session.getId() + ":" + playerId + ":" + matchNumbersJoined;
            lineNotificationService.sendConsolidatedSameDayVacancyNotification(
                    session, vacanciesByMatch, playerId, dedupeKey);
        }

        // 3. 管理者通知（セッション単位のバッチ送信）
        List<AdminWaitlistNotificationData> notifDataList = new ArrayList<>();
        for (int matchNumber : sorted) {
            notifDataList.add(AdminWaitlistNotificationData.builder()
                    .triggerAction("キャンセル（当日補充）")
                    .triggerPlayerId(playerId)
                    .sessionId(session.getId())
                    .matchNumber(matchNumber)
                    .promotedParticipant(null)
                    .build());
        }
        sendBatchedAdminWaitlistNotifications(notifDataList, session);

        densukeSyncService.triggerWriteAsync();
    }

    /**
     * 当日キャンセル通知フローを afterCommit で登録する（トランザクション未起動時は即時実行）。
     * {@link #cancelParticipation} 単体呼び出し時、または呼び出し元が集約後にまとめて
     * 通知を送信したい場合に利用する。
     */
    public void registerSameDayCancelAfterCommit(PracticeSession session, Long playerId,
                                                 String playerName, List<Integer> matchNumbers) {
        if (matchNumbers == null || matchNumbers.isEmpty()) return;
        final PracticeSession finalSession = session;
        final Long finalPlayerId = playerId;
        final String finalPlayerName = playerName;
        final List<Integer> finalMatchNumbers = List.copyOf(matchNumbers);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    handleSameDayCancelAndRecruitBatch(finalSession, finalPlayerId, finalPlayerName, finalMatchNumbers);
                }
            });
        } else {
            handleSameDayCancelAndRecruitBatch(finalSession, finalPlayerId, finalPlayerName, finalMatchNumbers);
        }
    }

    /**
     * 複数の cancelParticipationSuppressed 結果から当日キャンセル分を分離し、
     * セッション×プレイヤー単位でまとめて afterCommit に通知送信を登録する。
     * 戻り値は通常の繰り上げ通知用データ（sameDayCancelContext が null のもの）のリスト。
     * 呼び出し元は戻り値を従来通り {@link #sendBatchedAdminWaitlistNotifications} に渡す。
     */
    public List<AdminWaitlistNotificationData> dispatchSameDayCancelNotifications(
            List<AdminWaitlistNotificationData> input) {
        if (input == null || input.isEmpty()) return List.of();

        List<AdminWaitlistNotificationData> normal = new ArrayList<>();
        // キー: (sessionId, playerId) / 値: (session, playerName, matchNumbers)
        Map<String, SameDayCancelAggregate> groups = new LinkedHashMap<>();

        for (AdminWaitlistNotificationData data : input) {
            SameDayCancelContext ctx = data.getSameDayCancelContext();
            if (ctx == null) {
                normal.add(data);
                continue;
            }
            String key = ctx.getSession().getId() + ":" + ctx.getPlayerId();
            SameDayCancelAggregate agg = groups.computeIfAbsent(key,
                    k -> new SameDayCancelAggregate(ctx.getSession(), ctx.getPlayerId(),
                            ctx.getPlayerName(), new ArrayList<>()));
            agg.matchNumbers.add(ctx.getMatchNumber());
        }

        for (SameDayCancelAggregate agg : groups.values()) {
            registerSameDayCancelAfterCommit(agg.session, agg.playerId, agg.playerName, agg.matchNumbers);
        }

        return normal;
    }

    private static class SameDayCancelAggregate {
        final PracticeSession session;
        final Long playerId;
        final String playerName;
        final List<Integer> matchNumbers;

        SameDayCancelAggregate(PracticeSession session, Long playerId, String playerName, List<Integer> matchNumbers) {
            this.session = session;
            this.playerId = playerId;
            this.playerName = playerName;
            this.matchNumbers = matchNumbers;
        }
    }

    /**
     * キャンセル待ちリストから次の人を繰り上げる
     *
     * @return 繰り上げた参加者（繰り上げ対象なしの場合はempty）
     */
    @Transactional
    public Optional<PracticeParticipant> promoteNextWaitlisted(Long sessionId, Integer matchNumber, LocalDate sessionDate) {
        return promoteNextWaitlisted(sessionId, matchNumber, sessionDate, null);
    }

    /**
     * キャンセル待ちリストから次の人を繰り上げる（特定プレイヤーを除外可能）
     *
     * @param excludePlayerId 除外するプレイヤーID（nullなら除外なし）
     * @return 繰り上げた参加者（繰り上げ対象なしの場合はempty）
     */
    @Transactional
    public Optional<PracticeParticipant> promoteNextWaitlisted(Long sessionId, Integer matchNumber, LocalDate sessionDate, Long excludePlayerId) {
        if (!hasOfferableVacancy(sessionId, matchNumber)) {
            log.info("Skip promotion for session {} match {}: no available slot (capacity already reserved)",
                    sessionId, matchNumber);
            return Optional.empty();
        }

        Optional<PracticeParticipant> nextWaitlisted = excludePlayerId != null
                ? practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusAndPlayerIdNotOrderByWaitlistNumberAsc(
                            sessionId, matchNumber, ParticipantStatus.WAITLISTED, excludePlayerId)
                : practiceParticipantRepository
                    .findFirstBySessionIdAndMatchNumberAndStatusOrderByWaitlistNumberAsc(
                            sessionId, matchNumber, ParticipantStatus.WAITLISTED);

        if (nextWaitlisted.isEmpty()) {
            log.info("No waitlisted players remaining for session {} match {} - slot remains open",
                    sessionId, matchNumber);
            return Optional.empty();
        }

        PracticeParticipant next = nextWaitlisted.get();

        // OFFEREDに変更し、応答期限を設定
        LocalDateTime deadline = lotteryDeadlineHelper.calculateOfferDeadline(sessionDate);
        LocalDateTime now = JstDateTimeUtil.now();

        // 応答期限が既に過ぎている場合はオファーを発行しない
        // （当日12:00以降は SameDayConfirmationScheduler による当日補充フローに移行する）
        if (!deadline.isAfter(now)) {
            log.info("Skip promotion for session {} match {}: offer deadline {} is already past",
                    sessionId, matchNumber, deadline);
            return Optional.empty();
        }

        Integer oldWaitlistNumber = next.getWaitlistNumber();

        next.setStatus(ParticipantStatus.OFFERED);
        next.setDirty(true);
        next.setOfferedAt(now);
        next.setOfferDeadline(deadline);
        practiceParticipantRepository.save(next);

        // OFFERED時点では番号を繰り上げない（離脱確定時＝WON/DECLINED時に繰り上げる）

        log.info("Offered waitlist #{} (player {}) for session {} match {}. Deadline: {}",
                oldWaitlistNumber, next.getPlayerId(), sessionId, matchNumber, deadline);

        // アプリ内通知を送信（LINE通知は呼び出し元でバッチ送信する）
        notificationService.createOfferNotification(next);

        return Optional.of(next);
    }

    /**
     * 発行可能な空き枠があるかを判定する。
     * OFFERED は「仮押さえ中の枠」とみなし、WON + OFFERED が定員以上なら新規オファーは出さない。
     */
    private boolean hasOfferableVacancy(Long sessionId, Integer matchNumber) {
        Optional<PracticeSession> sessionOpt = practiceSessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) {
            // 呼び出し側でセッションは通常存在するが、欠損時は既存挙動を優先して処理継続する。
            log.warn("Session {} not found while checking vacancy for match {}", sessionId, matchNumber);
            return true;
        }

        PracticeSession session = sessionOpt.get();
        int capacity = session.getCapacity() != null ? session.getCapacity() : Integer.MAX_VALUE;
        long wonCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                sessionId, matchNumber, ParticipantStatus.WON);
        long offeredCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                sessionId, matchNumber, ParticipantStatus.OFFERED);
        long availableSlots = (long) capacity - wonCount - offeredCount;

        return availableSlots > 0;
    }

    /**
     * 繰り上げオファーに対する応答を処理する
     *
     * @param participantId 参加者レコードID
     * @param accept        true=参加する, false=参加しない
     */
    @Transactional
    public void respondToOffer(Long participantId, boolean accept) {
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            throw new IllegalStateException("OFFERED状態のみ応答できます（現在: " + participant.getStatus() + "）");
        }

        // 応答期限の超過チェック
        if (participant.getOfferDeadline() != null
                && JstDateTimeUtil.now().isAfter(participant.getOfferDeadline())) {
            throw new IllegalStateException("応答期限が過ぎています");
        }

        participant.setRespondedAt(JstDateTimeUtil.now());

        if (accept) {
            participant.setStatus(ParticipantStatus.WON);
            participant.setDirty(true);
            participant.setWaitlistNumber(null);
            log.info("Player {} accepted offer for session {} match {}",
                    participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());

            // キャンセル待ち列から離脱確定 → 残存キューを再採番
            practiceParticipantRepository.save(participant);
            renumberRemainingWaitlist(participant.getSessionId(), participant.getMatchNumber());

            // 同一セッション×同一プレイヤーの残りOFFEREDを検索し、残りがあれば通知
            List<PracticeParticipant> remainingOffered = practiceParticipantRepository
                    .findBySessionIdAndPlayerIdAndStatus(
                            participant.getSessionId(), participant.getPlayerId(), ParticipantStatus.OFFERED);
            if (!remainingOffered.isEmpty()) {
                lineNotificationService.sendRemainingOfferNotification(remainingOffered);
            }

            // 管理者通知
            PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
            AdminWaitlistNotificationData notifData = AdminWaitlistNotificationData.builder()
                    .triggerAction("オファー承諾")
                    .triggerPlayerId(participant.getPlayerId())
                    .sessionId(participant.getSessionId())
                    .matchNumber(participant.getMatchNumber())
                    .promotedParticipant(null)
                    .build();
            sendBatchedAdminWaitlistNotifications(List.of(notifData), session);
        } else {
            participant.setStatus(ParticipantStatus.DECLINED);
            participant.setDirty(true);
            participant.setWaitlistNumber(null);
            practiceParticipantRepository.save(participant);

            // キャンセル待ち列から離脱確定 → 残存キューを再採番
            renumberRemainingWaitlist(participant.getSessionId(), participant.getMatchNumber());

            log.info("Player {} declined offer for session {} match {}",
                    participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());

            // 次のキャンセル待ちに通知
            PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                    .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
            Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                    participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

            // 繰り上げ先プレイヤーへLINE通知
            promoted.ifPresent(p -> lineNotificationService.sendWaitlistOfferNotification(p));

            // 管理者通知
            AdminWaitlistNotificationData notifData = AdminWaitlistNotificationData.builder()
                    .triggerAction("オファー辞退")
                    .triggerPlayerId(participant.getPlayerId())
                    .sessionId(participant.getSessionId())
                    .matchNumber(participant.getMatchNumber())
                    .promotedParticipant(promoted.orElse(null))
                    .build();
            sendBatchedAdminWaitlistNotifications(List.of(notifData), session);

            densukeSyncService.triggerWriteAsync();
            return;
        }

        densukeSyncService.triggerWriteAsync();
    }

    /**
     * オファー辞退処理（通知抑制版）。
     * LINE通知（繰り上げ先・管理者）を送信せず、通知データを返す。
     * DensukeImportServiceでバッチ処理する用途。
     *
     * @param participantId 参加者レコードID
     * @return 通知データ（繰り上げ結果 + 管理者通知データ）
     */
    @Transactional
    public AdminWaitlistNotificationData respondToOfferDeclineSuppressed(Long participantId) {
        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            throw new IllegalStateException("OFFERED状態のみ応答できます（現在: " + participant.getStatus() + "）");
        }

        // 応答期限の超過チェック
        if (participant.getOfferDeadline() != null
                && JstDateTimeUtil.now().isAfter(participant.getOfferDeadline())) {
            throw new IllegalStateException("応答期限が過ぎています");
        }

        participant.setStatus(ParticipantStatus.DECLINED);
        participant.setDirty(true);
        participant.setWaitlistNumber(null);
        participant.setRespondedAt(JstDateTimeUtil.now());
        practiceParticipantRepository.save(participant);

        renumberRemainingWaitlist(participant.getSessionId(), participant.getMatchNumber());

        log.info("Player {} declined offer (suppressed) for session {} match {}",
                participant.getPlayerId(), participant.getSessionId(), participant.getMatchNumber());

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

        densukeSyncService.triggerWriteAsync();

        return AdminWaitlistNotificationData.builder()
                .triggerAction("オファー辞退")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.orElse(null))
                .build();
    }

    /**
     * 同一セッション内の全OFFEREDを一括承諾/辞退する
     *
     * @param sessionId セッションID
     * @param playerId  プレイヤーID
     * @param accept    true=全て参加, false=全て辞退
     * @return 処理した件数
     */
    @Transactional
    public int respondToOfferAll(Long sessionId, Long playerId, boolean accept) {
        List<PracticeParticipant> offered = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndStatus(sessionId, playerId, ParticipantStatus.OFFERED);

        if (offered.isEmpty()) {
            throw new IllegalStateException("応答可能なオファーがありません");
        }

        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        if (accept) {
            // 全OFFEREDをWONに変更
            int acceptedCount = 0;
            Set<Integer> affectedMatches = new LinkedHashSet<>();
            List<AdminWaitlistNotificationData> notificationDataList = new ArrayList<>();
            for (PracticeParticipant p : offered) {
                // 応答期限チェック
                if (p.getOfferDeadline() != null && JstDateTimeUtil.now().isAfter(p.getOfferDeadline())) {
                    log.warn("Offer expired for player {} session {} match {} during batch accept, skipping",
                            playerId, sessionId, p.getMatchNumber());
                    continue;
                }
                p.setStatus(ParticipantStatus.WON);
                p.setDirty(true);
                p.setWaitlistNumber(null);
                p.setRespondedAt(JstDateTimeUtil.now());
                practiceParticipantRepository.save(p);
                affectedMatches.add(p.getMatchNumber());
                acceptedCount++;
                log.info("Player {} accepted offer (batch) for session {} match {}",
                        playerId, sessionId, p.getMatchNumber());

                notificationDataList.add(AdminWaitlistNotificationData.builder()
                        .triggerAction("オファー承諾")
                        .triggerPlayerId(playerId)
                        .sessionId(sessionId)
                        .matchNumber(p.getMatchNumber())
                        .promotedParticipant(null)
                        .build());
            }
            // 影響試合ごとに再採番
            for (Integer matchNumber : affectedMatches) {
                renumberRemainingWaitlist(sessionId, matchNumber);
            }
            if (acceptedCount == 0) {
                throw new IllegalStateException("すべてのオファーが期限切れです");
            }

            // 管理者通知をバッチ送信
            sendBatchedAdminWaitlistNotifications(notificationDataList, session);

            densukeSyncService.triggerWriteAsync();
            return acceptedCount;
        } else {
            // 全OFFEREDをDECLINEDに変更し、各試合で繰り上げ発動
            List<AdminWaitlistNotificationData> notificationDataList = new ArrayList<>();
            List<PracticeParticipant> declined = new ArrayList<>();
            Set<Integer> affectedMatches = new LinkedHashSet<>();

            for (PracticeParticipant p : offered) {
                // 期限切れチェック（単体respondToOfferと整合）
                if (p.getOfferDeadline() != null && JstDateTimeUtil.now().isAfter(p.getOfferDeadline())) {
                    log.warn("Offer expired for player {} session {} match {} during batch decline, skipping",
                            playerId, sessionId, p.getMatchNumber());
                    continue;
                }
                p.setStatus(ParticipantStatus.DECLINED);
                p.setDirty(true);
                p.setWaitlistNumber(null);
                p.setRespondedAt(JstDateTimeUtil.now());
                practiceParticipantRepository.save(p);
                declined.add(p);
                affectedMatches.add(p.getMatchNumber());
                log.info("Player {} declined offer (batch) for session {} match {}",
                        playerId, sessionId, p.getMatchNumber());
            }

            if (declined.isEmpty()) {
                throw new IllegalStateException("すべてのオファーが期限切れです");
            }

            // 影響試合ごとに再採番
            for (Integer matchNumber : affectedMatches) {
                renumberRemainingWaitlist(sessionId, matchNumber);
            }

            // 各試合で次のキャンセル待ちに繰り上げ（通知は蓄積して後でまとめ送信）
            List<PracticeParticipant> promotedList = new ArrayList<>();
            for (PracticeParticipant p : declined) {
                Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                        sessionId, p.getMatchNumber(), session.getSessionDate());

                promoted.ifPresent(promotedList::add);

                notificationDataList.add(AdminWaitlistNotificationData.builder()
                        .triggerAction("オファー辞退")
                        .triggerPlayerId(playerId)
                        .sessionId(sessionId)
                        .matchNumber(p.getMatchNumber())
                        .promotedParticipant(promoted.orElse(null))
                        .build());
            }

            // 繰り上げ先プレイヤーへ統合LINE通知（セッション×プレイヤーでグルーピング）
            if (!promotedList.isEmpty()) {
                Map<Long, List<PracticeParticipant>> byPlayer = new LinkedHashMap<>();
                for (PracticeParticipant pp : promotedList) {
                    byPlayer.computeIfAbsent(pp.getPlayerId(), k -> new ArrayList<>()).add(pp);
                }
                for (List<PracticeParticipant> playerOffered : byPlayer.values()) {
                    lineNotificationService.sendConsolidatedWaitlistOfferNotification(
                            playerOffered, session, "オファー辞退", playerId);
                }
            }

            // 管理者通知をバッチ送信
            sendBatchedAdminWaitlistNotifications(notificationDataList, session);
        }

        densukeSyncService.triggerWriteAsync();
        return offered.size();
    }

    /**
     * セッション単位でキャンセル待ちを辞退する
     *
     * @param sessionId セッションID
     * @param playerId  プレイヤーID
     * @return 辞退した件数
     */
    @Transactional
    public int declineWaitlistBySession(Long sessionId, Long playerId) {
        List<PracticeParticipant> waitlisted = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndStatus(sessionId, playerId, ParticipantStatus.WAITLISTED);

        if (waitlisted.isEmpty()) {
            throw new IllegalStateException("辞退対象のキャンセル待ちがありません");
        }

        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        // 通知データを蓄積
        List<AdminWaitlistNotificationData> notificationDataList = new ArrayList<>();
        Set<Integer> affectedMatches = new LinkedHashSet<>();

        for (PracticeParticipant p : waitlisted) {
            Integer oldNumber = p.getWaitlistNumber();
            p.setStatus(ParticipantStatus.WAITLIST_DECLINED);
            p.setDirty(true);
            p.setWaitlistNumber(null);
            practiceParticipantRepository.save(p);

            affectedMatches.add(p.getMatchNumber());

            notificationDataList.add(AdminWaitlistNotificationData.builder()
                    .triggerAction("キャンセル待ち辞退")
                    .triggerPlayerId(playerId)
                    .sessionId(sessionId)
                    .matchNumber(p.getMatchNumber())
                    .promotedParticipant(null)
                    .build());

            log.info("Player {} declined waitlist for session {} match {} (was #{})",
                    playerId, sessionId, p.getMatchNumber(), oldNumber);
        }

        // 影響試合ごとに1回だけ再採番
        for (Integer matchNumber : affectedMatches) {
            renumberRemainingWaitlist(sessionId, matchNumber);
        }

        // まとめて通知送信
        sendBatchedAdminWaitlistNotifications(notificationDataList, session);

        densukeSyncService.triggerWriteAsync();
        return waitlisted.size();
    }

    /**
     * セッション単位でキャンセル待ちに復帰する
     *
     * @param sessionId セッションID
     * @param playerId  プレイヤーID
     * @return 復帰した件数
     */
    @Transactional
    public int rejoinWaitlistBySession(Long sessionId, Long playerId) {
        List<PracticeParticipant> declined = practiceParticipantRepository
                .findBySessionIdAndPlayerIdAndStatus(sessionId, playerId, ParticipantStatus.WAITLIST_DECLINED);

        if (declined.isEmpty()) {
            throw new IllegalStateException("復帰対象がありません（WAITLIST_DECLINED状態のものがない）");
        }

        for (PracticeParticipant p : declined) {
            // 該当試合の最後尾番号を取得（OFFEREDも含めて重複を防ぐ）
            int maxNumber = practiceParticipantRepository
                    .findMaxWaitlistNumberIncludingOffered(sessionId, p.getMatchNumber())
                    .orElse(0);

            p.setStatus(ParticipantStatus.WAITLISTED);
            p.setDirty(true);
            p.setWaitlistNumber(maxNumber + 1);
            practiceParticipantRepository.save(p);

            log.info("Player {} rejoined waitlist for session {} match {} (new #{})",
                    playerId, sessionId, p.getMatchNumber(), maxNumber + 1);
        }

        densukeSyncService.triggerWriteAsync();

        return declined.size();
    }

    /**
     * 期限切れのOFFERを自動的にDECLINEDにし、次の人に繰り上げる
     * （OfferExpirySchedulerから呼ばれる）
     */
    @Transactional
    public void expireOffer(PracticeParticipant participant) {
        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            return;
        }

        Integer oldNumber = participant.getWaitlistNumber();
        participant.setStatus(ParticipantStatus.DECLINED);
        participant.setDirty(true);
        participant.setRespondedAt(JstDateTimeUtil.now());
        participant.setWaitlistNumber(null);
        practiceParticipantRepository.save(participant);

        // キャンセル待ち列から離脱確定 → 残存キューを再採番
        renumberRemainingWaitlist(participant.getSessionId(), participant.getMatchNumber());

        log.info("Offer expired for player {} in session {} match {} (was waitlist #{})",
                participant.getPlayerId(), participant.getSessionId(),
                participant.getMatchNumber(), oldNumber);

        notificationService.createOfferExpiredNotification(participant);

        // LINE通知を送信
        lineNotificationService.sendOfferExpiredNotification(participant);

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

        // 繰り上げ先プレイヤーへLINE通知
        promoted.ifPresent(p -> lineNotificationService.sendWaitlistOfferNotification(p));

        // 管理者通知
        AdminWaitlistNotificationData notifData = AdminWaitlistNotificationData.builder()
                .triggerAction("オファー期限切れ")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.orElse(null))
                .build();
        sendBatchedAdminWaitlistNotifications(List.of(notifData), session);
    }

    /**
     * オファー期限切れ処理（通知抑制版）。
     * LINE通知（繰り上げ先・管理者）を送信せず、通知データを返す。
     * OfferExpirySchedulerでバッチ処理する用途。
     *
     * @param participant 期限切れのOFFERED参加者
     * @return 通知データ（繰り上げ結果 + 管理者通知データ）。OFFERED以外の場合はnull
     */
    @Transactional
    public ExpireOfferResult expireOfferSuppressed(PracticeParticipant participant) {
        if (participant.getStatus() != ParticipantStatus.OFFERED) {
            return null;
        }

        Integer oldNumber = participant.getWaitlistNumber();
        participant.setStatus(ParticipantStatus.DECLINED);
        participant.setDirty(true);
        participant.setRespondedAt(JstDateTimeUtil.now());
        participant.setWaitlistNumber(null);
        practiceParticipantRepository.save(participant);

        // キャンセル待ち列から離脱確定 → 残存キューを再採番
        renumberRemainingWaitlist(participant.getSessionId(), participant.getMatchNumber());

        log.info("Offer expired for player {} in session {} match {} (was waitlist #{})",
                participant.getPlayerId(), participant.getSessionId(),
                participant.getMatchNumber(), oldNumber);

        // アプリ内通知・期限切れ本人へのLINE通知は個別送信（統合不要）
        notificationService.createOfferExpiredNotification(participant);
        lineNotificationService.sendOfferExpiredNotification(participant);

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));
        Optional<PracticeParticipant> promoted = promoteNextWaitlisted(
                participant.getSessionId(), participant.getMatchNumber(), session.getSessionDate());

        AdminWaitlistNotificationData notifData = AdminWaitlistNotificationData.builder()
                .triggerAction("オファー期限切れ")
                .triggerPlayerId(participant.getPlayerId())
                .sessionId(participant.getSessionId())
                .matchNumber(participant.getMatchNumber())
                .promotedParticipant(promoted.orElse(null))
                .build();

        return ExpireOfferResult.builder()
                .notificationData(notifData)
                .promotedParticipant(promoted.orElse(null))
                .build();
    }

    /**
     * 当日12:00確定時に、OFFERED状態の参加者を期限切れ処理する。
     * 通常の expireOffer と異なり、promoteNextWaitlisted ではなく
     * 当日空き募集フロー（先着ボタン方式）を使用する。
     * SameDayConfirmationScheduler から呼ばれる。
     */
    @Transactional
    public void expireOfferedForSameDayConfirmation(PracticeSession session) {
        List<PracticeParticipant> offered = practiceParticipantRepository
                .findBySessionIdAndStatus(session.getId(), ParticipantStatus.OFFERED);

        if (offered.isEmpty()) {
            log.debug("No OFFERED participants for session {}", session.getId());
            return;
        }

        log.info("Expiring {} OFFERED participants for same-day confirmation (session {})",
                offered.size(), session.getId());

        Set<Integer> affectedMatches = new LinkedHashSet<>();

        for (PracticeParticipant participant : offered) {
            Integer oldNumber = participant.getWaitlistNumber();
            participant.setStatus(ParticipantStatus.DECLINED);
            participant.setDirty(true);
            participant.setRespondedAt(JstDateTimeUtil.now());
            participant.setWaitlistNumber(null);
            practiceParticipantRepository.save(participant);

            notificationService.createOfferExpiredNotification(participant);
            lineNotificationService.sendOfferExpiredNotification(participant);

            affectedMatches.add(participant.getMatchNumber());

            log.info("Expired offer for player {} session {} match {} (was waitlist #{})",
                    participant.getPlayerId(), session.getId(), participant.getMatchNumber(), oldNumber);
        }

        // 影響試合ごとに1回だけ再採番
        for (Integer matchNumber : affectedMatches) {
            renumberRemainingWaitlist(session.getId(), matchNumber);
        }

        // 空き枠がある試合を蓄積し、セッション単位で統合通知
        Map<Integer, Integer> vacanciesByMatch = new LinkedHashMap<>();
        int capacity = session.getCapacity() != null ? session.getCapacity() : 0;

        for (Integer matchNumber : affectedMatches) {
            long wonCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                    session.getId(), matchNumber, ParticipantStatus.WON);

            if (wonCount < capacity) {
                int vacancies = (int) (capacity - wonCount);
                vacanciesByMatch.put(matchNumber, vacancies);
                log.info("Triggered same-day vacancy recruitment for session {} match {} ({} vacancies)",
                        session.getId(), matchNumber, vacancies);
            }
        }

        if (!vacanciesByMatch.isEmpty()) {
            lineNotificationService.sendConsolidatedSameDayVacancyNotification(
                    session, vacanciesByMatch, null);
        }

        densukeSyncService.triggerWriteAsync();
    }

    /**
     * 複数試合分のキャンセル待ち状況通知をまとめて送信する。
     * 同一セッション×トリガー×プレイヤーの通知データをリストで受け取り、
     * 試合ごとのキャンセル待ち列を収集して1通のFlexメッセージとして送信する。
     *
     * @param notificationDataList 通知データのリスト（同一セッション・トリガー・プレイヤー）
     * @param session              対象セッション
     */
    public void sendBatchedAdminWaitlistNotifications(List<AdminWaitlistNotificationData> notificationDataList,
                                                       PracticeSession session) {
        if (notificationDataList.isEmpty()) return;

        try {
            AdminWaitlistNotificationData first = notificationDataList.get(0);
            Player triggerPlayer = playerRepository.findById(first.getTriggerPlayerId()).orElse(null);
            if (triggerPlayer == null) return;

            // 各試合の情報を収集
            List<Integer> matchNumbers = new ArrayList<>();
            Map<Integer, List<PracticeParticipant>> waitlistByMatch = new LinkedHashMap<>();
            Map<Integer, Player> offeredPlayerByMatch = new HashMap<>();

            for (AdminWaitlistNotificationData data : notificationDataList) {
                matchNumbers.add(data.getMatchNumber());

                List<PracticeParticipant> remainingWaitlist = practiceParticipantRepository
                        .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                                session.getId(), data.getMatchNumber(),
                                List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED));
                waitlistByMatch.put(data.getMatchNumber(), remainingWaitlist);

                if (data.getPromotedParticipant() != null) {
                    Player offeredPlayer = playerRepository.findById(data.getPromotedParticipant().getPlayerId()).orElse(null);
                    offeredPlayerByMatch.put(data.getMatchNumber(), offeredPlayer);
                }
            }

            // 管理者向け通知（1通のFlexにまとめて送信）
            lineNotificationService.sendAdminWaitlistNotification(
                    first.getTriggerAction(), triggerPlayer, session, matchNumbers,
                    waitlistByMatch, offeredPlayerByMatch);

            // WAITLISTEDユーザー向け通知（同じFlexを使用）
            lineNotificationService.sendWaitlistPositionUpdateNotifications(
                    first.getTriggerAction(), triggerPlayer, session, matchNumbers,
                    waitlistByMatch, offeredPlayerByMatch);
        } catch (Exception e) {
            log.error("Failed to send batched waitlist change notifications: {}", e.getMessage(), e);
        }
    }

    /**
     * 容量拡張に伴い、キャンセル待ちを応答期限なしの OFFERED に昇格する。
     *
     * 各試合について WON + 既存OFFERED が capacity に達するまで、waitlist_number 昇順で
     * WAITLISTED を OFFERED（offer_deadline=null）に変更する。空き枠を超えた分の WAITLISTED は
     * そのまま残す（waitlist_number も維持）。
     * 既存OFFERED の応答期限は一律 null にクリアする（容量拡張で参加が確定したため）。
     *
     * @param sessionId 対象セッションID
     */
    @Transactional
    public void promoteWaitlistedAfterCapacityIncrease(Long sessionId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));

        LocalDateTime now = JstDateTimeUtil.now();

        // 既存OFFERED は応答期限を一律クリア（拡張で参加確定）
        List<PracticeParticipant> existingOffered = practiceParticipantRepository
                .findBySessionIdAndStatus(sessionId, ParticipantStatus.OFFERED);
        for (PracticeParticipant p : existingOffered) {
            if (p.getOfferDeadline() != null) {
                p.setOfferDeadline(null);
                p.setDirty(true);
            }
        }
        if (!existingOffered.isEmpty()) {
            practiceParticipantRepository.saveAll(existingOffered);
        }

        Integer capacity = session.getCapacity();

        // 容量制限なし／match_number 別の WAITLISTED を waitlist_number 昇順で処理
        List<PracticeParticipant> waitlisted = practiceParticipantRepository
                .findBySessionIdAndStatus(sessionId, ParticipantStatus.WAITLISTED);
        if (waitlisted.isEmpty()) {
            log.info("promoteWaitlistedAfterCapacityIncrease: session {} has no WAITLISTED to promote", sessionId);
            return;
        }

        Map<Integer, List<PracticeParticipant>> waitlistedByMatch = waitlisted.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber));

        int totalPromoted = 0;
        int totalRemained = 0;
        List<PracticeParticipant> toSave = new ArrayList<>();

        for (Map.Entry<Integer, List<PracticeParticipant>> entry : waitlistedByMatch.entrySet()) {
            Integer matchNumber = entry.getKey();
            List<PracticeParticipant> matchWaitlisted = new ArrayList<>(entry.getValue());
            matchWaitlisted.sort(Comparator.comparing(p ->
                    p.getWaitlistNumber() == null ? Integer.MAX_VALUE : p.getWaitlistNumber()));

            long wonCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                    sessionId, matchNumber, ParticipantStatus.WON);
            long offeredCount = practiceParticipantRepository.countBySessionIdAndMatchNumberAndStatus(
                    sessionId, matchNumber, ParticipantStatus.OFFERED);

            long availableSlots;
            if (capacity == null) {
                // 容量無制限：全員昇格
                availableSlots = matchWaitlisted.size();
            } else {
                availableSlots = (long) capacity - wonCount - offeredCount;
            }

            if (availableSlots <= 0) {
                log.info("Skip match {} in session {}: no available slot (won={}, offered={}, capacity={})",
                        matchNumber, sessionId, wonCount, offeredCount, capacity);
                totalRemained += matchWaitlisted.size();
                continue;
            }

            int promoteCount = (int) Math.min(availableSlots, matchWaitlisted.size());
            for (int i = 0; i < promoteCount; i++) {
                PracticeParticipant p = matchWaitlisted.get(i);
                p.setStatus(ParticipantStatus.OFFERED);
                p.setOfferedAt(now);
                p.setOfferDeadline(null);
                p.setDirty(true);
                toSave.add(p);
            }
            totalPromoted += promoteCount;
            totalRemained += matchWaitlisted.size() - promoteCount;
            log.info("Promoted {} WAITLISTED to OFFERED for session {} match {} (won={}, offered_before={}, capacity={}, remained={})",
                    promoteCount, sessionId, matchNumber, wonCount, offeredCount, capacity,
                    matchWaitlisted.size() - promoteCount);
        }

        if (!toSave.isEmpty()) {
            practiceParticipantRepository.saveAll(toSave);
        }

        // 影響試合ごとに waitlist_number を 1..N で再採番
        for (Integer matchNumber : waitlistedByMatch.keySet()) {
            renumberRemainingWaitlist(sessionId, matchNumber);
        }

        log.info("promoteWaitlistedAfterCapacityIncrease completed for session {}: promoted={}, remainedAsWaitlisted={}",
                sessionId, totalPromoted, totalRemained);
    }

    /**
     * 指定試合の残存キャンセル待ち（WAITLISTED + OFFERED）を 1..N で再採番する。
     * decrement方式では OFFERED が対象外になり番号が崩れるため、全ステータスを一括で再付番する。
     */
    private void renumberRemainingWaitlist(Long sessionId, Integer matchNumber) {
        List<PracticeParticipant> remaining = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatusInOrderByWaitlistNumberAsc(
                        sessionId, matchNumber,
                        List.of(ParticipantStatus.WAITLISTED, ParticipantStatus.OFFERED));
        for (int i = 0; i < remaining.size(); i++) {
            PracticeParticipant p = remaining.get(i);
            int newNumber = i + 1;
            if (!Integer.valueOf(newNumber).equals(p.getWaitlistNumber())) {
                p.setWaitlistNumber(newNumber);
                practiceParticipantRepository.save(p);
            }
        }
    }
}
