package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.MatchCreateRequest;
import com.karuta.matchtracker.dto.MatchDto;
import com.karuta.matchtracker.dto.MatchSimpleCreateRequest;
import com.karuta.matchtracker.dto.MatchStatisticsDto;
import com.karuta.matchtracker.entity.Match;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.DuplicateMatchException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.MatchRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final MatchRepository matchRepository;
    private final PlayerRepository playerRepository;

    /**
     * IDで試合結果を取得
     */
    public MatchDto findById(Long id) {
        log.debug("Finding match by id: {}", id);
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match", id));
        return enrichMatchWithPlayerNames(match);
    }

    /**
     * 日付別の試合結果を取得（試合番号順）
     */
    public List<MatchDto> findMatchesByDate(LocalDate date) {
        log.debug("Finding matches by date: {}", date);
        List<Match> matches = matchRepository.findByMatchDateOrderByMatchNumber(date);
        return enrichMatchesWithPlayerNames(matches);
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
    public List<MatchDto> findPlayerMatches(Long playerId) {
        log.debug("Finding matches for player: {}", playerId);
        validatePlayerExists(playerId);
        List<Match> matches = matchRepository.findByPlayerId(playerId);
        return enrichMatchesWithPlayerPerspective(matches, playerId);
    }

    /**
     * 選手の期間内の試合履歴を取得
     */
    public List<MatchDto> findPlayerMatchesInPeriod(Long playerId, LocalDate startDate, LocalDate endDate) {
        log.debug("Finding matches for player {} between {} and {}", playerId, startDate, endDate);
        validatePlayerExists(playerId);
        List<Match> matches = matchRepository.findByPlayerIdAndDateRange(playerId, startDate, endDate);
        return enrichMatchesWithPlayerNames(matches);
    }

    /**
     * 2人の選手間の対戦履歴を取得
     */
    public List<MatchDto> findMatchesBetweenPlayers(Long player1Id, Long player2Id) {
        log.debug("Finding matches between players {} and {}", player1Id, player2Id);
        validatePlayerExists(player1Id);
        validatePlayerExists(player2Id);

        Long smallerId = Math.min(player1Id, player2Id);
        Long largerId = Math.max(player1Id, player2Id);

        List<Match> matches = matchRepository.findByTwoPlayers(smallerId, largerId);
        return enrichMatchesWithPlayerNames(matches);
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
     * 試合結果を新規登録（簡易版：対戦相手名と結果から登録）
     */
    @Transactional
    public MatchDto createMatchSimple(MatchSimpleCreateRequest request) {
        log.info("Creating new match (simple) on {} (match #{})", request.getMatchDate(), request.getMatchNumber());

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
        if ("勝ち".equals(request.getResult())) {
            winnerId = request.getPlayerId();
        } else if ("負け".equals(request.getResult())) {
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
                .notes(request.getNotes())
                .createdBy(request.getPlayerId())
                .updatedBy(request.getPlayerId())
                .build();

        Match saved = matchRepository.save(match);

        // DTOに変換（対戦相手名はfromEntityで、結果はenrichMatchWithPlayerNamesで設定）
        MatchDto dto = enrichMatchWithPlayerNames(saved);

        log.info("Successfully created match with id: {}", saved.getId());
        return dto;
    }

    /**
     * 試合結果を新規登録
     */
    @Transactional
    public MatchDto createMatch(MatchCreateRequest request) {
        log.info("Creating new match on {} (match #{})", request.getMatchDate(), request.getMatchNumber());

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

        Match match = request.toEntity();
        Match saved = matchRepository.save(match);

        log.info("Successfully created match with id: {}", saved.getId());
        return enrichMatchWithPlayerNames(saved);
    }

    /**
     * 試合結果を更新
     */
    @Transactional
    public MatchDto updateMatch(Long id, Long winnerId, Integer scoreDifference, Long updatedBy) {
        log.info("Updating match with id: {}", id);

        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match", id));

        // 勝者が対戦者のいずれかであることを確認
        if (!winnerId.equals(match.getPlayer1Id()) && !winnerId.equals(match.getPlayer2Id())) {
            throw new IllegalArgumentException("Winner must be one of the players");
        }

        match.setWinnerId(winnerId);
        match.setScoreDifference(scoreDifference);
        match.setUpdatedBy(updatedBy);

        Match updated = matchRepository.save(match);

        log.info("Successfully updated match with id: {}", id);
        return enrichMatchWithPlayerNames(updated);
    }

    /**
     * 試合結果を更新（簡易版）
     */
    @Transactional
    public MatchDto updateMatchSimple(Long id, MatchSimpleCreateRequest request) {
        log.info("Updating match (simple) with id: {}", id);

        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Match", id));

        // 選手の存在確認
        Player player = playerRepository.findById(request.getPlayerId())
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", request.getPlayerId()));

        // 結果に基づいて勝者を決定
        Long winnerId;
        if ("勝ち".equals(request.getResult())) {
            winnerId = request.getPlayerId();
        } else if ("負け".equals(request.getResult())) {
            winnerId = 0L; // 対戦相手が勝者
        } else {
            winnerId = 0L; // 引き分け
        }

        // 試合情報を更新
        match.setMatchDate(request.getMatchDate());
        match.setMatchNumber(request.getMatchNumber());
        match.setPlayer1Id(request.getPlayerId());
        match.setWinnerId(winnerId);
        match.setScoreDifference(Math.abs(request.getScoreDifference()));
        match.setOpponentName(request.getOpponentName());
        match.setNotes(request.getNotes());
        match.setUpdatedBy(request.getPlayerId());

        Match updated = matchRepository.save(match);

        log.info("Successfully updated match with id: {}", id);
        return enrichMatchWithPlayerNames(updated);
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
     * 選手の存在確認
     */
    private void validatePlayerExists(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new ResourceNotFoundException("Player", playerId);
        }
    }

    /**
     * 試合リストに選手名を設定
     */
    private List<MatchDto> enrichMatchesWithPlayerNames(List<Match> matches) {
        if (matches.isEmpty()) {
            return List.of();
        }

        // 全選手IDを収集（0は除外）
        List<Long> playerIds = matches.stream()
                .flatMap(m -> List.of(m.getPlayer1Id(), m.getPlayer2Id()).stream())
                .filter(id -> id != 0L)
                .distinct()
                .collect(Collectors.toList());

        // 選手名のマップを作成
        Map<Long, String> playerNames = new HashMap<>();
        playerRepository.findAllById(playerIds).forEach(p -> playerNames.put(p.getId(), p.getName()));

        // DTOに変換して選手名を設定
        return matches.stream()
                .map(match -> {
                    MatchDto dto = MatchDto.fromEntity(match);

                    // 通常の試合（両選手がシステムに登録されている場合）
                    dto.setPlayer1Name(playerNames.get(match.getPlayer1Id()));
                    dto.setPlayer2Name(playerNames.get(match.getPlayer2Id()));
                    dto.setWinnerName(playerNames.get(match.getWinnerId()));

                    // 簡易試合（対戦相手が未登録の場合）の処理
                    // player1Idとplayer2Idのどちらかが0の場合
                    if ((match.getPlayer1Id() == 0L || match.getPlayer2Id() == 0L) && match.getOpponentName() != null) {
                        // 登録選手のIDを特定
                        Long registeredPlayerId = match.getPlayer1Id() == 0L ? match.getPlayer2Id() : match.getPlayer1Id();

                        // 結果を計算
                        if (match.getWinnerId() == 0L) {
                            dto.setResult("引き分け");
                        } else if (match.getWinnerId().equals(registeredPlayerId)) {
                            dto.setResult("勝ち");
                        } else {
                            dto.setResult("負け");
                        }
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 単一の試合に選手名を設定
     */
    private MatchDto enrichMatchWithPlayerNames(Match match) {
        List<MatchDto> enriched = enrichMatchesWithPlayerNames(List.of(match));
        return enriched.isEmpty() ? null : enriched.get(0);
    }

    /**
     * 試合リストに選手名を設定（選手視点版）
     * 指定された選手の視点で、opponentNameとresultフィールドを設定する
     *
     * @param matches 試合リスト
     * @param viewingPlayerId 視点となる選手のID
     * @return 選手視点情報を含むMatchDtoリスト
     */
    private List<MatchDto> enrichMatchesWithPlayerPerspective(List<Match> matches, Long viewingPlayerId) {
        if (matches.isEmpty()) {
            return List.of();
        }

        // 全選手IDを収集（0は除外）
        List<Long> playerIds = matches.stream()
                .flatMap(m -> List.of(m.getPlayer1Id(), m.getPlayer2Id()).stream())
                .filter(id -> id != 0L)
                .distinct()
                .collect(Collectors.toList());

        // 選手名のマップを作成
        Map<Long, String> playerNames = new HashMap<>();
        playerRepository.findAllById(playerIds).forEach(p -> playerNames.put(p.getId(), p.getName()));

        // DTOに変換して選手名を設定
        return matches.stream()
                .map(match -> {
                    MatchDto dto = MatchDto.fromEntity(match);

                    // 通常の試合（両選手がシステムに登録されている場合）
                    dto.setPlayer1Name(playerNames.get(match.getPlayer1Id()));
                    dto.setPlayer2Name(playerNames.get(match.getPlayer2Id()));
                    dto.setWinnerName(playerNames.get(match.getWinnerId()));

                    // 簡易試合（対戦相手が未登録の場合）
                    if ((match.getPlayer1Id() == 0L || match.getPlayer2Id() == 0L) && match.getOpponentName() != null) {
                        // 登録選手のIDを特定
                        Long registeredPlayerId = match.getPlayer1Id() == 0L ? match.getPlayer2Id() : match.getPlayer1Id();

                        // opponentNameは既にエンティティから設定されている

                        // 結果を計算
                        if (match.getWinnerId() == 0L) {
                            dto.setResult("引き分け");
                        } else if (match.getWinnerId().equals(registeredPlayerId)) {
                            dto.setResult("勝ち");
                        } else {
                            dto.setResult("負け");
                        }
                    } else {
                        // 通常試合の場合、viewingPlayerIdの視点で情報を設定

                        // 対戦相手名を設定
                        if (match.getPlayer1Id().equals(viewingPlayerId)) {
                            dto.setOpponentName(playerNames.get(match.getPlayer2Id()));
                        } else if (match.getPlayer2Id().equals(viewingPlayerId)) {
                            dto.setOpponentName(playerNames.get(match.getPlayer1Id()));
                        }

                        // 結果を設定
                        if (match.getWinnerId() == null || match.getWinnerId() == 0L) {
                            dto.setResult("引き分け");
                        } else if (match.getWinnerId().equals(viewingPlayerId)) {
                            dto.setResult("勝ち");
                        } else {
                            dto.setResult("負け");
                        }
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }
}
