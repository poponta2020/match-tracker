package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.PracticeParticipantService;
import com.karuta.matchtracker.service.PracticeSessionService;
import com.karuta.matchtracker.util.AdminScopeValidator;
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
    private final PracticeParticipantService practiceParticipantService;
    private final com.karuta.matchtracker.service.DensukeImportService densukeImportService;
    private final com.karuta.matchtracker.service.DensukeWriteService densukeWriteService;
    private final com.karuta.matchtracker.service.DensukeSyncService densukeSyncService;
    private final com.karuta.matchtracker.service.AdjacentRoomService adjacentRoomService;

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
        PracticeSessionDto session = practiceSessionService.findByDateWithParticipants(date);
        return ResponseEntity.ok(session);
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
            @RequestParam int month,
            HttpServletRequest httpRequest) {
        log.debug("GET /api/practice-sessions/year-month?year={}&month={}", year, month);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<PracticeSessionDto> sessions;
        if (currentUserId != null) {
            sessions = practiceSessionService.findSessionsByYearMonthAndPlayer(year, month, currentUserId);
        } else {
            sessions = practiceSessionService.findSessionsByYearMonth(year, month);
        }
        return ResponseEntity.ok(sessions);
    }

    /**
     * 年月別で練習日サマリーを取得（カレンダー用・軽量）
     */
    @GetMapping("/year-month/summary")
    public ResponseEntity<List<PracticeSessionDto>> getSessionSummariesByYearMonth(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest httpRequest) {
        log.debug("GET /api/practice-sessions/year-month/summary?year={}&month={}", year, month);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<PracticeSessionDto> sessions = practiceSessionService.findSessionSummariesByYearMonth(year, month, currentUserId);
        return ResponseEntity.ok(sessions);
    }

    /**
     * 月別参加率TOP3を取得
     *
     * @param year 年
     * @param month 月
     * @return 参加率TOP3リスト
     */
    @GetMapping("/participation-rate-top3")
    public ResponseEntity<List<ParticipationRateDto>> getParticipationRateTop3(
            @RequestParam int year,
            @RequestParam int month) {
        log.debug("GET /api/practice-sessions/participation-rate-top3?year={}&month={}", year, month);
        List<ParticipationRateDto> top3 = practiceParticipantService.getParticipationRateTop3(year, month);
        return ResponseEntity.ok(top3);
    }

    /**
     * 次の参加予定練習を取得（ホーム画面用・軽量）
     *
     * @param playerId 選手ID
     * @return 次の参加予定情報（なければ204）
     */
    @GetMapping("/next-participation")
    public ResponseEntity<?> getNextParticipation(@RequestParam Long playerId) {
        log.debug("GET /api/practice-sessions/next-participation?playerId={}", playerId);
        var result = practiceSessionService.findNextParticipation(playerId);
        if (result == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 指定日以降の練習日の日付リストのみ取得（軽量）
     *
     * @param fromDate 基準日
     * @return 日付リスト
     */
    @GetMapping("/dates")
    public ResponseEntity<List<LocalDate>> getSessionDates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            HttpServletRequest httpRequest) {
        log.debug("GET /api/practice-sessions/dates?fromDate={}", fromDate);
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<LocalDate> dates = practiceSessionService.findSessionDates(fromDate, currentUserId);
        return ResponseEntity.ok(dates);
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
        List<PlayerDto> participants = practiceParticipantService.getParticipants(id);
        return ResponseEntity.ok(participants);
    }

    /**
     * 練習日を新規登録
     *
     * @param request 登録リクエスト
     * @return 登録された練習日情報
     */
    @PostMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<PracticeSessionDto> createSession(
            @Valid @RequestBody PracticeSessionCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("POST /api/practice-sessions - Creating new practice session on {}", request.getSessionDate());
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        String currentUserRole = (String) httpRequest.getAttribute("currentUserRole");

        // ADMIN は自団体の organizationId を自動設定 / 他団体指定を禁止
        if ("ADMIN".equals(currentUserRole)) {
            Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
            if (request.getOrganizationId() == null) {
                request.setOrganizationId(adminOrgId);
            } else {
                AdminScopeValidator.validateScope(currentUserRole, adminOrgId, request.getOrganizationId(),
                        "他団体の練習日は作成できません");
            }
        }

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
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<PracticeSessionDto> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody PracticeSessionUpdateRequest request,
            HttpServletRequest httpRequest) {
        log.info("PUT /api/practice-sessions/{} - Updating practice session", id);
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        practiceSessionService.checkAdminScope(id, role, adminOrgId);

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
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
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
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
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> deleteSession(@PathVariable Long id, HttpServletRequest httpRequest) {
        log.info("DELETE /api/practice-sessions/{} - Deleting practice session", id);
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        practiceSessionService.checkAdminScope(id, role, adminOrgId);

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
        practiceParticipantService.registerParticipations(request);
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
        Map<Long, List<Integer>> participations = practiceParticipantService.getPlayerParticipationsByMonth(playerId, year, month);
        return ResponseEntity.ok(participations);
    }

    /**
     * 選手の参加状況を取得（抽選ステータス付き）
     */
    @GetMapping("/participations/player/{playerId}/status")
    public ResponseEntity<PlayerParticipationStatusDto> getPlayerParticipationStatus(
            @PathVariable Long playerId,
            @RequestParam int year,
            @RequestParam int month) {
        log.debug("GET /api/practice-sessions/participations/player/{}/status?year={}&month={}", playerId, year, month);
        PlayerParticipationStatusDto status = practiceParticipantService.getPlayerParticipationStatusByMonth(playerId, year, month);
        return ResponseEntity.ok(status);
    }

    /**
     * 特定の試合の参加者を設定（管理者のみ）
     *
     * @param sessionId 練習日ID
     * @param matchNumber 試合番号
     * @param request 参加者IDリスト
     * @return 成功レスポンス
     */
    @PutMapping("/{sessionId}/matches/{matchNumber}/participants")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> setMatchParticipants(
            @PathVariable Long sessionId,
            @PathVariable Integer matchNumber,
            @Valid @RequestBody MatchParticipantsRequest request,
            HttpServletRequest httpRequest) {
        log.info("PUT /api/practice-sessions/{}/matches/{}/participants - Setting participants",
                sessionId, matchNumber);
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        practiceSessionService.checkAdminScope(sessionId, role, adminOrgId);
        practiceParticipantService.setMatchParticipants(sessionId, matchNumber, request.getPlayerIds());
        return ResponseEntity.ok().build();
    }

    /**
     * 特定の試合に参加者を1名追加
     *
     * @param date 練習日の日付
     * @param matchNumber 試合番号
     * @param playerId 追加する選手ID
     * @return 更新された練習日情報
     */
    @PostMapping("/date/{date}/matches/{matchNumber}/participants/{playerId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<PracticeSessionDto> addParticipantToMatch(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable Integer matchNumber,
            @PathVariable Long playerId,
            HttpServletRequest httpRequest) {
        log.info("POST /api/practice-sessions/date/{}/matches/{}/participants/{} - Adding participant to match",
                date, matchNumber, playerId);
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        practiceSessionService.checkAdminScopeByDate(date, role, adminOrgId);
        practiceParticipantService.addParticipantToMatch(date, matchNumber, playerId);
        // 更新後の練習セッション情報を返す
        PracticeSessionDto session = practiceSessionService.findByDate(date);
        return ResponseEntity.ok(session);
    }

    /**
     * 未登録者を一括登録して伝助から再同期
     */
    @PostMapping("/register-and-sync-densuke")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<?> registerAndSyncDensuke(@RequestBody Map<String, Object> request,
                                                     HttpServletRequest httpRequest) {
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) request.get("names");
        Integer year = (Integer) request.get("year");
        Integer month = (Integer) request.get("month");

        if (names == null || names.isEmpty() || year == null || month == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "names, year, monthは必須です"));
        }

        Integer orgIdInt = (Integer) request.get("organizationId");
        Long organizationId = orgIdInt != null ? orgIdInt.longValue() : null;

        // ADMIN権限チェック: 自団体のみ操作可能
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AdminScopeValidator.validateScope(role, adminOrgId, organizationId, "他団体の未登録者一括登録はできません");

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        var densukeUrl = practiceSessionService.getDensukeUrl(year, month, organizationId);
        if (densukeUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    year + "年" + month + "月の伝助URLが登録されていません"));
        }

        try {
            LocalDate targetMonth = LocalDate.of(year, month, 1);
            var result = densukeImportService.registerAndSync(
                    names, densukeUrl.get().getUrl(), targetMonth, currentUserId, organizationId);
            return ResponseEntity.ok(result);
        } catch (java.io.IOException e) {
            log.error("Register and sync failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "伝助との同期に失敗しました: " + e.getMessage()));
        }
    }

    /**
     * 特定の試合から参加者を1名削除
     */
    @DeleteMapping("/{sessionId}/matches/{matchNumber}/participants/{playerId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> removeParticipantFromMatch(
            @PathVariable Long sessionId,
            @PathVariable Integer matchNumber,
            @PathVariable Long playerId,
            HttpServletRequest httpRequest) {
        log.info("DELETE /api/practice-sessions/{}/matches/{}/participants/{} - Removing participant",
                sessionId, matchNumber, playerId);
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        practiceSessionService.checkAdminScope(sessionId, role, adminOrgId);
        practiceParticipantService.removeParticipantFromMatch(sessionId, matchNumber, playerId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 会場を拡張（隣室と合わせた大部屋に変更）
     *
     * @param id セッションID
     * @return 更新後のセッション情報
     */
    @PostMapping("/{id}/expand-venue")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<PracticeSessionDto> expandVenue(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        log.info("POST /api/practice-sessions/{}/expand-venue - Expanding venue", id);
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        practiceSessionService.checkAdminScope(id, role, adminOrgId);

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        adjacentRoomService.expandVenue(id, currentUserId);
        PracticeSessionDto updatedSession = practiceSessionService.findById(id);
        return ResponseEntity.ok(updatedSession);
    }

    // ========== 伝助URL管理 ==========

    /**
     * 指定年月の伝助URLを取得
     */
    @GetMapping("/densuke-url")
    @RequireRole({Role.PLAYER, Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<?> getDensukeUrl(@RequestParam int year, @RequestParam int month,
                                             @RequestParam Long organizationId) {
        var url = practiceSessionService.getDensukeUrl(year, month, organizationId);
        return url.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * 伝助URLを登録/更新（upsert）
     */
    @PutMapping("/densuke-url")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<?> saveDensukeUrl(@RequestBody Map<String, Object> request,
                                              HttpServletRequest httpRequest) {
        Integer year = (Integer) request.get("year");
        Integer month = (Integer) request.get("month");
        String url = (String) request.get("url");
        Integer orgIdInt = (Integer) request.get("organizationId");
        Long organizationId = orgIdInt != null ? orgIdInt.longValue() : null;

        if (year == null || month == null || url == null || url.isBlank() || organizationId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "year, month, url, organizationIdは必須です"));
        }

        // ADMIN権限チェック: 自団体のみ操作可能
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AdminScopeValidator.validateScope(role, adminOrgId, organizationId, "他団体の伝助URLは編集できません");

        try {
            var entity = practiceSessionService.saveDensukeUrl(year, month, url, organizationId);
            return ResponseEntity.ok(entity);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * 指定年月の伝助データを同期（URLはDBから取得）
     */
    @PostMapping("/sync-densuke")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<?> syncDensuke(@RequestBody Map<String, Integer> request,
                                         HttpServletRequest httpRequest) {
        Integer year = request.get("year");
        Integer month = request.get("month");
        Integer orgIdInt = request.get("organizationId");
        Long organizationId = orgIdInt != null ? orgIdInt.longValue() : null;

        if (year == null || month == null || organizationId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "year, month, organizationIdは必須です"));
        }

        // ADMIN権限チェック
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AdminScopeValidator.validateScope(role, adminOrgId, organizationId, "他団体の伝助は同期できません");

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        var densukeUrl = practiceSessionService.getDensukeUrl(year, month, organizationId);
        if (densukeUrl.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    year + "年" + month + "月の伝助URLが登録されていません"));
        }

        log.info("POST /api/practice-sessions/sync-densuke - Syncing {}/{} (orgId={})",
                year, month, organizationId);

        try {
            var result = densukeSyncService.syncForOrganization(year, month, organizationId, currentUserId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (java.io.IOException e) {
            log.error("Densuke sync failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "伝助との同期に失敗しました: " + e.getMessage()));
        }
    }

    /**
     * 伝助への書き込み状況を取得
     */
    @GetMapping("/densuke-write-status")
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<DensukeWriteStatusDto> getDensukeWriteStatus(
            @RequestParam Long organizationId,
            HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        AdminScopeValidator.validateScope(role, adminOrgId, organizationId, "他団体の書き込み状況は取得できません");
        return ResponseEntity.ok(densukeWriteService.getStatus(organizationId));
    }
}
