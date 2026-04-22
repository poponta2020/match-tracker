package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchPairing;
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

    private final MatchPairingRepository matchPairingRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;

    /**
     * 指定日の対戦組み合わせを取得
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDate(LocalDate sessionDate) {
        return getByDate(sessionDate, false);
    }

    /**
     * 指定日の対戦組み合わせを取得（軽量オプション付き）
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDate(LocalDate sessionDate, boolean light) {
        List<MatchPairing> pairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
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
        List<MatchPairing> pairings = matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber);
        Map<Long, String> playerNames = collectPlayerNames(pairings);
        List<MatchPairingDto> dtos = pairings.stream()
                .map(p -> convertToDtoWithCache(p, playerNames))
                .collect(Collectors.toList());
        enrichWithRecentMatches(dtos, sessionDate, matchNumber);
        enrichWithMatchResults(dtos, sessionDate);
        return dtos;
    }

    /**
     * 対戦組み合わせを作成
     */
    @Transactional
    public MatchPairingDto create(MatchPairingCreateRequest request, Long createdBy) {
        // バリデーション
        if (request.getPlayer1Id().equals(request.getPlayer2Id())) {
            throw new IllegalArgumentException("同じ選手を対戦相手に設定できません");
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
        if (!"player1".equals(side) && !"player2".equals(side)) {
            throw new IllegalArgumentException("sideは'player1'または'player2'を指定してください");
        }

        MatchPairing pairing = matchPairingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MatchPairing", id));

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
     * ペアリングIDから所属組織IDを取得（セッション参加者経由で一意特定）
     * ペアリングのプレイヤーが参加しているセッションの組織IDを返す
     */
    @Transactional(readOnly = true)
    public Long getOrganizationIdByPairingId(Long pairingId) {
        MatchPairing pairing = matchPairingRepository.findById(pairingId)
                .orElseThrow(() -> new ResourceNotFoundException("MatchPairing", pairingId));

        // ペアリングの日付にあるセッション一覧から、プレイヤーが参加しているセッションを特定
        List<com.karuta.matchtracker.entity.PracticeSession> sessions =
                practiceSessionRepository.findByDateRange(pairing.getSessionDate(), pairing.getSessionDate());
        for (com.karuta.matchtracker.entity.PracticeSession session : sessions) {
            boolean hasPlayer = practiceParticipantRepository.findBySessionId(session.getId()).stream()
                    .anyMatch(pp -> pp.getPlayerId().equals(pairing.getPlayer1Id())
                            || pp.getPlayerId().equals(pairing.getPlayer2Id()));
            if (hasPlayer) {
                return session.getOrganizationId();
            }
        }
        // セッションが見つからない場合はnull（SUPER_ADMINのみアクセス可）
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
        List<Long> participantIds = loadWonParticipantIdsForMatch(sessionDate, matchNumber);

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

        // 同日の他試合（自分以外の試合番号）の組み合わせ履歴もmatchHistoryMapに追加（recentMatches表示用）
        if (matchNumber != null) {
            List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
            for (MatchPairing mp : sameDayPairings) {
                if (!mp.getMatchNumber().equals(matchNumber)) {
                    String pairKey = getPairKey(mp.getPlayer1Id(), mp.getPlayer2Id());
                    matchHistoryMap.computeIfAbsent(pairKey, k -> new ArrayList<>());
                    List<LocalDate> dates = matchHistoryMap.get(pairKey);
                    if (!dates.contains(sessionDate)) {
                        dates.add(sessionDate);
                    }
                }
            }
        }

        // 同日の既存組み合わせを取得（除外用）— MatchPairingテーブルから
        Set<String> todayMatches = getTodayPairings(sessionDate, matchNumber);

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
                    getRecentMatchesForPair(bestPlayer1, bestPlayer2, matchHistoryMap, sessionDate);

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
     */
    private List<MatchPairing> filterPairingsBySession(List<MatchPairing> pairings, Set<Long> sessionPlayerIds,
                                                        boolean orgScoped) {
        // 組織スコープ時に参加者0人なら空リストを返す（無フィルタにフォールバックしない）
        if (orgScoped && sessionPlayerIds.isEmpty()) return Collections.emptyList();
        if (sessionPlayerIds.isEmpty()) return pairings;
        return pairings.stream()
                .filter(p -> sessionPlayerIds.contains(p.getPlayer1Id()) || sessionPlayerIds.contains(p.getPlayer2Id()))
                .collect(Collectors.toList());
    }

    private List<Match> filterMatchesBySession(List<Match> matches, Set<Long> sessionPlayerIds,
                                                boolean orgScoped) {
        if (orgScoped && sessionPlayerIds.isEmpty()) return Collections.emptyList();
        if (sessionPlayerIds.isEmpty()) return matches;
        return matches.stream()
                .filter(m -> sessionPlayerIds.contains(m.getPlayer1Id()) || sessionPlayerIds.contains(m.getPlayer2Id()))
                .collect(Collectors.toList());
    }

    /**
     * 指定日・試合番号のWON参加者IDを取得
     */
    private List<Long> loadWonParticipantIdsForMatch(LocalDate sessionDate, Integer matchNumber) {
        if (sessionDate == null || matchNumber == null) {
            log.warn("WON参加者取得をスキップ: sessionDateまたはmatchNumberがnull (sessionDate={}, matchNumber={})",
                    sessionDate, matchNumber);
            return Collections.emptyList();
        }

        return practiceSessionRepository.findBySessionDate(sessionDate)
                .map(session -> {
                    List<Long> wonParticipantIds = practiceParticipantRepository
                            .findBySessionIdAndMatchNumberAndStatus(
                                    session.getId(),
                                    matchNumber,
                                    ParticipantStatus.WON)
                            .stream()
                            .map(PracticeParticipant::getPlayerId)
                            .distinct()
                            .toList();
                    if (wonParticipantIds.isEmpty()) {
                        log.info("WON参加者なし: sessionId={}, sessionDate={}, matchNumber={}",
                                session.getId(), sessionDate, matchNumber);
                    }
                    return wonParticipantIds;
                })
                .orElseGet(() -> {
                    log.info("セッション未登録のためWON参加者なし: sessionDate={}, matchNumber={}",
                            sessionDate, matchNumber);
                    return Collections.emptyList();
                });
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
     */
    private Set<String> getTodayMatches(LocalDate sessionDate, Integer currentMatchNumber) {
        List<Object[]> results = matchRepository.findTodayMatches(sessionDate, currentMatchNumber);

        return results.stream()
                .map(row -> {
                    Long playerA = (Long) row[0];
                    Long playerB = (Long) row[1];
                    return getPairKey(playerA, playerB);
                })
                .collect(Collectors.toSet());
    }

    /**
     * 同日の既存組み合わせを取得（MatchPairingテーブルから、同日の他の試合番号）
     */
    private Set<String> getTodayPairings(LocalDate sessionDate, Integer currentMatchNumber) {
        // MatchPairingテーブルから同日の他の試合番号の組み合わせを取得
        List<MatchPairing> todayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);

        Set<String> pairKeys = todayPairings.stream()
                .filter(p -> p.getMatchNumber() < currentMatchNumber)
                .map(p -> getPairKey(p.getPlayer1Id(), p.getPlayer2Id()))
                .collect(Collectors.toSet());

        // Matchテーブルからも同日の対戦を取得してマージ
        pairKeys.addAll(getTodayMatches(sessionDate, currentMatchNumber));

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
