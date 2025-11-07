package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.dto.PracticeSessionCreateRequest;
import com.karuta.matchtracker.dto.PracticeSessionDto;
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

/**
 * 練習日管理のRESTコントローラ
 */
@RestController
@RequestMapping("/api/practice-sessions")
@RequiredArgsConstructor
@Slf4j
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
     * 練習日を新規登録
     *
     * @param request 登録リクエスト
     * @return 登録された練習日情報
     */
    @PostMapping
    public ResponseEntity<PracticeSessionDto> createSession(@Valid @RequestBody PracticeSessionCreateRequest request) {
        log.info("POST /api/practice-sessions - Creating new practice session on {}", request.getSessionDate());
        PracticeSessionDto createdSession = practiceSessionService.createSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdSession);
    }

    /**
     * 総試合数を更新
     *
     * @param id 練習日ID
     * @param totalMatches 総試合数
     * @return 更新された練習日情報
     */
    @PutMapping("/{id}/total-matches")
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
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        log.info("DELETE /api/practice-sessions/{} - Deleting practice session", id);
        practiceSessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }
}
