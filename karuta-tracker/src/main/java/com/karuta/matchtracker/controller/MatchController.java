package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.MatchCreateRequest;
import com.karuta.matchtracker.dto.MatchDto;
import com.karuta.matchtracker.dto.MatchSimpleCreateRequest;
import com.karuta.matchtracker.dto.MatchStatisticsDto;
import com.karuta.matchtracker.service.MatchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 試合結果管理のRESTコントローラ
 */
@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Slf4j
public class MatchController {

    private final MatchService matchService;
    private final com.karuta.matchtracker.service.OrganizationService organizationService;
    private final com.karuta.matchtracker.repository.PracticeSessionRepository practiceSessionRepository;

    /**
     * 日付別の試合結果を取得
     *
     * @param date 試合日
     * @return 試合結果リスト
     */
    @GetMapping
    public ResponseEntity<List<MatchDto>> getMatchesByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest httpRequest) {
        log.debug("GET /api/matches?date={} - Getting matches by date", date);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId != null && !hasSessionOnDateForUser(date, currentUserId)) {
            return ResponseEntity.ok(List.of());
        }
        List<MatchDto> matches = matchService.findMatchesByDate(date);
        return ResponseEntity.ok(matches);
    }

    /**
     * 特定の日付に試合が存在するか確認
     *
     * @param date 試合日
     * @return 存在するかどうか
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> existsMatchOnDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /api/matches/exists?date={} - Checking if match exists", date);
        boolean exists = matchService.existsMatchOnDate(date);
        return ResponseEntity.ok(exists);
    }

    /**
     * IDで試合結果を取得
     *
     * @param id 試合ID
     * @return 試合結果
     */
    @GetMapping("/{id}")
    public ResponseEntity<MatchDto> getMatchById(@PathVariable Long id) {
        log.debug("GET /api/matches/{} - Getting match by id", id);
        MatchDto match = matchService.findById(id);
        return ResponseEntity.ok(match);
    }

    /**
     * 選手ID・日付・試合番号で試合結果を取得
     *
     * @param playerId 選手ID
     * @param matchDate 試合日
     * @param matchNumber 試合番号
     * @return 試合結果（存在しない場合は404）
     */
    @GetMapping("/player/{playerId}/date/{matchDate}/match/{matchNumber}")
    public ResponseEntity<MatchDto> getMatchByPlayerDateAndMatchNumber(
            @PathVariable Long playerId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate matchDate,
            @PathVariable Integer matchNumber) {
        log.debug("GET /api/matches/player/{}/date/{}/match/{} - Getting match by player, date, and match number",
                playerId, matchDate, matchNumber);
        MatchDto match = matchService.findByPlayerDateAndMatchNumber(playerId, matchDate, matchNumber);
        if (match == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(match);
    }

    /**
     * 選手の試合履歴を取得
     *
     * @param playerId 選手ID
     * @param kyuRank 級位フィルタ（オプション）
     * @param gender 性別フィルタ（オプション）
     * @param dominantHand 利き手フィルタ（オプション）
     * @return 試合結果リスト
     */
    @GetMapping("/player/{playerId}")
    public ResponseEntity<List<MatchDto>> getPlayerMatches(
            @PathVariable Long playerId,
            @RequestParam(required = false) String kyuRank,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String dominantHand,
            @RequestParam(required = false) Integer limit) {
        log.debug("GET /api/matches/player/{} - Getting player matches with filters: kyuRank={}, gender={}, dominantHand={}, limit={}",
                playerId, kyuRank, gender, dominantHand, limit);
        List<MatchDto> matches = matchService.findPlayerMatchesWithFilters(playerId, kyuRank, gender, dominantHand);
        if (limit != null && limit > 0 && matches.size() > limit) {
            matches = matches.subList(0, limit);
        }
        return ResponseEntity.ok(matches);
    }

    /**
     * 選手の期間内の試合履歴を取得
     *
     * @param playerId 選手ID
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 試合結果リスト
     */
    @GetMapping("/player/{playerId}/period")
    public ResponseEntity<List<MatchDto>> getPlayerMatchesInPeriod(
            @PathVariable Long playerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("GET /api/matches/player/{}/period?startDate={}&endDate={}", playerId, startDate, endDate);
        List<MatchDto> matches = matchService.findPlayerMatchesInPeriod(playerId, startDate, endDate);
        return ResponseEntity.ok(matches);
    }

    /**
     * 選手の期間内の試合数を取得（軽量）
     */
    @GetMapping("/player/{playerId}/period/count")
    public ResponseEntity<Long> getPlayerMatchCountInPeriod(
            @PathVariable Long playerId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("GET /api/matches/player/{}/period/count?startDate={}&endDate={}", playerId, startDate, endDate);
        long count = matchService.countPlayerMatchesInPeriod(playerId, startDate, endDate);
        return ResponseEntity.ok(count);
    }

    /**
     * 2人の選手間の対戦履歴を取得
     *
     * @param player1Id 選手1のID
     * @param player2Id 選手2のID
     * @return 試合結果リスト
     */
    @GetMapping("/between")
    public ResponseEntity<List<MatchDto>> getMatchesBetweenPlayers(
            @RequestParam Long player1Id,
            @RequestParam Long player2Id) {
        log.debug("GET /api/matches/between?player1Id={}&player2Id={}", player1Id, player2Id);
        List<MatchDto> matches = matchService.findMatchesBetweenPlayers(player1Id, player2Id);
        return ResponseEntity.ok(matches);
    }

    /**
     * 選手の統計情報を取得
     *
     * @param playerId 選手ID
     * @return 統計情報
     */
    @GetMapping("/player/{playerId}/statistics")
    public ResponseEntity<MatchStatisticsDto> getPlayerStatistics(@PathVariable Long playerId) {
        log.debug("GET /api/matches/player/{}/statistics - Getting player statistics", playerId);
        MatchStatisticsDto statistics = matchService.getPlayerStatistics(playerId);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 選手の級別統計情報を取得
     *
     * @param playerId 選手ID
     * @param gender 性別フィルタ（オプション）
     * @param dominantHand 利き手フィルタ（オプション）
     * @param startDate 開始日（オプション）
     * @param endDate 終了日（オプション）
     * @return 級別統計情報
     */
    @GetMapping("/player/{playerId}/statistics-by-rank")
    public ResponseEntity<com.karuta.matchtracker.dto.StatisticsByRankDto> getPlayerStatisticsByRank(
            @PathVariable Long playerId,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String dominantHand,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("GET /api/matches/player/{}/statistics-by-rank - Getting player statistics by rank with filters: gender={}, dominantHand={}, startDate={}, endDate={}",
                playerId, gender, dominantHand, startDate, endDate);
        com.karuta.matchtracker.dto.StatisticsByRankDto statistics =
                matchService.getPlayerStatisticsByRank(playerId, gender, dominantHand, startDate, endDate);
        return ResponseEntity.ok(statistics);
    }

    /**
     * 試合結果を新規登録（簡易版）
     *
     * @param request 簡易登録リクエスト
     * @return 登録された試合結果
     */
    @PostMapping
    public ResponseEntity<MatchDto> createMatch(@Valid @RequestBody MatchSimpleCreateRequest request) {
        log.info("POST /api/matches - Creating new match (simple) on {}", request.getMatchDate());
        MatchDto createdMatch = matchService.createMatchSimple(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMatch);
    }

    /**
     * 試合結果を新規登録（詳細版）
     *
     * @param request 登録リクエスト
     * @return 登録された試合結果
     */
    @PostMapping("/detailed")
    public ResponseEntity<MatchDto> createMatchDetailed(@Valid @RequestBody MatchCreateRequest request) {
        log.info("POST /api/matches/detailed - Creating new match on {}", request.getMatchDate());
        MatchDto createdMatch = matchService.createMatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMatch);
    }

    /**
     * 試合結果を更新（簡易版）
     *
     * @param id 試合ID
     * @param request 更新リクエスト
     * @return 更新された試合結果
     */
    @PutMapping("/{id}")
    public ResponseEntity<MatchDto> updateMatchSimple(
            @PathVariable Long id,
            @Valid @RequestBody MatchSimpleCreateRequest request) {
        log.info("PUT /api/matches/{} - Updating match (simple)", id);
        MatchDto updatedMatch = matchService.updateMatchSimple(id, request);
        return ResponseEntity.ok(updatedMatch);
    }

    /**
     * 試合結果を更新（詳細版）
     *
     * @param id 試合ID
     * @param winnerId 勝者ID
     * @param scoreDifference 点差
     * @param updatedBy 更新者ID
     * @return 更新された試合結果
     */
    @PutMapping("/{id}/detailed")
    public ResponseEntity<MatchDto> updateMatchDetailed(
            @PathVariable Long id,
            @RequestParam Long winnerId,
            @RequestParam Integer scoreDifference,
            @RequestParam Long updatedBy) {
        log.info("PUT /api/matches/{}/detailed - Updating match (detailed)", id);
        MatchDto updatedMatch = matchService.updateMatch(id, winnerId, scoreDifference, updatedBy);
        return ResponseEntity.ok(updatedMatch);
    }

    /**
     * 試合結果を削除
     *
     * @param id 試合ID
     * @return レスポンスなし
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMatch(@PathVariable Long id) {
        log.info("DELETE /api/matches/{} - Deleting match", id);
        matchService.deleteMatch(id);
        return ResponseEntity.noContent().build();
    }

    private boolean hasSessionOnDateForUser(LocalDate date, Long userId) {
        java.util.List<Long> orgIds = organizationService.getPlayerOrganizationIds(userId);
        if (orgIds.isEmpty()) return true;
        java.util.List<com.karuta.matchtracker.entity.PracticeSession> sessions =
                practiceSessionRepository.findByDateRange(date, date);
        if (sessions.isEmpty()) return true;
        return sessions.stream().anyMatch(s ->
                s.getOrganizationId() == null || orgIds.contains(s.getOrganizationId()));
    }
}
