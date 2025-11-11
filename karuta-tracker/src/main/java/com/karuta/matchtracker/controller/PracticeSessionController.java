package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.PracticeSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 練習日管理のRESTコントローラ
 */
@RestController
@RequestMapping("/api/practice-sessions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class PracticeSessionController {

    private final PracticeSessionService practiceSessionService;

    /**
     * 全ての練習日を取得
     *
     * @return 練習日リスト
     */
    @GetMapping
    public ResponseEntity<List<PracticeSessionDto>> getAllSessions() {
        log.debug("GET /api/practice-sessions - Getting all practice sessions");
        List<PracticeSessionDto> sessions = practiceSessionService.findAllSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * IDで練習日を取得
     *
     * @param id 練習日ID
     * @return 練習日情報
     */
    @GetMapping("/{id}")
    public ResponseEntity<PracticeSessionDto> getSessionById(@PathVariable Long id) {
        log.debug("GET /api/practice-sessions/{} - Getting practice session by id", id);
        PracticeSessionDto session = practiceSessionService.findById(id);
        return ResponseEntity.ok(session);
    }

    /**
     * 日付で練習日を取得
     *
     * @param date 日付
     * @return 練習日情報
     */
    @GetMapping("/date")
    public ResponseEntity<PracticeSessionDto> getSessionByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /api/practice-sessions/date?date={} - Getting practice session by date", date);
        PracticeSessionDto session = practiceSessionService.findByDate(date);
        return ResponseEntity.ok(session);
    }

    /**
     * 期間内の練習日を取得
     *
     * @param startDate 開始日
     * @param endDate 終了日
     * @return 練習日リスト
     */
    @GetMapping("/range")
    public ResponseEntity<List<PracticeSessionDto>> getSessionsInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        log.debug("GET /api/practice-sessions/range?startDate={}&endDate={}", startDate, endDate);
        List<PracticeSessionDto> sessions = practiceSessionService.findSessionsInRange(startDate, endDate);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 年月別で練習日を取得
     *
     * @param year 年
     * @param month 月
     * @return 練習日リスト
     */
    @GetMapping("/year-month")
    public ResponseEntity<List<PracticeSessionDto>> getSessionsByYearMonth(
            @RequestParam int year,
            @RequestParam int month) {
        log.debug("GET /api/practice-sessions/year-month?year={}&month={}", year, month);
        List<PracticeSessionDto> sessions = practiceSessionService.findSessionsByYearMonth(year, month);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 指定日以降の練習日を取得
     *
     * @param fromDate 基準日
     * @return 練習日リスト
     */
    @GetMapping("/upcoming")
    public ResponseEntity<List<PracticeSessionDto>> getUpcomingSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        log.debug("GET /api/practice-sessions/upcoming?fromDate={}", fromDate);
        List<PracticeSessionDto> sessions = practiceSessionService.findUpcomingSessions(fromDate);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 練習日の存在確認
     *
     * @param date 日付
     * @return 存在するかどうか
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> existsSessionOnDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("GET /api/practice-sessions/exists?date={}", date);
        boolean exists = practiceSessionService.existsSessionOnDate(date);
        return ResponseEntity.ok(exists);
    }

    /**
     * 練習セッションの参加者一覧を取得
     *
     * @param id 練習セッションID
     * @return 参加者リスト
     */
    @GetMapping("/{id}/participants")
    public ResponseEntity<List<PlayerDto>> getParticipants(@PathVariable Long id) {
        log.debug("GET /api/practice-sessions/{}/participants - Getting participants", id);
        List<PlayerDto> participants = practiceSessionService.getParticipants(id);
        return ResponseEntity.ok(participants);
    }

    /**
     * 練習日を新規登録
     *
     * @param request 登録リクエスト
     * @return 登録された練習日情報
     */
    @PostMapping
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<PracticeSessionDto> createSession(
            @Valid @RequestBody PracticeSessionCreateRequest request) {
        log.info("POST /api/practice-sessions - Creating new practice session on {}", request.getSessionDate());
        // TODO: 認証実装後は実際のユーザーIDを使用
        Long currentUserId = 1L;  // 仮のユーザーID
        PracticeSessionDto createdSession = practiceSessionService.createSession(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSession);
    }

    /**
     * 練習セッションを更新
     *
     * @param id 練習セッションID
     * @param request 更新リクエスト
     * @return 更新された練習日情報
     */
    @PutMapping("/{id}")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<PracticeSessionDto> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody PracticeSessionUpdateRequest request) {
        log.info("PUT /api/practice-sessions/{} - Updating practice session", id);
        // TODO: 認証実装後は実際のユーザーIDを使用
        Long currentUserId = 1L;  // 仮のユーザーID
        PracticeSessionDto updatedSession = practiceSessionService.updateSession(id, request, currentUserId);
        return ResponseEntity.ok(updatedSession);
    }

    /**
     * 総試合数を更新
     *
     * @param id 練習日ID
     * @param totalMatches 総試合数
     * @return 更新された練習日情報
     */
    @PutMapping("/{id}/total-matches")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<PracticeSessionDto> updateTotalMatches(
            @PathVariable Long id,
            @RequestParam Integer totalMatches) {
        log.info("PUT /api/practice-sessions/{}/total-matches - Updating total matches to {}", id, totalMatches);
        PracticeSessionDto updatedSession = practiceSessionService.updateTotalMatches(id, totalMatches);
        return ResponseEntity.ok(updatedSession);
    }

    /**
     * 練習日を削除
     *
     * @param id 練習日ID
     * @return レスポンスなし
     */
    @DeleteMapping("/{id}")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        log.info("DELETE /api/practice-sessions/{} - Deleting practice session", id);
        practiceSessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 選手の練習参加を一括登録
     *
     * @param request 参加登録リクエスト
     * @return レスポンスなし
     */
    @PostMapping("/participations")
    public ResponseEntity<Void> registerParticipations(
            @Valid @RequestBody PracticeParticipationRequest request) {
        log.info("POST /api/practice-sessions/participations - Registering participations for player {}",
                request.getPlayerId());
        practiceSessionService.registerParticipations(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 選手の特定月の参加状況を取得
     *
     * @param playerId 選手ID
     * @param year 年
     * @param month 月
     * @return セッションIDをキーとした試合番号リストのマップ
     */
    @GetMapping("/participations/player/{playerId}")
    public ResponseEntity<Map<Long, List<Integer>>> getPlayerParticipations(
            @PathVariable Long playerId,
            @RequestParam int year,
            @RequestParam int month) {
        log.debug("GET /api/practice-sessions/participations/player/{}?year={}&month={}", playerId, year, month);
        Map<Long, List<Integer>> participations = practiceSessionService.getPlayerParticipationsByMonth(playerId, year, month);
        return ResponseEntity.ok(participations);
    }
}
