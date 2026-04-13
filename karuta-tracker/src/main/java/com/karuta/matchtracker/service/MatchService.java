package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MatchCreateRequest;
import com.karuta.matchtracker.dto.MatchDto;
import com.karuta.matchtracker.dto.MatchSimpleCreateRequest;
import com.karuta.matchtracker.dto.MatchStatisticsDto;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.MatchPersonalNote;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateMatchException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.entity.MatchPairing;
import com.karuta.matchtracker.repository.MatchPairingRepository;
import com.karuta.matchtracker.repository.MatchPersonalNoteRepository;
import com.karuta.matchtracker.repository.MentorRelationshipRepository;
import com.karuta.matchtracker.entity.MentorRelationship;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 試合結果管理サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MatchService {

    static final String RESULT_WIN = "勝ち";
    static final String RESULT_LOSE = "負け";
    static final String RESULT_DRAW = "引き分け";

    private final MatchRepository matchRepository;
    private final MatchPairingRepository matchPairingRepository;
    private final PlayerRepository playerRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final MatchPersonalNoteRepository matchPersonalNoteRepository;
    private final MentorRelationshipRepository mentorRelationshipRepository;
    private final LineNotificationService lineNotificationService;

    /**
     * IDで試合結果を取得
     */
    public MatchDto findById(Long id, Long currentPlayerId) {
        return findById(id, currentPlayerId, null);
    }

    public MatchDto findById(Long id, Long currentPlayerId, Long viewedPlayerId) {
        log.debug("Finding match by id: {}", id);
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match", id));

        // viewedPlayerIdが指定されている場合、その試合の参加者であることを検証
        if (viewedPlayerId != null && !viewedPlayerId.equals(currentPlayerId)) {
            if (!viewedPlayerId.equals(match.getPlayer1Id()) && !viewedPlayerId.equals(match.getPlayer2Id())) {
                throw new IllegalArgumentException("指定されたplayerIdはこの試合の参加者ではありません");
            }
        }

        MatchDto dto = enrichMatchWithPlayerNames(match, currentPlayerId);
        List<MatchDto> enriched = enrichDtosWithPersonalNotes(List.of(dto), currentPlayerId, viewedPlayerId);
        return enriched.get(0);
    }

    /**
     * 選手ID・日付・試合番号で試合結果を取得
     */
    public MatchDto findByPlayerDateAndMatchNumber(Long playerId, LocalDate matchDate, Integer matchNumber, Long currentPlayerId) {
        log.debug("Finding match by playerId: {}, matchDate: {}, matchNumber: {}", playerId, matchDate, matchNumber);
        Match match = matchRepository.findByPlayerIdAndMatchDateAndMatchNumber(playerId, matchDate, matchNumber);
        if (match == null) {
            return null;
        }
        List<MatchDto> enriched = enrichMatchesWithPlayerPerspective(List.of(match), playerId);
        enriched = enrichDtosWithPersonalNotes(enriched, currentPlayerId, playerId);
        return enriched.isEmpty() ? null : enriched.get(0);
    }

    /**
     * 日付別の試合結果を取得（試合番号順）
     */
    public List<MatchDto> findMatchesByDate(LocalDate date, Long currentPlayerId) {
        log.debug("Finding matches by date: {}", date);
        List<Match> matches = matchRepository.findByMatchDateOrderByMatchNumber(date);
        List<MatchDto> dtos = enrichMatchesWithPlayerNames(matches, currentPlayerId);
        return enrichDtosWithPersonalNotes(dtos, currentPlayerId);
    }

    /**
     * 特定の日付に試合が存在するか確認
     */
    public boolean existsMatchOnDate(LocalDate date) {
        log.debug("Checking if matches exist on date: {}", date);
        return matchRepository.existsByMatchDate(date);
    }

    /**
     * 選手の試合履歴を取得（選手視点版）
     */
    public List<MatchDto> findPlayerMatches(Long playerId, Long currentPlayerId) {
        log.debug("Finding matches for player: {}", playerId);
        validatePlayerExists(playerId);
        List<Match> matches = matchRepository.findByPlayerId(playerId);
        List<MatchDto> dtos = enrichMatchesWithPlayerPerspective(matches, playerId);
        return enrichDtosWithPersonalNotes(dtos, currentPlayerId);
    }

    /**
     * 選手の試合履歴を取得（フィルタ付き）
     */
    public List<MatchDto> findPlayerMatchesWithFilters(Long playerId, String kyuRank, String gender, String dominantHand, Long currentPlayerId) {
        log.debug("Finding matches for player {} with filters: kyuRank={}, gender={}, dominantHand={}",
                playerId, kyuRank, gender, dominantHand);
        validatePlayerExists(playerId);
        List<Match> matches = matchRepository.findByPlayerId(playerId);
        List<MatchDto> enrichedMatches = enrichMatchesWithPlayerPerspective(matches, playerId);

        // フィルタリング処理
        List<MatchDto> filteredResult = enrichedMatches.stream()
                .filter(match -> {
                    // 対戦相手の情報を取得してフィルタリング
                    Player opponent = getOpponentPlayer(match, playerId);
                    if (opponent == null) {
                        // 対戦相手が未登録の場合はフィルタ対象外
                        return false;
                    }

                    // 級位でフィルタ（対戦時の級を優先）
                    if (kyuRank != null && !kyuRank.isEmpty()) {
                        String opponentRank = getOpponentKyuRankFromMatch(match, playerId);
                        if (opponentRank != null) {
                            if (!opponentRank.equals(kyuRank)) return false;
                        } else {
                            if (opponent.getKyuRank() == null ||
                                !opponent.getKyuRank().name().equals(kyuRank)) {
                                return false;
                            }
                        }
                    }

                    // 性別でフィルタ
                    if (gender != null && !gender.isEmpty()) {
                        if (opponent.getGender() == null ||
                            !opponent.getGender().name().equals(gender)) {
                            return false;
                        }
                    }

                    // 利き手でフィルタ
                    if (dominantHand != null && !dominantHand.isEmpty()) {
                        if (opponent.getDominantHand() == null ||
                            !opponent.getDominantHand().name().equals(dominantHand)) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());
        return enrichDtosWithPersonalNotes(filteredResult, currentPlayerId, playerId);
    }

    /**
     * 試合から対戦相手のPlayerエンティティを取得
     */
    private Player getOpponentPlayer(MatchDto match, Long viewingPlayerId) {
        Long opponentId = null;

        if (match.getPlayer1Id() != null && match.getPlayer1Id().equals(viewingPlayerId)) {
            opponentId = match.getPlayer2Id();
        } else if (match.getPlayer2Id() != null && match.getPlayer2Id().equals(viewingPlayerId)) {
            opponentId = match.getPlayer1Id();
        }

        // 対戦相手IDが0の場合は未登録選手
        if (opponentId == null || opponentId == 0L) {
            return null;
        }

        return playerRepository.findById(opponentId).orElse(null);
    }

    /**
     * 選手の期間内の試合履歴を取得
     */
    public List<MatchDto> findPlayerMatchesInPeriod(Long playerId, LocalDate startDate, LocalDate endDate, Long currentPlayerId) {
        log.debug("Finding matches for player {} between {} and {}", playerId, startDate, endDate);
        validatePlayerExists(playerId);
        List<Match> matches = matchRepository.findByPlayerIdAndDateRange(playerId, startDate, endDate);
        List<MatchDto> dtos = enrichMatchesWithPlayerNames(matches, currentPlayerId);
        return enrichDtosWithPersonalNotes(dtos, currentPlayerId, playerId);
    }

    /**
     * 選手の期間内の試合数を取得（軽量）
     */
    public long countPlayerMatchesInPeriod(Long playerId, LocalDate startDate, LocalDate endDate) {
        log.debug("Counting matches for player {} between {} and {}", playerId, startDate, endDate);
        return matchRepository.countByPlayerIdAndDateRange(playerId, startDate, endDate);
    }

    /**
     * 2人の選手間の対戦履歴を取得
     */
    public List<MatchDto> findMatchesBetweenPlayers(Long player1Id, Long player2Id, Long currentPlayerId) {
        log.debug("Finding matches between players {} and {}", player1Id, player2Id);
        validatePlayerExists(player1Id);
        validatePlayerExists(player2Id);

        Long smallerId = Math.min(player1Id, player2Id);
        Long largerId = Math.max(player1Id, player2Id);

        List<Match> matches = matchRepository.findByTwoPlayers(smallerId, largerId);
        List<MatchDto> dtos = enrichMatchesWithPlayerNames(matches, currentPlayerId);
        return enrichDtosWithPersonalNotes(dtos, currentPlayerId);
    }

    /**
     * 選手の統計情報を取得
     */
    public MatchStatisticsDto getPlayerStatistics(Long playerId) {
        log.debug("Getting statistics for player: {}", playerId);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        Long totalMatches = matchRepository.countByPlayerId(playerId);
        Long wins = matchRepository.countWinsByPlayerId(playerId);

        return MatchStatisticsDto.create(playerId, player.getName(), totalMatches, wins);
    }

    /**
     * 選手の級別統計情報を取得
     */
    public com.karuta.matchtracker.dto.StatisticsByRankDto getPlayerStatisticsByRank(
            Long playerId, String gender, String dominantHand, LocalDate startDate, LocalDate endDate) {
        log.debug("Getting statistics by rank for player {} with filters: gender={}, dominantHand={}, startDate={}, endDate={}",
                playerId, gender, dominantHand, startDate, endDate);

        validatePlayerExists(playerId);

        // 全試合を取得（性別・利き手でフィルタ済み）
        List<Match> allMatches = matchRepository.findByPlayerId(playerId);
        List<MatchDto> enrichedMatches = enrichMatchesWithPlayerPerspective(allMatches, playerId);

        // 性別・利き手・期間でフィルタ
        List<MatchDto> filteredMatches = enrichedMatches.stream()
                .filter(match -> {
                    // 期間フィルタ
                    if (startDate != null && match.getMatchDate().isBefore(startDate)) {
                        return false;
                    }
                    if (endDate != null && match.getMatchDate().isAfter(endDate)) {
                        return false;
                    }

                    // 対戦相手の情報を取得
                    Player opponent = getOpponentPlayer(match, playerId);
                    if (opponent == null) {
                        return false;
                    }

                    // 性別でフィルタ
                    if (gender != null && !gender.isEmpty()) {
                        if (opponent.getGender() == null ||
                            !opponent.getGender().name().equals(gender)) {
                            return false;
                        }
                    }

                    // 利き手でフィルタ
                    if (dominantHand != null && !dominantHand.isEmpty()) {
                        if (opponent.getDominantHand() == null ||
                            !opponent.getDominantHand().name().equals(dominantHand)) {
                            return false;
                        }
                    }

                    return true;
                })
                .collect(Collectors.toList());

        // 総計を計算
        long totalMatches = filteredMatches.size();
        long totalWins = filteredMatches.stream()
                .filter(m -> RESULT_WIN.equals(m.getResult()))
                .count();

        com.karuta.matchtracker.dto.RankStatisticsDto totalStats =
                com.karuta.matchtracker.dto.RankStatisticsDto.create("総計", totalMatches, totalWins);

        // 級別統計を計算
        Map<String, com.karuta.matchtracker.dto.RankStatisticsDto> byRankMap = new java.util.LinkedHashMap<>();

        for (String rank : java.util.List.of("A級", "B級", "C級", "D級", "E級")) {
            List<MatchDto> rankMatches = filteredMatches.stream()
                    .filter(match -> {
                        // 保存された対戦時の級を使用
                        String opponentRank = getOpponentKyuRankFromMatch(match, playerId);
                        if (opponentRank != null) {
                            return opponentRank.equals(rank);
                        }
                        // フォールバック: 保存された級がない場合は現在の級を参照
                        Player opponent = getOpponentPlayer(match, playerId);
                        return opponent != null &&
                               opponent.getKyuRank() != null &&
                               opponent.getKyuRank().name().equals(rank);
                    })
                    .collect(Collectors.toList());

            long rankTotal = rankMatches.size();
            long rankWins = rankMatches.stream()
                    .filter(m -> RESULT_WIN.equals(m.getResult()))
                    .count();

            byRankMap.put(rank, com.karuta.matchtracker.dto.RankStatisticsDto.create(rank, rankTotal, rankWins));
        }

        return com.karuta.matchtracker.dto.StatisticsByRankDto.builder()
                .total(totalStats)
                .byRank(byRankMap)
                .build();
    }

    /**
     * 試合結果を新規登録（簡易版：対戦相手名と結果から登録）
     */
    @Transactional
    public MatchDto createMatchSimple(MatchSimpleCreateRequest request, Long currentUserId) {
        log.info("Creating new match (simple) on {} (match #{})", request.getMatchDate(), request.getMatchNumber());

        // 練習日として登録されているかチェック
        if (!practiceSessionRepository.existsBySessionDate(request.getMatchDate())) {
            throw new IllegalArgumentException("試合記録は練習日として登録されている日のみ登録可能です");
        }

        // 選手の存在確認
        Player player = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", request.getPlayerId()));

        // 同日同試合番号の重複チェック
        log.debug("Checking for duplicate: playerId={}, date={}, matchNumber={}",
                  request.getPlayerId(), request.getMatchDate(), request.getMatchNumber());
        Match existingMatch = matchRepository.findByPlayerIdAndMatchDateAndMatchNumber(
                request.getPlayerId(), request.getMatchDate(), request.getMatchNumber());
        log.debug("Duplicate check result: {}", existingMatch != null);

        if (existingMatch != null) {
            String errorMessage = String.format("この日の第%d試合は既に登録されています", request.getMatchNumber());
            log.warn("Duplicate match detected: {} (existing ID: {})", errorMessage, existingMatch.getId());
            throw new DuplicateMatchException(errorMessage, existingMatch.getId());
        }

        // 対戦相手は名前のみで記録（将来的には選手IDと関連付けも可能）
        Long winnerId;
        Long loserId = null;

        // 結果に基づいて勝者を決定
        if (RESULT_WIN.equals(request.getResult())) {
            winnerId = request.getPlayerId();
        } else if (RESULT_LOSE.equals(request.getResult())) {
            winnerId = null; // 対戦相手が勝者だが、IDがないのでnull
        } else {
            winnerId = null; // 引き分け
        }

        // Matchエンティティを構築
        Match match = Match.builder()
                .matchDate(request.getMatchDate())
                .matchNumber(request.getMatchNumber())
                .player1Id(request.getPlayerId())
                .player2Id(0L) // ダミーID（対戦相手がシステム未登録のため）
                .winnerId(winnerId != null ? winnerId : 0L)
                .scoreDifference(Math.abs(request.getScoreDifference()))
                .opponentName(request.getOpponentName())
                .createdBy(currentUserId != null ? currentUserId : request.getPlayerId())
                .updatedBy(currentUserId != null ? currentUserId : request.getPlayerId())
                .build();

        // 対戦時の級位を記録
        setPlayerKyuRanks(match);

        Match saved = matchRepository.save(match);

        // 個人メモ・お手付きを保存
        upsertPersonalNote(saved.getId(), request.getPlayerId(), request.getPersonalNotes(), request.getOtetsukiCount(), currentUserId);

        // DTOに変換（対戦相手名はfromEntityで、結果はenrichMatchWithPlayerNamesで設定）
        MatchDto dto = enrichMatchWithPlayerNames(saved, request.getPlayerId());
        List<MatchDto> enriched = enrichDtosWithPersonalNotes(List.of(dto), request.getPlayerId());
        dto = enriched.get(0);

        log.info("Successfully created match with id: {}", saved.getId());
        return dto;
    }

    /**
     * 試合結果を新規登録
     */
    @Transactional
    public MatchDto createMatch(MatchCreateRequest request, Long currentUserId) {
        log.info("Creating new match on {} (match #{})", request.getMatchDate(), request.getMatchNumber());

        // 練習日として登録されているかチェック
        if (!practiceSessionRepository.existsBySessionDate(request.getMatchDate())) {
            throw new IllegalArgumentException("試合記録は練習日として登録されている日のみ登録可能です");
        }

        // 選手の存在確認
        validatePlayerExists(request.getPlayer1Id());
        validatePlayerExists(request.getPlayer2Id());
        validatePlayerExists(request.getWinnerId());

        // 勝者が対戦者のいずれかであることを確認
        if (!request.getWinnerId().equals(request.getPlayer1Id()) &&
            !request.getWinnerId().equals(request.getPlayer2Id())) {
            throw new IllegalArgumentException("Winner must be one of the players");
        }

        // 同じ選手同士の対戦は不可
        if (request.getPlayer1Id().equals(request.getPlayer2Id())) {
            throw new IllegalArgumentException("Player cannot play against themselves");
        }

        // player1Id < player2Idに正規化
        Long smallerId = Math.min(request.getPlayer1Id(), request.getPlayer2Id());
        Long largerId = Math.max(request.getPlayer1Id(), request.getPlayer2Id());

        // 既存の試合を検索（upsert: 同日・同試合番号・同ペアが存在すれば更新）
        var existing = matchRepository.findByMatchDateAndMatchNumberAndPlayers(
                request.getMatchDate(), request.getMatchNumber(), smallerId, largerId);

        Match saved;
        if (existing.isPresent()) {
            Match match = existing.get();
            match.setWinnerId(request.getWinnerId());
            match.setScoreDifference(request.getScoreDifference());
            match.setUpdatedBy(currentUserId != null ? currentUserId : request.getCreatedBy());
            setPlayerKyuRanks(match);
            saved = matchRepository.save(match);
            log.info("Upsert: updated existing match with id: {}", saved.getId());
        } else {
            Match match = request.toEntity();
            setPlayerKyuRanks(match);
            saved = matchRepository.save(match);
            log.info("Upsert: created new match with id: {}", saved.getId());
        }

        // 個人メモ・お手付きを保存
        upsertPersonalNote(saved.getId(), request.getCreatedBy(), request.getPersonalNotes(), request.getOtetsukiCount(), currentUserId);

        // 両プレイヤーが登録済みの場合、対応するmatch_pairingを自動生成
        autoCreateMatchPairingIfAbsent(saved);

        MatchDto dto = enrichMatchWithPlayerNames(saved, request.getCreatedBy());
        List<MatchDto> enriched = enrichDtosWithPersonalNotes(List.of(dto), request.getCreatedBy());
        return enriched.get(0);
    }

    /**
     * 対応するmatch_pairingが存在しなければ自動生成する
     */
    private void autoCreateMatchPairingIfAbsent(Match match) {
        if (match.getPlayer2Id() == null || match.getPlayer2Id() == 0L) {
            return; // 未登録対戦相手の場合はスキップ
        }
        Long p1 = Math.min(match.getPlayer1Id(), match.getPlayer2Id());
        Long p2 = Math.max(match.getPlayer1Id(), match.getPlayer2Id());
        var existing = matchPairingRepository.findBySessionDateAndMatchNumberAndPlayers(
                match.getMatchDate(), match.getMatchNumber(), p1, p2);
        if (existing.isEmpty()) {
            MatchPairing pairing = MatchPairing.builder()
                    .sessionDate(match.getMatchDate())
                    .matchNumber(match.getMatchNumber())
                    .player1Id(p1)
                    .player2Id(p2)
                    .createdBy(match.getCreatedBy())
                    .build();
            matchPairingRepository.save(pairing);
            log.info("match_pairing自動生成: date={}, matchNumber={}, p1={}, p2={}",
                     match.getMatchDate(), match.getMatchNumber(), p1, p2);
        }
    }

    /**
     * 試合結果を更新
     */
    @Transactional
    public MatchDto updateMatch(Long id, Long winnerId, Integer scoreDifference, Long updatedBy, String personalNotes, Integer otetsukiCount, Long currentUserId) {
        log.info("Updating match with id: {}", id);

        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match", id));

        // 勝者が対戦者のいずれかであることを確認
        if (!winnerId.equals(match.getPlayer1Id()) && !winnerId.equals(match.getPlayer2Id())) {
            throw new IllegalArgumentException("Winner must be one of the players");
        }

        match.setWinnerId(winnerId);
        match.setScoreDifference(scoreDifference);
        match.setUpdatedBy(currentUserId != null ? currentUserId : updatedBy);

        Match updated = matchRepository.save(match);

        // 個人メモ・お手付きを保存
        upsertPersonalNote(updated.getId(), updatedBy, personalNotes, otetsukiCount, currentUserId);

        MatchDto dto = enrichMatchWithPlayerNames(updated, updatedBy);
        List<MatchDto> enriched = enrichDtosWithPersonalNotes(List.of(dto), updatedBy);

        log.info("Successfully updated match with id: {}", id);
        return enriched.get(0);
    }

    /**
     * 試合結果を更新（簡易版）
     */
    @Transactional
    public MatchDto updateMatchSimple(Long id, MatchSimpleCreateRequest request, Long currentUserId) {
        log.info("Updating match (simple) with id: {}", id);

        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match", id));

        // 選手の存在確認
        Player player = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", request.getPlayerId()));

        // リクエストのplayerIdが対戦参加者であることを検証
        if (!request.getPlayerId().equals(match.getPlayer1Id()) && !request.getPlayerId().equals(match.getPlayer2Id())) {
            throw new IllegalArgumentException("指定された選手はこの試合の参加者ではありません");
        }

        // 結果に基づいて勝者を決定
        Long winnerId;
        if (RESULT_WIN.equals(request.getResult())) {
            winnerId = request.getPlayerId();
        } else if (RESULT_LOSE.equals(request.getResult())) {
            // 対戦相手のIDを特定（自分がplayer1ならplayer2、逆も同様）
            Long opponentId = match.getPlayer1Id().equals(request.getPlayerId())
                    ? match.getPlayer2Id() : match.getPlayer1Id();
            winnerId = opponentId;
        } else {
            winnerId = 0L; // 引き分け
        }

        // 試合情報を更新（player1Id/player2Idは変更しない）
        match.setMatchDate(request.getMatchDate());
        match.setMatchNumber(request.getMatchNumber());
        match.setWinnerId(winnerId);
        match.setScoreDifference(Math.abs(request.getScoreDifference()));
        match.setOpponentName(request.getOpponentName());
        match.setUpdatedBy(currentUserId != null ? currentUserId : request.getPlayerId());

        // 対戦時の級位を再記録
        setPlayerKyuRanks(match);

        Match updated = matchRepository.save(match);

        // 個人メモ・お手付きを保存
        upsertPersonalNote(updated.getId(), request.getPlayerId(), request.getPersonalNotes(), request.getOtetsukiCount(), currentUserId);

        MatchDto dto = enrichMatchWithPlayerNames(updated, request.getPlayerId());
        List<MatchDto> enriched = enrichDtosWithPersonalNotes(List.of(dto), request.getPlayerId());

        log.info("Successfully updated match with id: {}", id);
        return enriched.get(0);
    }

    /**
     * 試合結果を削除
     */
    @Transactional
    public void deleteMatch(Long id) {
        log.info("Deleting match with id: {}", id);

        if (!matchRepository.existsById(id)) {
            throw new ResourceNotFoundException("Match", id);
        }

        matchRepository.deleteById(id);
        log.info("Successfully deleted match with id: {}", id);
    }

    /**
     * MatchDtoから対戦相手の対戦時の級位を取得
     */
    private String getOpponentKyuRankFromMatch(MatchDto match, Long viewingPlayerId) {
        if (match.getPlayer1Id() != null && match.getPlayer1Id().equals(viewingPlayerId)) {
            return match.getPlayer2KyuRank();
        } else if (match.getPlayer2Id() != null && match.getPlayer2Id().equals(viewingPlayerId)) {
            return match.getPlayer1KyuRank();
        }
        return null;
    }

    /**
     * 選手の級位をString形式で取得（nullの場合はnull）
     */
    private String getPlayerKyuRankString(Long playerId) {
        if (playerId == null || playerId == 0L) return null;
        return playerRepository.findById(playerId)
                .map(p -> p.getKyuRank() != null ? p.getKyuRank().name() : null)
                .orElse(null);
    }

    /**
     * Matchエンティティに両選手の級位を設定
     */
    private void setPlayerKyuRanks(Match match) {
        match.setPlayer1KyuRank(getPlayerKyuRankString(match.getPlayer1Id()));
        match.setPlayer2KyuRank(getPlayerKyuRankString(match.getPlayer2Id()));
    }

    /**
     * 選手の存在確認
     */
    private void validatePlayerExists(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Player", playerId);
        }
    }

    /**
     * 試合リストから全選手IDを収集し、名前マップを構築する
     */
    private Map<Long, String> collectPlayerNames(List<Match> matches) {
        List<Long> playerIds = matches.stream()
                .flatMap(m -> List.of(m.getPlayer1Id(), m.getPlayer2Id()).stream())
                .filter(id -> id != 0L)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> playerNames = new HashMap<>();
        playerRepository.findAllById(playerIds).forEach(p -> playerNames.put(p.getId(), p.getName()));
        return playerNames;
    }

    /**
     * 試合の勝敗結果文字列を算出する
     */
    private String determineResult(Match match, Long viewingPlayerId) {
        if (match.getWinnerId() == null || match.getWinnerId() == 0L) {
            return RESULT_DRAW;
        }
        // 閲覧者が対戦者でない場合（null or 非参加者）はplayer1基準にフォールバック
        Long effectivePlayerId = viewingPlayerId;
        if (effectivePlayerId == null
                || (!effectivePlayerId.equals(match.getPlayer1Id())
                    && !effectivePlayerId.equals(match.getPlayer2Id()))) {
            effectivePlayerId = match.getPlayer1Id();
        }
        if (match.getWinnerId().equals(effectivePlayerId)) {
            return RESULT_WIN;
        }
        return RESULT_LOSE;
    }

    /**
     * 試合リストに選手名を設定
     */
    private List<MatchDto> enrichMatchesWithPlayerNames(List<Match> matches, Long currentPlayerId) {
        if (matches.isEmpty()) {
            return List.of();
        }

        Map<Long, String> playerNames = collectPlayerNames(matches);

        return matches.stream()
                .map(match -> {
                    MatchDto dto = MatchDto.fromEntity(match);

                    if (match.getPlayer1Id() != 0L && match.getPlayer2Id() != 0L) {
                        dto.setPlayer1Name(playerNames.get(match.getPlayer1Id()));
                        dto.setPlayer2Name(playerNames.get(match.getPlayer2Id()));

                        dto.setResult(determineResult(match, currentPlayerId));
                        dto.setWinnerName(match.getWinnerId() == 0L ? null : playerNames.get(match.getWinnerId()));

                        dto.setOpponentName(dto.getPlayer2Name());
                    }
                    else if ((match.getPlayer1Id() == 0L || match.getPlayer2Id() == 0L) && match.getOpponentName() != null) {
                        Long registeredPlayerId = match.getPlayer1Id() == 0L ? match.getPlayer2Id() : match.getPlayer1Id();

                        if (match.getPlayer1Id() != 0L) {
                            dto.setPlayer1Name(playerNames.get(match.getPlayer1Id()));
                        }
                        if (match.getPlayer2Id() != 0L) {
                            dto.setPlayer2Name(playerNames.get(match.getPlayer2Id()));
                        }

                        dto.setResult(determineResult(match, currentPlayerId));
                        if (match.getWinnerId() == 0L) {
                            dto.setWinnerName(null);
                        } else if (match.getWinnerId().equals(registeredPlayerId)) {
                            dto.setWinnerName(playerNames.get(match.getWinnerId()));
                        } else {
                            dto.setWinnerName(match.getOpponentName());
                        }
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 単一の試合に選手名を設定
     */
    private MatchDto enrichMatchWithPlayerNames(Match match, Long currentPlayerId) {
        List<MatchDto> enriched = enrichMatchesWithPlayerNames(List.of(match), currentPlayerId);
        return enriched.isEmpty() ? null : enriched.get(0);
    }

    /**
     * 試合リストに選手名を設定（選手視点版）
     */
    private List<MatchDto> enrichMatchesWithPlayerPerspective(List<Match> matches, Long viewingPlayerId) {
        if (matches.isEmpty()) {
            return List.of();
        }

        Map<Long, String> playerNames = collectPlayerNames(matches);

        return matches.stream()
                .map(match -> {
                    MatchDto dto = MatchDto.fromEntity(match);

                    dto.setPlayer1Name(playerNames.get(match.getPlayer1Id()));
                    dto.setPlayer2Name(playerNames.get(match.getPlayer2Id()));
                    dto.setWinnerName(match.getWinnerId() == 0L ? null : playerNames.get(match.getWinnerId()));

                    boolean isSimpleMatch = (match.getPlayer1Id() == 0L || match.getPlayer2Id() == 0L)
                            && match.getOpponentName() != null;

                    if (isSimpleMatch) {
                        Long registeredPlayerId = match.getPlayer1Id() == 0L
                                ? match.getPlayer2Id() : match.getPlayer1Id();
                        dto.setResult(determineResult(match, registeredPlayerId));
                    } else {
                        if (match.getPlayer1Id().equals(viewingPlayerId)) {
                            dto.setOpponentName(playerNames.get(match.getPlayer2Id()));
                        } else if (match.getPlayer2Id().equals(viewingPlayerId)) {
                            dto.setOpponentName(playerNames.get(match.getPlayer1Id()));
                        }
                        dto.setResult(determineResult(match, viewingPlayerId));
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 個人メモ・お手付き記録をupsert
     */
    private void upsertPersonalNote(Long matchId, Long playerId, String personalNotes, Integer otetsukiCount, Long currentUserId) {
        if (playerId == null || (personalNotes == null && otetsukiCount == null)) {
            return;
        }

        // playerIdが試合の参加者であることを検証
        Match match = matchRepository.findById(matchId).orElse(null);
        if (match == null) {
            return;
        }
        if (!playerId.equals(match.getPlayer1Id()) && !playerId.equals(match.getPlayer2Id())) {
            log.warn("個人メモ保存拒否: playerId={}は試合(id={})の参加者ではありません", playerId, matchId);
            return;
        }

        // 認証ユーザーとplayerIdの一致を検証（なりすまし防止）
        if (currentUserId == null || !currentUserId.equals(playerId)) {
            log.warn("個人メモ保存拒否: currentUserId={}がplayerId={}と一致しません(matchId={})", currentUserId, playerId, matchId);
            return;
        }

        MatchPersonalNote note = matchPersonalNoteRepository.findByMatchIdAndPlayerId(matchId, playerId)
                .orElse(MatchPersonalNote.builder()
                        .matchId(matchId)
                        .playerId(playerId)
                        .build());

        // Check if memo actually changed
        String oldNotes = note.getNotes();
        boolean memoChanged = personalNotes != null && !personalNotes.isBlank()
                             && !personalNotes.equals(oldNotes);

        note.setNotes(personalNotes);
        note.setOtetsukiCount(otetsukiCount);
        matchPersonalNoteRepository.save(note);

        // Send notification to mentors after transaction commit
        if (memoChanged) {
            final Long notifyPlayerId = playerId;
            final String notifyMemo = personalNotes;
            final Match notifyMatch = match;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        lineNotificationService.sendMemoUpdateFlexNotification(notifyPlayerId, notifyMatch, notifyMemo);
                    } catch (Exception e) {
                        log.warn("メモ更新通知の送信に失敗しました: matchId={}, playerId={}, error={}", matchId, notifyPlayerId, e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * DTOリストに個人メモ・お手付きデータを付与
     */
    private List<MatchDto> enrichDtosWithPersonalNotes(List<MatchDto> dtos, Long currentPlayerId) {
        return enrichDtosWithPersonalNotes(dtos, currentPlayerId, null);
    }

    private List<MatchDto> enrichDtosWithPersonalNotes(List<MatchDto> dtos, Long currentPlayerId, Long viewedPlayerId) {
        if (currentPlayerId == null || dtos.isEmpty()) {
            return dtos;
        }

        List<Long> matchIds = dtos.stream()
                .map(MatchDto::getId)
                .collect(Collectors.toList());

        Map<Long, MatchPersonalNote> noteMap = matchPersonalNoteRepository
                .findByPlayerIdAndMatchIdIn(currentPlayerId, matchIds)
                .stream()
                .collect(Collectors.toMap(MatchPersonalNote::getMatchId, n -> n));

        for (MatchDto dto : dtos) {
            MatchPersonalNote note = noteMap.get(dto.getId());
            if (note != null) {
                dto.setMyPersonalNotes(note.getNotes());
                dto.setMyOtetsukiCount(note.getOtetsukiCount());
            }
        }

        // メンター関係がある場合、メンティーのメモも取得
        if (viewedPlayerId != null && !viewedPlayerId.equals(currentPlayerId)) {
            List<MentorRelationship> activeRelationships = mentorRelationshipRepository
                    .findByMentorIdAndStatus(currentPlayerId, MentorRelationship.Status.ACTIVE);
            boolean isMentor = activeRelationships.stream()
                    .anyMatch(r -> r.getMenteeId().equals(viewedPlayerId));

            if (isMentor) {
                Map<Long, MatchPersonalNote> menteeNoteMap = matchPersonalNoteRepository
                        .findByPlayerIdAndMatchIdIn(viewedPlayerId, matchIds)
                        .stream()
                        .collect(Collectors.toMap(MatchPersonalNote::getMatchId, n -> n));

                for (MatchDto dto : dtos) {
                    MatchPersonalNote menteeNote = menteeNoteMap.get(dto.getId());
                    if (menteeNote != null) {
                        dto.setMenteePersonalNotes(menteeNote.getNotes());
                        dto.setMenteeOtetsukiCount(menteeNote.getOtetsukiCount());
                    }
                }
            }
        }

        return dtos;
    }
}
