package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.*;
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
        List<MatchPairing> pairings = matchPairingRepository.findBySessionDateOrderByMatchNumber(sessionDate);
        return pairings.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * 指定日・試合番号の対戦組み合わせを取得
     */
    @Transactional(readOnly = true)
    public List<MatchPairingDto> getByDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber) {
        List<MatchPairing> pairings = matchPairingRepository.findBySessionDateAndMatchNumber(sessionDate, matchNumber);
        return pairings.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
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

        // 過去30日の対戦履歴を取得
        LocalDate startDate = sessionDate.minusDays(30);
        Map<String, List<LocalDate>> matchHistoryMap = getMatchHistory(participantIds, startDate, sessionDate);

        // 同日の既存対戦を取得（除外用）
        Set<String> todayMatches = getTodayMatches(sessionDate, matchNumber);

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
     * 同日の既存対戦を取得
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
     * エンティティをDTOに変換
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
