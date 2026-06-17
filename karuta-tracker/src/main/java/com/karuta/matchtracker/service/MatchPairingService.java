package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchPairing;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class MatchPairingService {

    private static final int MATCH_HISTORY_DAYS = 30;
    private static final double SAME_DAY_PENALTY_SCORE = -1000.0;
    private static final double INTERVAL_BASE_SCORE = 100.0;

    /** 選手起点の最近ペアリング取得で返す最大件数。 */
    private static final int RECENT_PAIRINGS_LIMIT = 30;

    private final MatchPairingRepository matchPairingRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;

    /**
     * 指定日の対戦組み合わせを取得
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDate(LocalDate sessionDate) {
        return getByDate(sessionDate, false, null);
    }

    /**
     * 指定日の対戦組み合わせを取得（軽量オプション付き）
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDate(LocalDate sessionDate, boolean light) {
        return getByDate(sessionDate, light, null);
    }

    /**
     * 指定日の対戦組み合わせを取得（軽量オプション・組織スコープ対応）
     *
     * organizationId が指定されている場合は当該団体のセッション参加者に紐づく
     * ペアリングのみを返す。同日に複数団体のセッションがあっても他団体の
     * 組み合わせが混入しないようにする（createBatch / autoMatch と同じスコープ）。
     * organizationId == null は SUPER_ADMIN / PLAYER 経路で組織非限定の取得を許可する。
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDate(LocalDate sessionDate, boolean light, Long organizationId) {
        List<MatchPairing> pairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
        if (organizationId != null) {
            Set<Long> sessionPlayerIds = getSessionAllPlayerIds(sessionDate, organizationId);
            pairings = filterPairingsBySession(pairings, sessionPlayerIds, true);
        }
        // 全選手IDを一括取得してN+1問題を回避
        Map<Long, String> playerNames = collectPlayerNames(pairings);
        List<MatchPairingDto> dtos = pairings.stream()
                .map(p -> convertToDtoWithCache(p, playerNames))
                .collect(Collectors.toList());
        if (!light) {
            enrichWithRecentMatches(dtos, sessionDate, null);
        }
        enrichWithMatchResults(dtos, sessionDate);
        return dtos;
    }

    /**
     * 指定日・試合番号の対戦組み合わせを取得
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber) {
        return getByDateAndMatchNumber(sessionDate, matchNumber, null);
    }

    /**
     * 指定日・試合番号の対戦組み合わせを取得（組織スコープ対応）
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber, Long organizationId) {
        List<MatchPairing> pairings = matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber);
        if (organizationId != null) {
            Set<Long> sessionPlayerIds = getSessionAllPlayerIds(sessionDate, organizationId);
            pairings = filterPairingsBySession(pairings, sessionPlayerIds, true);
        }
        Map<Long, String> playerNames = collectPlayerNames(pairings);
        List<MatchPairingDto> dtos = pairings.stream()
                .map(p -> convertToDtoWithCache(p, playerNames))
                .collect(Collectors.toList());
        enrichWithRecentMatches(dtos, sessionDate, matchNumber);
        enrichWithMatchResults(dtos, sessionDate);
        return dtos;
    }

    /**
     * 指定選手が player1 または player2 に含まれる最近のペアリングを取得する。
     *
     * 動画倉庫の登録モーダル「選手起点」で、結果未入力（match_pairings にのみ存在）の試合も
     * 選択肢に含めるために使用する。並びは sessionDate DESC, matchNumber DESC、直近
     * {@code RECENT_PAIRINGS_LIMIT} 件に制限する。選手名は一括解決して N+1 を回避する。
     *
     * <p>返すのはペアリング（組み合わせ）であり結果(matches)とは別物。recentMatches /
     * 試合結果（hasResult 等）は付与しない（登録モーダルの選択肢用の軽量レスポンス）。</p>
     *
     * @param playerId 選手ID
     * @return 最近のペアリングDTOのリスト（新しい順）
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getRecentByPlayerId(Long playerId) {
        List<MatchPairing> pairings = matchPairingRepository.findRecentByPlayerId(
                playerId, PageRequest.of(0, RECENT_PAIRINGS_LIMIT));
        // 選手名を一括解決（N+1回避）してから DTO へ変換する
        Map<Long, String> playerNames = collectPlayerNames(pairings);
        return pairings.stream()
                .map(p -> convertToDtoWithCache(p, playerNames))
                .collect(Collectors.toList());
    }

    /**
     * 対戦組み合わせを作成
     */
    @Transactional
    public MatchPairingDto create(MatchPairingCreateRequest request, Long createdBy) {
        return create(request, createdBy, null);
    }

    /**
     * 対戦組み合わせを作成（組織スコープ付き）。
     *
     * organizationId が指定された場合、player1Id / player2Id が当該団体・対象日付の
     * セッション参加者に含まれていることを検証する。組織非限定（SUPER_ADMIN 等）の
     * ケースに後方互換性を持たせるため、null の場合は参加者検証をスキップする。
     */
    @Transactional
    public MatchPairingDto create(MatchPairingCreateRequest request, Long createdBy, Long organizationId) {
        // バリデーション
        if (request.getPlayer1Id().equals(request.getPlayer2Id())) {
            throw new IllegalArgumentException("同じ選手を対戦相手に設定できません");
        }

        if (organizationId != null) {
            Set<Long> sessionPlayerIds = getSessionAllPlayerIds(request.getSessionDate(), organizationId);
            if (!sessionPlayerIds.contains(request.getPlayer1Id())
                    || !sessionPlayerIds.contains(request.getPlayer2Id())) {
                throw new ForbiddenException("対象セッションの参加者でない選手は組み合わせに含められません");
            }
        }

        MatchPairing pairing = MatchPairing.builder()
                .sessionDate(request.getSessionDate())
                .matchNumber(request.getMatchNumber())
                .player1Id(request.getPlayer1Id())
                .player2Id(request.getPlayer2Id())
                .createdBy(createdBy)
                .build();

        MatchPairing saved = matchPairingRepository.save(pairing);
        return convertToDto(saved);
    }

    /**
     * 対戦組み合わせを一括作成
     */
    @Transactional
    public List<MatchPairingDto> createBatch(LocalDate sessionDate, Integer matchNumber,
                                              List<MatchPairingCreateRequest> requests,
                                              List<Long> waitingPlayerIds, Long createdBy,
                                              Long organizationId) {
        // 組織スコープ: セッション参加者でフィルタ
        Set<Long> sessionPlayerIds = getSessionAllPlayerIds(sessionDate, organizationId);

        // 組織スコープ実行（PLAYER / ADMIN）時は、新規ペアと待機者の選手IDが
        // 当該セッション参加者であることを保存前に検証する。所属外IDをリクエストに
        // 混入させて他団体・他セッションのデータを汚染する経路を遮断する。
        if (organizationId != null) {
            for (MatchPairingCreateRequest req : requests) {
                if (!sessionPlayerIds.contains(req.getPlayer1Id())
                        || !sessionPlayerIds.contains(req.getPlayer2Id())) {
                    throw new ForbiddenException("対象セッションの参加者でない選手は組み合わせに含められません");
                }
            }
            if (waitingPlayerIds != null) {
                for (Long playerId : waitingPlayerIds) {
                    if (!sessionPlayerIds.contains(playerId)) {
                        throw new ForbiddenException("対象セッションの参加者でない選手は待機者に含められません");
                    }
                }
            }
        }

        // ロック済みペアリング（結果入力済み）を特定して保持
        boolean orgScoped = organizationId != null;
        List<MatchPairing> existingPairings = filterPairingsBySession(
                matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber), sessionPlayerIds, orgScoped);
        List<Match> existingMatches = filterMatchesBySession(
                matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber), sessionPlayerIds, orgScoped);
        Set<String> lockedPairKeys = new HashSet<>();
        Set<Long> lockedPlayerIds = new HashSet<>();
        List<MatchPairing> lockedPairings = new ArrayList<>();

        for (MatchPairing pairing : existingPairings) {
            Long p1 = Math.min(pairing.getPlayer1Id(), pairing.getPlayer2Id());
            Long p2 = Math.max(pairing.getPlayer1Id(), pairing.getPlayer2Id());
            boolean hasResult = existingMatches.stream().anyMatch(m ->
                    (Math.min(m.getPlayer1Id(), m.getPlayer2Id()) == p1) &&
                    (Math.max(m.getPlayer1Id(), m.getPlayer2Id()) == p2));
            if (hasResult) {
                lockedPairKeys.add(getPairKey(p1, p2));
                lockedPlayerIds.add(pairing.getPlayer1Id());
                lockedPlayerIds.add(pairing.getPlayer2Id());
                lockedPairings.add(pairing);
            }
        }

        // ロック済み以外の既存ペアリングを削除
        List<MatchPairing> toDelete = existingPairings.stream()
                .filter(p -> !lockedPairKeys.contains(getPairKey(p.getPlayer1Id(), p.getPlayer2Id())))
                .collect(Collectors.toList());
        matchPairingRepository.deleteAll(toDelete);

        // 新規ペアリングからロック済みプレイヤーを含むものを除外
        List<MatchPairing> pairings = requests.stream()
                .filter(request -> !lockedPlayerIds.contains(request.getPlayer1Id())
                        && !lockedPlayerIds.contains(request.getPlayer2Id()))
                .map(request -> MatchPairing.builder()
                        .sessionDate(sessionDate)
                        .matchNumber(matchNumber)
                        .player1Id(request.getPlayer1Id())
                        .player2Id(request.getPlayer2Id())
                        .createdBy(createdBy)
                        .build())
                .collect(Collectors.toList());

        List<MatchPairing> saved = matchPairingRepository.saveAll(pairings);
        // ロック済みペアリングも結果に含める
        saved.addAll(lockedPairings);

        // 待機者リストからロック済みプレイヤーを除外
        List<Long> filteredWaitingPlayerIds = waitingPlayerIds != null
                ? waitingPlayerIds.stream().filter(id -> !lockedPlayerIds.contains(id)).collect(Collectors.toList())
                : Collections.emptyList();

        // 抜け番（待機者）をPracticeParticipantにmatchNumber=nullで登録する
        // waitingPlayerIds が渡された場合は、まず既存抜け番を必ず削除し、フィルタ後の待機者を再登録
        if (waitingPlayerIds != null) {
            Optional<com.karuta.matchtracker.entity.PracticeSession> byeSessionOpt = organizationId != null
                    ? practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId)
                    : practiceSessionRepository.findBySessionDate(sessionDate);
            byeSessionOpt.ifPresent(session -> {
                // 既存の抜け番登録（matchNumber=null）を一旦削除
                List<PracticeParticipant> existingBye = practiceParticipantRepository
                        .findBySessionId(session.getId()).stream()
                        .filter(pp -> pp.getMatchNumber() == null)
                        .collect(Collectors.toList());
                practiceParticipantRepository.deleteAll(existingBye);

                // フィルタ後の待機者を再登録（0件なら登録なし）
                if (!filteredWaitingPlayerIds.isEmpty()) {
                    List<PracticeParticipant> byeParticipants = filteredWaitingPlayerIds.stream()
                            .map(playerId -> PracticeParticipant.builder()
                                    .sessionId(session.getId())
                                    .playerId(playerId)
                                    .matchNumber(null)
                                    .dirty(false)
                                    .build())
                            .collect(Collectors.toList());
                    practiceParticipantRepository.saveAll(byeParticipants);
                }
            });
        }

        return saved.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 対戦組み合わせの選手を更新（旧ペアの試合結果も削除）
     */
    @Transactional
    public MatchPairingDto updatePlayer(Long id, Long newPlayerId, String side, Long updatedBy) {
        return updatePlayer(id, newPlayerId, side, updatedBy, null);
    }

    /**
     * 対戦組み合わせの選手を更新（組織スコープ付き）。
     *
     * organizationId が指定された場合、newPlayerId が当該団体・対象日付のセッション
     * 参加者に含まれることを検証する。所属外選手を差し替えに使う経路を遮断する。
     * organizationId が null（SUPER_ADMIN 等の組織非限定）の場合は検証をスキップする。
     */
    @Transactional
    public MatchPairingDto updatePlayer(Long id, Long newPlayerId, String side, Long updatedBy, Long organizationId) {
        if (!"player1".equals(side) && !"player2".equals(side)) {
            throw new IllegalArgumentException("sideは'player1'または'player2'を指定してください");
        }

        MatchPairing pairing = matchPairingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MatchPairing", id));

        if (organizationId != null) {
            Set<Long> sessionPlayerIds = getSessionAllPlayerIds(pairing.getSessionDate(), organizationId);
            if (!sessionPlayerIds.contains(newPlayerId)) {
                throw new ForbiddenException("対象セッションの参加者でない選手には差し替えできません");
            }
        }

        Long oldPlayer1Id = pairing.getPlayer1Id();
        Long oldPlayer2Id = pairing.getPlayer2Id();

        // 新しい選手を設定
        if ("player1".equals(side)) {
            if (newPlayerId.equals(pairing.getPlayer2Id())) {
                throw new IllegalArgumentException("同じ選手を対戦相手に設定できません");
            }
            pairing.setPlayer1Id(newPlayerId);
        } else {
            if (newPlayerId.equals(pairing.getPlayer1Id())) {
                throw new IllegalArgumentException("同じ選手を対戦相手に設定できません");
            }
            pairing.setPlayer2Id(newPlayerId);
        }

        // 旧ペアの試合結果を削除
        deleteMatchForPairing(pairing.getSessionDate(), pairing.getMatchNumber(), oldPlayer1Id, oldPlayer2Id);

        MatchPairing saved = matchPairingRepository.save(pairing);
        return convertToDto(saved);
    }

    /**
     * 特定のペアリングに対応する試合結果を削除
     */
    private void deleteMatchForPairing(LocalDate matchDate, Integer matchNumber, Long player1Id, Long player2Id) {
        List<Match> matches = matchRepository.findByMatchDateAndMatchNumber(matchDate, matchNumber);
        for (Match match : matches) {
            boolean matchesPair = (match.getPlayer1Id().equals(player1Id) && match.getPlayer2Id().equals(player2Id))
                    || (match.getPlayer1Id().equals(player2Id) && match.getPlayer2Id().equals(player1Id));
            if (matchesPair) {
                matchRepository.delete(match);
            }
        }
    }

    /**
     * 対戦組み合わせを削除
     */
    @Transactional
    public void delete(Long id) {
        matchPairingRepository.deleteById(id);
    }

    /**
     * 指定日・試合番号の対戦組み合わせを削除（ロック済みペアリングは保持）
     */
    @Transactional
    public void deleteByDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber, Long organizationId) {
        // 組織スコープ: セッション参加者でフィルタ
        Set<Long> sessionPlayerIds = getSessionAllPlayerIds(sessionDate, organizationId);
        boolean orgScoped = organizationId != null;
        List<MatchPairing> existingPairings = filterPairingsBySession(
                matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber), sessionPlayerIds, orgScoped);
        List<Match> existingMatches = filterMatchesBySession(
                matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber), sessionPlayerIds, orgScoped);

        List<MatchPairing> toDelete = existingPairings.stream()
                .filter(pairing -> {
                    Long p1 = Math.min(pairing.getPlayer1Id(), pairing.getPlayer2Id());
                    Long p2 = Math.max(pairing.getPlayer1Id(), pairing.getPlayer2Id());
                    return existingMatches.stream().noneMatch(m ->
                            (Math.min(m.getPlayer1Id(), m.getPlayer2Id()) == p1) &&
                            (Math.max(m.getPlayer1Id(), m.getPlayer2Id()) == p2));
                })
                .collect(Collectors.toList());

        matchPairingRepository.deleteAll(toDelete);
    }

    /**
     * ペアリングと対応する試合結果を同時に削除（リセット）
     * @return 削除された試合結果の情報を含むDTO（結果がない場合はnull）
     */
    @Transactional
    public MatchPairingDto resetWithResult(Long pairingId) {
        MatchPairing pairing = matchPairingRepository.findById(pairingId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchPairing", pairingId));

        // 対応する試合結果を検索して削除
        Long p1 = Math.min(pairing.getPlayer1Id(), pairing.getPlayer2Id());
        Long p2 = Math.max(pairing.getPlayer1Id(), pairing.getPlayer2Id());
        List<Match> matches = matchRepository.findByMatchDateAndMatchNumber(
                pairing.getSessionDate(), pairing.getMatchNumber());

        // 対応する試合結果を検索
        Match targetMatch = null;
        for (Match match : matches) {
            Long mp1 = Math.min(match.getPlayer1Id(), match.getPlayer2Id());
            Long mp2 = Math.max(match.getPlayer1Id(), match.getPlayer2Id());
            if (mp1.equals(p1) && mp2.equals(p2)) {
                targetMatch = match;
                break;
            }
        }

        // 結果が存在しないペアリングはリセット対象外
        if (targetMatch == null) {
            throw new IllegalStateException("対応する試合結果が見つかりません。結果なしの組み合わせはリセットできません。");
        }

        MatchPairingDto result = convertToDto(pairing);
        result.setHasResult(true);
        result.setMatchId(targetMatch.getId());
        result.setScoreDifference(targetMatch.getScoreDifference());
        Player winner = targetMatch.getWinnerId() != null && targetMatch.getWinnerId() != 0L
                ? playerRepository.findById(targetMatch.getWinnerId()).orElse(null) : null;
        result.setWinnerName(winner != null ? winner.getName() : null);

        // 試合結果とペアリングを削除
        matchRepository.delete(targetMatch);
        matchPairingRepository.delete(pairing);

        return result;
    }

    /**
     * ペアリングIDからセッション日付を取得
     */
    @Transactional(readOnly = true)
    public LocalDate getSessionDateById(Long id) {
        return matchPairingRepository.findById(id)
                .map(MatchPairing::getSessionDate)
                .orElseThrow(() -> new ResourceNotFoundException("MatchPairing", id));
    }

    /**
     * ペアリングIDから所属組織IDを取得（セッション参加者経由で一意特定）。
     *
     * 「両プレイヤーが同一セッションに参加している」セッションのみを候補とし、
     * 一意（候補1件）の場合のみ organizationId を返す。0件または複数件の場合は
     * null を返し、ADMIN/PLAYER のスコープ判定側で ForbiddenException として
     * 安全側拒否される（片方の選手だけが別団体セッションにも参加していて、別団体
     * セッションの organizationId が誤って一致してしまうケースを防ぐ）。
     */
    @Transactional(readOnly = true)
    public Long getOrganizationIdByPairingId(Long pairingId) {
        MatchPairing pairing = matchPairingRepository.findById(pairingId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchPairing", pairingId));

        // ペアリングの日付にあるセッション一覧から、両プレイヤーが参加しているセッションを特定
        List<com.karuta.matchtracker.entity.PracticeSession> sessions =
                practiceSessionRepository.findByDateRange(pairing.getSessionDate(), pairing.getSessionDate());
        List<Long> matchedOrgIds = new ArrayList<>();
        for (com.karuta.matchtracker.entity.PracticeSession session : sessions) {
            java.util.Set<Long> participantIds = practiceParticipantRepository
                    .findBySessionId(session.getId()).stream()
                    .map(com.karuta.matchtracker.entity.PracticeParticipant::getPlayerId)
                    .collect(Collectors.toSet());
            if (participantIds.contains(pairing.getPlayer1Id())
                    && participantIds.contains(pairing.getPlayer2Id())) {
                matchedOrgIds.add(session.getOrganizationId());
            }
        }
        // 両参加セッションが一意特定できる場合のみ organizationId を返す。
        // 0件: ペアリング作成後にセッション参加者が変更されたなどで両者参加セッションなし。
        // 複数件: 同日に複数団体で両者とも参加している → 団体一意特定不能。
        // いずれも ADMIN/PLAYER のスコープ判定で ForbiddenException となる（安全側拒否）。
        if (matchedOrgIds.size() == 1) {
            return matchedOrgIds.get(0);
        }
        return null;
    }

    /**
     * 対戦組み合わせが存在するか確認
     */
    @Transactional(readOnly = true)
    public boolean existsByDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber) {
        return matchPairingRepository.existsBySessionDateAndMatchNumber(sessionDate, matchNumber);
    }

    /**
     * ペアの直近対戦履歴を取得（手動組み合わせ時のリアルタイム表示用）
     */
    @Transactional(readOnly = true)
    public List<AutoMatchingResult.MatchHistory> getPairRecentMatches(
            Long player1Id, Long player2Id, LocalDate sessionDate, Integer matchNumber) {
        List<Long> playerIds = List.of(player1Id, player2Id);
        LocalDate startDate = sessionDate.minusDays(MATCH_HISTORY_DAYS);

        // 過去の組み合わせ履歴
        Map<String, List<LocalDate>> historyMap = getPairingHistory(playerIds, startDate, sessionDate);

        // 同日の他試合（自分以外の試合番号）の組み合わせも含める
        if (matchNumber != null) {
            List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
            for (MatchPairing mp : sameDayPairings) {
                if (!mp.getMatchNumber().equals(matchNumber)) {
                    String pairKey = getPairKey(mp.getPlayer1Id(), mp.getPlayer2Id());
                    historyMap.computeIfAbsent(pairKey, k -> new ArrayList<>());
                    List<LocalDate> dates = historyMap.get(pairKey);
                    if (!dates.contains(sessionDate)) {
                        dates.add(sessionDate);
                    }
                }
            }
        }

        // Matchテーブルからの対戦履歴もマージ
        Map<String, List<LocalDate>> gameHistoryMap = getMatchHistory(playerIds, startDate, sessionDate);
        for (Map.Entry<String, List<LocalDate>> entry : gameHistoryMap.entrySet()) {
            historyMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            for (LocalDate date : entry.getValue()) {
                if (!historyMap.get(entry.getKey()).contains(date)) {
                    historyMap.get(entry.getKey()).add(date);
                }
            }
        }

        return getRecentMatchesForPair(player1Id, player2Id, historyMap, sessionDate);
    }

    /**
     * 自動マッチングを実行
     */
    @Transactional(readOnly = true)
    public AutoMatchingResult autoMatch(AutoMatchingRequest request, Long organizationId) {
        LocalDate sessionDate = request.getSessionDate();
        Integer matchNumber = request.getMatchNumber();
        List<Long> participantIds = loadActiveParticipantIdsForMatch(sessionDate, matchNumber, organizationId);

        log.info("自動マッチング開始: 日付={}, 試合番号={}, 参加者数={}",
                 sessionDate, matchNumber, participantIds.size());

        // ロック済みペアリング（結果入力済み）を特定して除外（組織スコープ付き）
        Set<Long> sessionPlayerIds = getSessionAllPlayerIds(sessionDate, organizationId);
        boolean orgScoped = organizationId != null;
        List<MatchPairing> existingPairings = filterPairingsBySession(
                matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber), sessionPlayerIds, orgScoped);
        List<Match> existingMatches = filterMatchesBySession(
                matchRepository.findByMatchDateAndMatchNumber(sessionDate, matchNumber), sessionPlayerIds, orgScoped);
        Set<Long> lockedPlayerIds = new HashSet<>();
        List<AutoMatchingResult.PairingSuggestion> lockedPairingSuggestions = new ArrayList<>();

        Map<Long, Player> allPlayerMap = new HashMap<>();
        // ロック判定用に全プレイヤー情報を取得
        Set<Long> allPlayerIds = new HashSet<>(participantIds);
        existingPairings.forEach(p -> { allPlayerIds.add(p.getPlayer1Id()); allPlayerIds.add(p.getPlayer2Id()); });
        playerRepository.findAllById(allPlayerIds).forEach(p -> allPlayerMap.put(p.getId(), p));

        for (MatchPairing pairing : existingPairings) {
            Long p1 = Math.min(pairing.getPlayer1Id(), pairing.getPlayer2Id());
            Long p2 = Math.max(pairing.getPlayer1Id(), pairing.getPlayer2Id());
            boolean hasResult = existingMatches.stream().anyMatch(m ->
                    (Math.min(m.getPlayer1Id(), m.getPlayer2Id()) == p1) &&
                    (Math.max(m.getPlayer1Id(), m.getPlayer2Id()) == p2));
            if (hasResult) {
                lockedPlayerIds.add(pairing.getPlayer1Id());
                lockedPlayerIds.add(pairing.getPlayer2Id());
                Player player1 = allPlayerMap.get(pairing.getPlayer1Id());
                Player player2 = allPlayerMap.get(pairing.getPlayer2Id());

                // 対応するMatchから結果情報を取得
                Match matchResult = existingMatches.stream()
                        .filter(m -> (Math.min(m.getPlayer1Id(), m.getPlayer2Id()) == p1) &&
                                     (Math.max(m.getPlayer1Id(), m.getPlayer2Id()) == p2))
                        .findFirst().orElse(null);
                String winnerName = null;
                Integer scoreDiff = null;
                if (matchResult != null) {
                    Player winner = allPlayerMap.get(matchResult.getWinnerId());
                    winnerName = winner != null ? winner.getName() : "Unknown";
                    scoreDiff = matchResult.getScoreDifference();
                }

                lockedPairingSuggestions.add(AutoMatchingResult.PairingSuggestion.builder()
                        .id(pairing.getId())
                        .player1Id(pairing.getPlayer1Id())
                        .player1Name(player1 != null ? player1.getName() : "Unknown")
                        .player2Id(pairing.getPlayer2Id())
                        .player2Name(player2 != null ? player2.getName() : "Unknown")
                        .score(0.0)
                        .recentMatches(Collections.emptyList())
                        .winnerName(winnerName)
                        .scoreDifference(scoreDiff)
                        .build());
            }
        }

        // ロック済みプレイヤーを参加者リストから除外
        participantIds = participantIds.stream()
                .filter(id -> !lockedPlayerIds.contains(id))
                .collect(Collectors.toList());

        log.info("ロック済みペア数={}, ロック除外後の参加者数={}", lockedPairingSuggestions.size(), participantIds.size());

        if (participantIds.isEmpty()) {
            return AutoMatchingResult.builder()
                    .pairings(Collections.emptyList())
                    .waitingPlayers(Collections.emptyList())
                    .lockedPairings(lockedPairingSuggestions)
                    .build();
        }

        // 参加者情報を取得
        Map<Long, Player> playerMap = allPlayerMap;
        List<Long> availableParticipantIds = participantIds.stream()
                .filter(playerMap::containsKey)
                .toList();

        if (availableParticipantIds.isEmpty()) {
            return AutoMatchingResult.builder()
                    .pairings(Collections.emptyList())
                    .waitingPlayers(Collections.emptyList())
                    .lockedPairings(lockedPairingSuggestions)
                    .build();
        }

        // 過去30日の組み合わせ履歴を取得（MatchPairingテーブルから）
        LocalDate startDate = sessionDate.minusDays(MATCH_HISTORY_DAYS);
        Map<String, List<LocalDate>> matchHistoryMap = getPairingHistory(availableParticipantIds, startDate, sessionDate);

        // Matchテーブルからの対戦履歴もマージ
        Map<String, List<LocalDate>> gameHistoryMap = getMatchHistory(availableParticipantIds, startDate, sessionDate);
        for (Map.Entry<String, List<LocalDate>> entry : gameHistoryMap.entrySet()) {
            matchHistoryMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            for (LocalDate date : entry.getValue()) {
                if (!matchHistoryMap.get(entry.getKey()).contains(date)) {
                    matchHistoryMap.get(entry.getKey()).add(date);
                }
            }
        }

        // 表示用履歴マップ: スコア計算用のmatchHistoryMapに同日他試合のペアを加えたもの。
        // スコア計算（calculatePairScore）には matchHistoryMap を使い、同日ペアを混入させない。
        // これにより、後方試合番号の同日ペアが SAME_DAY_PENALTY_SCORE に誤って該当することを防ぐ。
        Map<String, List<LocalDate>> displayHistoryMap = new HashMap<>();
        for (Map.Entry<String, List<LocalDate>> entry : matchHistoryMap.entrySet()) {
            displayHistoryMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        if (matchNumber != null) {
            // 同日他試合のペアも組織スコープでフィルタする。
            // 同日に複数団体のセッションがあると、別団体の同日ペアが displayHistoryMap に
            // 入り、当該団体の組み合わせ画面に他団体ペアの履歴が表示されてしまう。
            List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
            if (orgScoped) {
                sameDayPairings = filterPairingsBySession(sameDayPairings, sessionPlayerIds, true);
            }
            for (MatchPairing mp : sameDayPairings) {
                if (!mp.getMatchNumber().equals(matchNumber)) {
                    String pairKey = getPairKey(mp.getPlayer1Id(), mp.getPlayer2Id());
                    displayHistoryMap.computeIfAbsent(pairKey, k -> new ArrayList<>());
                    List<LocalDate> dates = displayHistoryMap.get(pairKey);
                    if (!dates.contains(sessionDate)) {
                        dates.add(sessionDate);
                    }
                }
            }
        }

        // 同日の既存組み合わせを取得（除外用）— MatchPairingテーブルから
        // 組織スコープを引き継ぎ、別団体の前試合ペアが当該団体の候補から除外されないようにする
        Set<String> todayMatches = getTodayPairings(sessionDate, matchNumber, sessionPlayerIds, orgScoped);

        // スコアを計算して最適なペアリングを生成
        List<AutoMatchingResult.PairingSuggestion> pairings = new ArrayList<>();
        Set<Long> paired = new HashSet<>();
        List<Long> shuffled = new ArrayList<>(availableParticipantIds);
        Collections.shuffle(shuffled);

        while (paired.size() + 1 < shuffled.size()) {
            Long bestPlayer1 = null;
            Long bestPlayer2 = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (Long p1 : shuffled) {
                if (paired.contains(p1)) continue;

                for (Long p2 : shuffled) {
                    if (paired.contains(p2) || p1.equals(p2)) continue;

                    String pairKey = getPairKey(p1, p2);
                    if (todayMatches.contains(pairKey)) continue;

                    double score = calculatePairScore(p1, p2, matchHistoryMap, sessionDate);

                    if (score > bestScore) {
                        bestScore = score;
                        bestPlayer1 = p1;
                        bestPlayer2 = p2;
                    }
                }
            }

            if (bestPlayer1 != null && bestPlayer2 != null) {
                Player player1 = playerMap.get(bestPlayer1);
                Player player2 = playerMap.get(bestPlayer2);

                List<AutoMatchingResult.MatchHistory> recentMatches =
                    getRecentMatchesForPair(bestPlayer1, bestPlayer2, displayHistoryMap, sessionDate);

                pairings.add(AutoMatchingResult.PairingSuggestion.builder()
                        .player1Id(bestPlayer1)
                        .player1Name(player1.getName())
                        .player2Id(bestPlayer2)
                        .player2Name(player2.getName())
                        .score(bestScore)
                        .recentMatches(recentMatches)
                        .build());

                paired.add(bestPlayer1);
                paired.add(bestPlayer2);
            } else {
                break;
            }
        }

        // 待機者リスト
        List<AutoMatchingResult.PlayerInfo> waitingPlayers = shuffled.stream()
                .filter(id -> !paired.contains(id))
                .map(id -> {
                    Player player = playerMap.get(id);
                    return AutoMatchingResult.PlayerInfo.builder()
                            .id(id)
                            .name(player.getName())
                            .build();
                })
                .collect(Collectors.toList());

        log.info("自動マッチング完了: ペア数={}, 待機者数={}, ロック済みペア数={}",
                 pairings.size(), waitingPlayers.size(), lockedPairingSuggestions.size());

        return AutoMatchingResult.builder()
                .pairings(pairings)
                .waitingPlayers(waitingPlayers)
                .lockedPairings(lockedPairingSuggestions)
                .build();
    }

    /**
     * 指定日・組織のセッション全参加者IDを取得（組織スコープ用）
     * organizationId が null の場合は組織フィルタなし（SUPER_ADMIN向け）
     */
    private Set<Long> getSessionAllPlayerIds(LocalDate sessionDate, Long organizationId) {
        Optional<com.karuta.matchtracker.entity.PracticeSession> sessionOpt;
        if (organizationId != null) {
            sessionOpt = practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId);
        } else {
            sessionOpt = practiceSessionRepository.findBySessionDate(sessionDate);
        }
        return sessionOpt
                .map(session -> practiceParticipantRepository.findBySessionId(session.getId()).stream()
                        .map(PracticeParticipant::getPlayerId)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
    }

    /**
     * ペアリング/マッチをセッション参加者でフィルタ（組織スコープ）
     *
     * orgScoped=true (ADMIN 等の組織スコープ実行) では「両方の選手が当該団体の
     * セッション参加者である」場合のみ通過させる。同じ選手が複数団体に所属
     * していたり、別団体のペアリングに対象団体の選手が片方だけ含まれていた
     * 場合に、別団体のペアリングが ADMIN 画面に混入することを防ぐ。
     *
     * orgScoped=false (SUPER_ADMIN 経路で組織非限定) は従来通り片方一致でも
     * 通過させ、後方互換の挙動を維持する。
     */
    private List<MatchPairing> filterPairingsBySession(List<MatchPairing> pairings, Set<Long> sessionPlayerIds,
                                                        boolean orgScoped) {
        // 組織スコープ時に参加者0人なら空リストを返す（無フィルタにフォールバックしない）
        if (orgScoped && sessionPlayerIds.isEmpty()) return Collections.emptyList();
        if (sessionPlayerIds.isEmpty()) return pairings;
        if (orgScoped) {
            return pairings.stream()
                    .filter(p -> sessionPlayerIds.contains(p.getPlayer1Id())
                            && sessionPlayerIds.contains(p.getPlayer2Id()))
                    .collect(Collectors.toList());
        }
        return pairings.stream()
                .filter(p -> sessionPlayerIds.contains(p.getPlayer1Id()) || sessionPlayerIds.contains(p.getPlayer2Id()))
                .collect(Collectors.toList());
    }

    private List<Match> filterMatchesBySession(List<Match> matches, Set<Long> sessionPlayerIds,
                                                boolean orgScoped) {
        if (orgScoped && sessionPlayerIds.isEmpty()) return Collections.emptyList();
        if (sessionPlayerIds.isEmpty()) return matches;
        if (orgScoped) {
            return matches.stream()
                    .filter(m -> sessionPlayerIds.contains(m.getPlayer1Id())
                            && sessionPlayerIds.contains(m.getPlayer2Id()))
                    .collect(Collectors.toList());
        }
        return matches.stream()
                .filter(m -> sessionPlayerIds.contains(m.getPlayer1Id()) || sessionPlayerIds.contains(m.getPlayer2Id()))
                .collect(Collectors.toList());
    }

    /**
     * 指定日・試合番号のアクティブ参加者IDを取得。
     *
     * 団体の運用設定により対象ステータスを切り替える:
     *  - 抽選あり運用 (MONTHLY + 締め切りあり): WON のみ
     *  - 抽選なし運用 (SAME_DAY もしくは MONTHLY + 締め切りなしモード): PENDING + WON
     *
     * 抽選あり運用で PENDING を含めると抽選前の参加希望者まで自動マッチング対象になり、
     * 抽選結果をバイパスしてしまうため、組織設定に応じた判定が必須。
     * WAITLISTED / OFFERED / DECLINED / CANCELLED / WAITLIST_DECLINED は常に除外する。
     */
    private List<Long> loadActiveParticipantIdsForMatch(LocalDate sessionDate, Integer matchNumber,
                                                         Long organizationId) {
        if (sessionDate == null || matchNumber == null) {
            log.warn("アクティブ参加者取得をスキップ: sessionDateまたはmatchNumberがnull (sessionDate={}, matchNumber={})",
                    sessionDate, matchNumber);
            return Collections.emptyList();
        }

        // 先に対象セッションを取得する。
        // organizationId が指定されている場合は組織スコープでセッションを取得する。
        // 同日に複数団体の練習セッションがある場合、findBySessionDate(date) のみだと
        // 別団体のセッションを拾う / 単一結果前提のクエリで例外になる可能性があるため。
        // organizationId == null は SUPER_ADMIN 経路で組織非限定の取得を許可する。
        Optional<com.karuta.matchtracker.entity.PracticeSession> sessionOpt = organizationId != null
                ? practiceSessionRepository.findBySessionDateAndOrganizationId(sessionDate, organizationId)
                : practiceSessionRepository.findBySessionDate(sessionDate);

        if (sessionOpt.isEmpty()) {
            log.info("セッション未登録のためアクティブ参加者なし: sessionDate={}, matchNumber={}, organizationId={}",
                    sessionDate, matchNumber, organizationId);
            return Collections.emptyList();
        }

        com.karuta.matchtracker.entity.PracticeSession session = sessionOpt.get();
        // 抽選なし運用判定は対象セッションの団体に対して行う。
        // SUPER_ADMIN 経路では呼び出し側の organizationId が null になるため、
        // セッションの組織IDで判定しないと SAME_DAY / 締め切りなし団体でも PENDING が誤って除外される。
        Long effectiveOrganizationId = organizationId != null ? organizationId : session.getOrganizationId();
        List<ParticipantStatus> targetStatuses = lotteryDeadlineHelper.isLotteryDisabled(effectiveOrganizationId)
                ? List.of(ParticipantStatus.PENDING, ParticipantStatus.WON)
                : List.of(ParticipantStatus.WON);

        List<Long> activeParticipantIds = practiceParticipantRepository
                .findBySessionIdAndMatchNumberAndStatusIn(
                        session.getId(),
                        matchNumber,
                        targetStatuses)
                .stream()
                .map(PracticeParticipant::getPlayerId)
                .distinct()
                .toList();
        if (activeParticipantIds.isEmpty()) {
            log.info("アクティブ参加者なし: sessionId={}, sessionDate={}, matchNumber={}, statuses={}",
                    session.getId(), sessionDate, matchNumber, targetStatuses);
        }
        return activeParticipantIds;
    }

    private Map<String, List<LocalDate>> getMatchHistory(List<Long> participantIds,
                                                          LocalDate startDate, LocalDate endDate) {
        // クエリで過去30日の対戦履歴を取得
        List<Object[]> results = matchRepository.findRecentMatchHistory(participantIds, startDate, endDate);

        Map<String, List<LocalDate>> historyMap = new HashMap<>();
        for (Object[] row : results) {
            LocalDate matchDate = (LocalDate) row[0];
            Long playerA = (Long) row[1];
            Long playerB = (Long) row[2];
            String pairKey = getPairKey(playerA, playerB);

            historyMap.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(matchDate);
        }

        return historyMap;
    }

    /**
     * 同日の既存対戦を取得（Matchテーブルから）
     *
     * orgScoped=true の場合は両方の選手が当該団体セッション参加者である対戦のみ
     * 返す。同日に複数団体のセッションがあり共有選手がいる場合、別団体の前試合
     * 対戦が含まれて自動マッチング候補から誤って除外されることを防ぐ。
     */
    private Set<String> getTodayMatches(LocalDate sessionDate, Integer currentMatchNumber,
                                         Set<Long> sessionPlayerIds, boolean orgScoped) {
        List<Object[]> results = matchRepository.findTodayMatches(sessionDate, currentMatchNumber);

        return results.stream()
                .filter(row -> {
                    if (!orgScoped) return true;
                    Long playerA = (Long) row[0];
                    Long playerB = (Long) row[1];
                    return sessionPlayerIds.contains(playerA) && sessionPlayerIds.contains(playerB);
                })
                .map(row -> {
                    Long playerA = (Long) row[0];
                    Long playerB = (Long) row[1];
                    return getPairKey(playerA, playerB);
                })
                .collect(Collectors.toSet());
    }

    /**
     * 同日の既存組み合わせを取得（MatchPairingテーブルから、同日の他の試合番号）
     *
     * orgScoped=true の場合は両方の選手が当該団体セッション参加者であるペアのみ
     * 対象とする。これにより自動マッチング側で別団体のペアが除外候補に混入する
     * ことを防ぐ（filterPairingsBySession と同じ AND 条件）。
     */
    private Set<String> getTodayPairings(LocalDate sessionDate, Integer currentMatchNumber,
                                          Set<Long> sessionPlayerIds, boolean orgScoped) {
        // MatchPairingテーブルから同日の他の試合番号の組み合わせを取得
        List<MatchPairing> todayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
        if (orgScoped) {
            todayPairings = filterPairingsBySession(todayPairings, sessionPlayerIds, true);
        }

        Set<String> pairKeys = todayPairings.stream()
                .filter(p -> p.getMatchNumber() < currentMatchNumber)
                .map(p -> getPairKey(p.getPlayer1Id(), p.getPlayer2Id()))
                .collect(Collectors.toSet());

        // Matchテーブルからも同日の対戦を取得してマージ（組織スコープも引き継ぐ）
        pairKeys.addAll(getTodayMatches(sessionDate, currentMatchNumber, sessionPlayerIds, orgScoped));

        return pairKeys;
    }

    /**
     * ペアのスコアを計算
     */
    private double calculatePairScore(Long player1, Long player2,
                                      Map<String, List<LocalDate>> matchHistoryMap,
                                      LocalDate sessionDate) {
        String pairKey = getPairKey(player1, player2);
        List<LocalDate> history = matchHistoryMap.getOrDefault(pairKey, Collections.emptyList());

        if (history.isEmpty()) {
            // 初対戦は高スコア
            return 0;
        }

        // 最後の対戦からの日数に基づいてスコアを計算（日数が多いほど高スコア）
        LocalDate lastMatch = history.stream().max(LocalDate::compareTo).orElse(null);
        if (lastMatch == null) {
            return 0;
        }

        long daysAgo = ChronoUnit.DAYS.between(lastMatch, sessionDate);

        // 同日または未来の日付の場合は最低スコアを返す
        if (daysAgo <= 0) {
            return SAME_DAY_PENALTY_SCORE;
        }

        return -(INTERVAL_BASE_SCORE / daysAgo);
    }

    /**
     * ペアの最近の対戦履歴を取得
     */
    private List<AutoMatchingResult.MatchHistory> getRecentMatchesForPair(Long player1, Long player2,
                                                                           Map<String, List<LocalDate>> matchHistoryMap,
                                                                           LocalDate sessionDate) {
        String pairKey = getPairKey(player1, player2);
        List<LocalDate> dates = matchHistoryMap.getOrDefault(pairKey, Collections.emptyList());

        return dates.stream()
                .sorted(Comparator.reverseOrder())
                .limit(5)
                .map(matchDate -> {
                    long daysAgo = ChronoUnit.DAYS.between(matchDate, sessionDate);
                    return AutoMatchingResult.MatchHistory.builder()
                            .matchDate(matchDate)
                            .daysAgo((int) daysAgo)
                            .matchNumber(null) // 試合番号は取得していないのでnull
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * ペアキーを生成（player1 < player2の順で正規化）
     */
    private String getPairKey(Long player1, Long player2) {
        long smaller = Math.min(player1, player2);
        long larger = Math.max(player1, player2);
        return smaller + "-" + larger;
    }

    /**
     * DTOリストに直近対戦情報を付加（MatchPairingテーブルから組み合わせ履歴を取得）
     * @param currentMatchNumber 現在の試合番号（nullの場合は同日の全組み合わせを含める、指定時はそれより前の試合のみ）
     */
    private void enrichWithRecentMatches(List<MatchPairingDto> dtos, LocalDate sessionDate, Integer currentMatchNumber) {
        if (dtos.isEmpty()) return;

        // 全ペアの選手IDを収集
        List<Long> playerIds = dtos.stream()
                .flatMap(d -> Stream.of(d.getPlayer1Id(), d.getPlayer2Id()))
                .distinct()
                .collect(Collectors.toList());

        // 過去30日の組み合わせ履歴をMatchPairingテーブルから取得（当日より前の日付）
        LocalDate startDate = sessionDate.minusDays(MATCH_HISTORY_DAYS);
        Map<String, List<LocalDate>> pairingHistoryMap = getPairingHistory(playerIds, startDate, sessionDate);

        // 同日の他試合（自分以外の試合番号）の組み合わせ履歴も取得
        if (currentMatchNumber != null) {
            List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
            for (MatchPairing mp : sameDayPairings) {
                if (!mp.getMatchNumber().equals(currentMatchNumber)) {
                    String pairKey = getPairKey(mp.getPlayer1Id(), mp.getPlayer2Id());
                    pairingHistoryMap.computeIfAbsent(pairKey, k -> new ArrayList<>());
                    List<LocalDate> dates = pairingHistoryMap.get(pairKey);
                    if (!dates.contains(sessionDate)) {
                        dates.add(sessionDate);
                    }
                }
            }
        }

        // 各ペアにrecentMatchesを設定
        for (MatchPairingDto dto : dtos) {
            dto.setRecentMatches(
                    getRecentMatchesForPair(dto.getPlayer1Id(), dto.getPlayer2Id(), pairingHistoryMap, sessionDate)
            );
        }
    }

    /**
     * DTOリストに対応する試合結果情報（ロック状態）を付加
     */
    private void enrichWithMatchResults(List<MatchPairingDto> dtos, LocalDate sessionDate) {
        if (dtos.isEmpty()) return;

        // 該当日の全試合番号を収集して一括取得
        Set<Integer> matchNumbers = dtos.stream()
                .map(MatchPairingDto::getMatchNumber)
                .collect(Collectors.toSet());

        // 全matchNumberの試合結果を一括取得（N+1回避）
        Map<String, Match> matchMap = new HashMap<>();
        for (Integer mn : matchNumbers) {
            List<Match> matches = matchRepository.findByMatchDateAndMatchNumber(sessionDate, mn);
            for (Match m : matches) {
                Long p1 = Math.min(m.getPlayer1Id(), m.getPlayer2Id());
                Long p2 = Math.max(m.getPlayer1Id(), m.getPlayer2Id());
                matchMap.put(mn + "-" + p1 + "-" + p2, m);
            }
        }

        // 勝者名取得用にプレイヤー名を一括取得
        Set<Long> winnerIds = matchMap.values().stream()
                .map(Match::getWinnerId)
                .filter(id -> id != null && id != 0L)
                .collect(Collectors.toSet());
        Map<Long, String> winnerNames = new HashMap<>();
        if (!winnerIds.isEmpty()) {
            playerRepository.findAllById(winnerIds).forEach(p -> winnerNames.put(p.getId(), p.getName()));
        }

        // 各DTOにマッチ結果を付加
        for (MatchPairingDto dto : dtos) {
            Long p1 = Math.min(dto.getPlayer1Id(), dto.getPlayer2Id());
            Long p2 = Math.max(dto.getPlayer1Id(), dto.getPlayer2Id());
            String key = dto.getMatchNumber() + "-" + p1 + "-" + p2;
            Match match = matchMap.get(key);
            if (match != null) {
                dto.setHasResult(true);
                dto.setMatchId(match.getId());
                dto.setScoreDifference(match.getScoreDifference());
                dto.setWinnerName(winnerNames.getOrDefault(match.getWinnerId(), null));
            }
        }
    }

    /**
     * MatchPairingテーブルから過去の組み合わせ履歴を取得
     */
    private Map<String, List<LocalDate>> getPairingHistory(List<Long> participantIds,
                                                            LocalDate startDate, LocalDate endDate) {
        List<Object[]> results = matchPairingRepository.findRecentPairingHistory(participantIds, startDate, endDate);

        Map<String, List<LocalDate>> historyMap = new HashMap<>();
        for (Object[] row : results) {
            LocalDate pairingDate = (LocalDate) row[0];
            Long playerA = (Long) row[1];
            Long playerB = (Long) row[2];
            String pairKey = getPairKey(playerA, playerB);
            historyMap.computeIfAbsent(pairKey, k -> new ArrayList<>()).add(pairingDate);
        }

        return historyMap;
    }

    /**
     * ペアリングリストから全選手名を一括取得
     */
    private Map<Long, String> collectPlayerNames(List<MatchPairing> pairings) {
        List<Long> allIds = pairings.stream()
                .flatMap(p -> Stream.of(p.getPlayer1Id(), p.getPlayer2Id(), p.getCreatedBy()))
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> names = new HashMap<>();
        playerRepository.findAllById(allIds).forEach(p -> names.put(p.getId(), p.getName()));
        return names;
    }

    /**
     * エンティティをDTOに変換（キャッシュ済み選手名を使用）
     */
    private MatchPairingDto convertToDtoWithCache(MatchPairing pairing, Map<Long, String> playerNames) {
        return MatchPairingDto.builder()
                .id(pairing.getId())
                .sessionDate(pairing.getSessionDate())
                .matchNumber(pairing.getMatchNumber())
                .player1Id(pairing.getPlayer1Id())
                .player1Name(playerNames.getOrDefault(pairing.getPlayer1Id(), "Unknown"))
                .player2Id(pairing.getPlayer2Id())
                .player2Name(playerNames.getOrDefault(pairing.getPlayer2Id(), "Unknown"))
                .createdBy(pairing.getCreatedBy())
                .createdByName(playerNames.getOrDefault(pairing.getCreatedBy(), "Unknown"))
                .createdAt(pairing.getCreatedAt())
                .updatedAt(pairing.getUpdatedAt())
                .build();
    }

    /**
     * エンティティをDTOに変換（選手名を一括取得して変換）
     */
    private MatchPairingDto convertToDto(MatchPairing pairing) {
        Map<Long, String> playerNames = collectPlayerNames(List.of(pairing));
        return convertToDtoWithCache(pairing, playerNames);
    }
}
