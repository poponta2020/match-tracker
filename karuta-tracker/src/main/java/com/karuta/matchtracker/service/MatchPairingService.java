package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchPairing;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
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

    private final MatchPairingRepository matchPairingRepository;
    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;

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
                                              List<MatchPairingCreateRequest> requests, Long createdBy) {
        // 既存の組み合わせを削除
        matchPairingRepository.deleteBySessionDateAndMatchNumber(sessionDate, matchNumber);

        List<MatchPairing> pairings = requests.stream()
                .map(request -> MatchPairing.builder()
                        .sessionDate(sessionDate)
                        .matchNumber(matchNumber)
                        .player1Id(request.getPlayer1Id())
                        .player2Id(request.getPlayer2Id())
                        .createdBy(createdBy)
                        .build())
                .collect(Collectors.toList());

        List<MatchPairing> saved = matchPairingRepository.saveAll(pairings);
        return saved.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 対戦組み合わせの選手を更新（旧ペアの試合結果も削除）
     */
    @Transactional
    public MatchPairingDto updatePlayer(Long id, Long newPlayerId, String side, Long updatedBy) {
        MatchPairing pairing = matchPairingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ペアリングが見つかりません: " + id));

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
     * 指定日・試合番号の対戦組み合わせを削除
     */
    @Transactional
    public void deleteByDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber) {
        matchPairingRepository.deleteBySessionDateAndMatchNumber(sessionDate, matchNumber);
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
        LocalDate startDate = sessionDate.minusDays(90);

        // 過去の組み合わせ履歴
        Map<String, List<LocalDate>> historyMap = getPairingHistory(playerIds, startDate, sessionDate);

        // 同日の前の試合の組み合わせも含める
        if (matchNumber != null && matchNumber > 1) {
            List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
            for (MatchPairing mp : sameDayPairings) {
                if (mp.getMatchNumber() < matchNumber) {
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
    public AutoMatchingResult autoMatch(AutoMatchingRequest request) {
        LocalDate sessionDate = request.getSessionDate();
        Integer matchNumber = request.getMatchNumber();
        List<Long> participantIds = request.getParticipantIds();

        log.info("自動マッチング開始: 日付={}, 試合番号={}, 参加者数={}",
                 sessionDate, matchNumber, participantIds.size());

        // 参加者情報を取得
        Map<Long, Player> playerMap = playerRepository.findAllById(participantIds).stream()
                .collect(Collectors.toMap(Player::getId, p -> p));

        // 過去30日の組み合わせ履歴を取得（MatchPairingテーブルから）
        LocalDate startDate = sessionDate.minusDays(30);
        Map<String, List<LocalDate>> matchHistoryMap = getPairingHistory(participantIds, startDate, sessionDate);

        // Matchテーブルからの対戦履歴もマージ
        Map<String, List<LocalDate>> gameHistoryMap = getMatchHistory(participantIds, startDate, sessionDate);
        for (Map.Entry<String, List<LocalDate>> entry : gameHistoryMap.entrySet()) {
            matchHistoryMap.computeIfAbsent(entry.getKey(), k -> new ArrayList<>());
            for (LocalDate date : entry.getValue()) {
                if (!matchHistoryMap.get(entry.getKey()).contains(date)) {
                    matchHistoryMap.get(entry.getKey()).add(date);
                }
            }
        }

        // 同日の前の試合の組み合わせ履歴もmatchHistoryMapに追加（recentMatches表示用）
        if (matchNumber > 1) {
            List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
            for (MatchPairing mp : sameDayPairings) {
                if (mp.getMatchNumber() < matchNumber) {
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
        List<Long> shuffled = new ArrayList<>(participantIds);
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

        log.info("自動マッチング完了: ペア数={}, 待機者数={}", pairings.size(), waitingPlayers.size());

        return AutoMatchingResult.builder()
                .pairings(pairings)
                .waitingPlayers(waitingPlayers)
                .build();
    }

    /**
     * 過去の対戦履歴を取得
     */
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
            return -1000.0;  // 同日対戦は避けるべきなので非常に低いスコア
        }

        // 1日前: -100点, 2日前: -50点, 7日前: -14点, 14日前: -7点, 30日前: -3点
        return -(100.0 / daysAgo);
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

        // 過去90日の組み合わせ履歴をMatchPairingテーブルから取得（当日より前の日付）
        LocalDate startDate = sessionDate.minusDays(90);
        Map<String, List<LocalDate>> pairingHistoryMap = getPairingHistory(playerIds, startDate, sessionDate);

        // 同日の組み合わせ履歴も取得（currentMatchNumberより前の試合番号のみ）
        if (currentMatchNumber != null && currentMatchNumber > 1) {
            List<MatchPairing> sameDayPairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
            for (MatchPairing mp : sameDayPairings) {
                if (mp.getMatchNumber() < currentMatchNumber) {
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
     * エンティティをDTOに変換（個別取得版・後方互換）
     */
    private MatchPairingDto convertToDto(MatchPairing pairing) {
        Player player1 = playerRepository.findById(pairing.getPlayer1Id()).orElse(null);
        Player player2 = playerRepository.findById(pairing.getPlayer2Id()).orElse(null);
        Player creator = playerRepository.findById(pairing.getCreatedBy()).orElse(null);

        return MatchPairingDto.builder()
                .id(pairing.getId())
                .sessionDate(pairing.getSessionDate())
                .matchNumber(pairing.getMatchNumber())
                .player1Id(pairing.getPlayer1Id())
                .player1Name(player1 != null ? player1.getName() : "Unknown")
                .player2Id(pairing.getPlayer2Id())
                .player2Name(player2 != null ? player2.getName() : "Unknown")
                .createdBy(pairing.getCreatedBy())
                .createdByName(creator != null ? creator.getName() : "Unknown")
                .createdAt(pairing.getCreatedAt())
                .updatedAt(pairing.getUpdatedAt())
                .build();
    }
}
