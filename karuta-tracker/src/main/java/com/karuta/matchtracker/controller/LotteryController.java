package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionType;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.NotificationRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.repository.VenueRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.LotteryDeadlineHelper;
import com.karuta.matchtracker.util.AdminScopeValidator;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import com.karuta.matchtracker.service.LotteryService;
import com.karuta.matchtracker.service.NotificationService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽選関連のRESTコントローラ
 */
@RestController
@RequestMapping("/api/lottery")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174", "http://localhost:3000"})
public class LotteryController {

    private final LotteryService lotteryService;
    private final WaitlistPromotionService waitlistPromotionService;
    private final LineNotificationService lineNotificationService;
    private final NotificationService notificationService;
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final NotificationRepository notificationRepository;
    private final VenueRepository venueRepository;
    private final LotteryDeadlineHelper lotteryDeadlineHelper;

    /**
     * 締め切り日時取得
     */
    @GetMapping("/deadline")
    public ResponseEntity<Map<String, Object>> getDeadline(
            @RequestParam int year, @RequestParam int month,
            @RequestParam(required = false) Long organizationId) {
        boolean noDeadline = lotteryDeadlineHelper.isNoDeadline(organizationId);
        LocalDateTime deadline = noDeadline ? null : lotteryDeadlineHelper.getDeadline(year, month, organizationId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deadline", deadline);
        result.put("noDeadline", noDeadline);
        return ResponseEntity.ok(result);
    }

    /**
     * 手動抽選実行
     */
    @PostMapping("/execute")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<LotteryExecution> executeLottery(@Valid @RequestBody LotteryExecutionRequest request,
                                                              HttpServletRequest httpRequest) {
        int year = request.getYear();
        int month = request.getMonth();

        // 締め切り前チェック: 締め切り前に実行すると後から参加登録する人が漏れる
        // ただし「締め切りなし」モードの場合は管理者がいつでも手動実行可能
        Long orgId = request.getOrganizationId();
        if (!lotteryDeadlineHelper.isNoDeadline(orgId) && lotteryDeadlineHelper.isBeforeDeadline(year, month, orgId)) {
            throw new IllegalStateException(
                    String.format("%d年%d月の抽選はまだ締め切り前です。締め切り後に実行してください。", year, month));
        }

        // 重複チェック: 同一月に対して既に成功した抽選がある場合はエラー
        if (lotteryExecutionRepository.existsByTargetYearAndTargetMonthAndStatus(
                year, month, LotteryExecution.ExecutionStatus.SUCCESS)) {
            throw new IllegalStateException(
                    String.format("%d年%d月の抽選は既に実行済みです。再抽選が必要な場合はセッション単位で実行してください。", year, month));
        }

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        LotteryExecution result = lotteryService.executeLottery(
                year, month, currentUserId, ExecutionType.MANUAL, orgId);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * セッション再抽選
     */
    @PostMapping("/re-execute/{sessionId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LotteryExecution> reExecuteLottery(@PathVariable Long sessionId,
                                                                HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        validateAdminScopeBySessionId(sessionId, role, adminOrgId);

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        LotteryExecution result = lotteryService.reExecuteLottery(sessionId, currentUserId);
        return ResponseEntity.ok(result);
    }

    /**
     * 月別抽選結果取得
     */
    @GetMapping("/results")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<LotteryResultDto>> getLotteryResults(
            @RequestParam int year, @RequestParam int month) {

        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        List<LotteryResultDto> results = new ArrayList<>();

        for (PracticeSession session : sessions) {
            results.add(lotteryService.buildLotteryResult(session));
        }

        return ResponseEntity.ok(results);
    }

    /**
     * セッション別抽選結果取得
     */
    @GetMapping("/results/{sessionId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<LotteryResultDto> getSessionLotteryResult(@PathVariable Long sessionId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));
        return ResponseEntity.ok(lotteryService.buildLotteryResult(session));
    }

    /**
     * 自分の抽選結果取得
     */
    @GetMapping("/my-results")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<LotteryResultDto>> getMyLotteryResults(
            @RequestParam int year, @RequestParam int month,
            HttpServletRequest httpRequest) {

        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        List<LotteryResultDto> results = new ArrayList<>();

        for (PracticeSession session : sessions) {
            boolean involved = practiceParticipantRepository.existsBySessionIdAndPlayerId(
                    session.getId(), currentUserId);
            if (involved) {
                results.add(lotteryService.buildLotteryResult(session));
            }
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 参加キャンセル（理由付き・複数対応）
     */
    @PostMapping("/cancel")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Map<String, Object>> cancelParticipation(
            @Valid @RequestBody CancelRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));

        List<Long> ids = request.getEffectiveParticipantIds();
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "参加者IDが指定されていません"));
        }

        // PLAYER ロールは自分の参加のみキャンセル可能 + 過去日チェック
        if (currentUserRole == Role.PLAYER) {
            for (Long pid : ids) {
                PracticeParticipant p = practiceParticipantRepository.findById(pid).orElse(null);
                if (p != null && !p.getPlayerId().equals(currentUserId)) {
                    throw new ForbiddenException("他の参加者のキャンセルはできません");
                }
                if (p != null) {
                    PracticeSession session = practiceSessionRepository.findById(p.getSessionId()).orElse(null);
                    if (session != null && session.getSessionDate().isBefore(JstDateTimeUtil.today())) {
                        throw new IllegalStateException("過去の練習のキャンセルはできません");
                    }
                }
            }
        }

        List<String> results = new ArrayList<>();
        for (Long pid : ids) {
            ParticipantStatus status = waitlistPromotionService.cancelParticipation(
                    pid, request.getCancelReason(), request.getCancelReasonDetail());
            results.add(pid + ":" + status.name());
        }
        return ResponseEntity.ok(Map.of("status", "CANCELLED", "results", results));
    }

    /**
     * 繰り上げオファーへの応答
     */
    @PostMapping("/respond-offer")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Map<String, String>> respondToOffer(
            @Valid @RequestBody OfferResponseRequest request, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));

        // 応答前にparticipant情報を取得（応答後はステータスが変わるため）
        PracticeParticipant participant = practiceParticipantRepository.findById(request.getParticipantId())
                .orElse(null);

        // PLAYER ロールは自分のオファーのみ応答可能
        if (currentUserRole == Role.PLAYER && participant != null
                && !participant.getPlayerId().equals(currentUserId)) {
            throw new ForbiddenException("他の参加者のオファーには応答できません");
        }

        waitlistPromotionService.respondToOffer(request.getParticipantId(), request.getAccept());

        // Webアプリから応答した場合、LINEに確認通知を送信
        if (participant != null) {
            lineNotificationService.sendOfferResponseConfirmation(participant, request.getAccept());
        }

        return ResponseEntity.ok(Map.of("result", request.getAccept() ? "accepted" : "declined"));
    }

    /**
     * 個別オファー詳細取得
     */
    @GetMapping("/offer-detail/{participantId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<WaitlistStatusDto.WaitlistEntry> getOfferDetail(
            @PathVariable Long participantId, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));

        PracticeParticipant participant = practiceParticipantRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", participantId));

        // PLAYER ロールは自分のレコードのみ
        if (currentUserRole == Role.PLAYER && !participant.getPlayerId().equals(currentUserId)) {
            throw new ForbiddenException("他の参加者のオファー情報は参照できません");
        }

        PracticeSession session = practiceSessionRepository.findById(participant.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", participant.getSessionId()));

        String venueName = null;
        if (session.getVenueId() != null) {
            venueName = venueRepository.findById(session.getVenueId())
                    .map(v -> v.getName())
                    .orElse(null);
        }

        WaitlistStatusDto.WaitlistEntry entry = WaitlistStatusDto.WaitlistEntry.builder()
                .participantId(participant.getId())
                .sessionId(session.getId())
                .sessionDate(session.getSessionDate())
                .venueName(venueName)
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .matchNumber(participant.getMatchNumber())
                .waitlistNumber(participant.getWaitlistNumber())
                .status(participant.getStatus())
                .offerDeadline(participant.getOfferDeadline())
                .build();

        return ResponseEntity.ok(entry);
    }

    /**
     * キャンセル待ち状況取得
     */
    @GetMapping("/waitlist-status")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<WaitlistStatusDto> getWaitlistStatus(HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        List<PracticeParticipant> waitlisted = practiceParticipantRepository.findByPlayerId(currentUserId)
                .stream()
                .filter(p -> p.getStatus() == ParticipantStatus.WAITLISTED
                        || p.getStatus() == ParticipantStatus.OFFERED)
                .collect(Collectors.toList());

        List<WaitlistStatusDto.WaitlistEntry> entries = new ArrayList<>();

        for (PracticeParticipant p : waitlisted) {
            PracticeSession session = practiceSessionRepository.findById(p.getSessionId()).orElse(null);
            if (session == null) continue;

            entries.add(WaitlistStatusDto.WaitlistEntry.builder()
                    .participantId(p.getId())
                    .sessionId(session.getId())
                    .sessionDate(session.getSessionDate())
                    .startTime(session.getStartTime())
                    .endTime(session.getEndTime())
                    .matchNumber(p.getMatchNumber())
                    .waitlistNumber(p.getWaitlistNumber())
                    .status(p.getStatus())
                    .offerDeadline(p.getOfferDeadline())
                    .build());
        }

        return ResponseEntity.ok(WaitlistStatusDto.builder().entries(entries).build());
    }

    /**
     * キャンセル待ち辞退（セッション単位）
     */
    @PostMapping("/decline-waitlist")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Map<String, Object>> declineWaitlist(
            @RequestBody Map<String, Long> body, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));
        Long sessionId = body.get("sessionId");
        Long playerId = body.get("playerId");

        // PLAYERは自分のみ辞退可能
        if (currentUserRole == Role.PLAYER && !playerId.equals(currentUserId)) {
            throw new ForbiddenException("他の参加者のキャンセル待ちは辞退できません");
        }

        int count = waitlistPromotionService.declineWaitlistBySession(sessionId, playerId);
        return ResponseEntity.ok(Map.of(
                "declinedCount", count,
                "message", count + "件のキャンセル待ちを辞退しました"));
    }

    /**
     * キャンセル待ち復帰（セッション単位）
     */
    @PostMapping("/rejoin-waitlist")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<Map<String, Object>> rejoinWaitlist(
            @RequestBody Map<String, Long> body, HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        Role currentUserRole = Role.valueOf((String) httpRequest.getAttribute("currentUserRole"));
        Long sessionId = body.get("sessionId");
        Long playerId = body.get("playerId");

        // PLAYERは自分のみ復帰可能
        if (currentUserRole == Role.PLAYER && !playerId.equals(currentUserId)) {
            throw new ForbiddenException("他の参加者のキャンセル待ちは復帰できません");
        }

        int count = waitlistPromotionService.rejoinWaitlistBySession(sessionId, playerId);
        return ResponseEntity.ok(Map.of(
                "rejoinedCount", count,
                "message", "キャンセル待ちに復帰しました（" + count + "件）"));
    }

    /**
     * 管理者による参加者手動編集
     */
    @PutMapping("/admin/edit-participants")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> editParticipants(@Valid @RequestBody AdminEditParticipantsRequest request,
                                                     HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        validateAdminScopeBySessionId(request.getSessionId(), role, adminOrgId);

        lotteryService.editParticipants(request);
        return ResponseEntity.ok().build();
    }

    /**
     * 抽選結果通知の送信済みチェック
     */
    @GetMapping("/notify-status")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Map<String, Object>> getNotifyStatus(
            @RequestParam int year, @RequestParam int month,
            @RequestParam(required = false) Long organizationId,
            HttpServletRequest httpRequest) {

        // ADMINは自団体に強制
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        if ("ADMIN".equals(role)) {
            organizationId = adminOrgId;
        }

        // 対象月のセッションに紐づく参加者IDを一括取得（N+1対策）
        List<PracticeSession> sessions = organizationId != null
                ? practiceSessionRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                : practiceSessionRepository.findByYearAndMonth(year, month);
        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());
        List<Long> participantIds = sessionIds.isEmpty()
                ? List.of()
                : practiceParticipantRepository.findBySessionIdIn(sessionIds).stream()
                        .map(PracticeParticipant::getId)
                        .collect(Collectors.toList());

        if (participantIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("sent", false, "sentCount", 0));
        }

        long sentCount = notificationRepository.countByReferenceIdInAndTypeIn(
                participantIds,
                List.of(NotificationType.LOTTERY_WON, NotificationType.LOTTERY_WAITLISTED,
                        NotificationType.LOTTERY_ALL_WON, NotificationType.LOTTERY_REMAINING_WON));

        return ResponseEntity.ok(Map.of("sent", sentCount > 0, "sentCount", sentCount));
    }

    /**
     * 抽選結果通知の統合送信（アプリ内通知 + LINE通知）
     */
    @PostMapping("/notify-results")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Map<String, Object>> notifyResults(@RequestBody Map<String, Integer> body,
                                                                HttpServletRequest httpRequest) {
        int year = body.getOrDefault("year", 0);
        int month = body.getOrDefault("month", 0);
        Integer orgIdInt = body.get("organizationId");
        Long organizationId = orgIdInt != null ? orgIdInt.longValue() : null;

        // ADMINは自団体に強制
        String role = (String) httpRequest.getAttribute("currentUserRole");
        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        if ("ADMIN".equals(role)) {
            organizationId = adminOrgId;
        }

        // アプリ内通知を生成（全参加者を一括取得してフィルタリング、N+1対策）
        List<PracticeSession> sessions = organizationId != null
                ? practiceSessionRepository.findByYearAndMonthAndOrganizationId(year, month, organizationId)
                : practiceSessionRepository.findByYearAndMonth(year, month);
        List<Long> sessionIds = sessions.stream()
                .map(PracticeSession::getId)
                .collect(Collectors.toList());
        List<PracticeParticipant> allParticipants = sessionIds.isEmpty()
                ? List.of()
                : practiceParticipantRepository.findBySessionIdIn(sessionIds).stream()
                        .filter(p -> p.getStatus() == ParticipantStatus.WON
                                || p.getStatus() == ParticipantStatus.WAITLISTED)
                        .collect(Collectors.toList());
        int inAppCount = notificationService.createLotteryResultNotifications(allParticipants);

        // LINE通知を送信
        var lineResult = lineNotificationService.sendLotteryResults(year, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inAppCount", inAppCount);
        result.put("lineSent", lineResult.getSentPlayerCount());
        result.put("lineFailed", lineResult.getFailedPlayerCount());
        result.put("lineSkipped", lineResult.getSkippedPlayerCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 抽選結果確定
     */
    @PostMapping("/confirm")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<LotteryExecution> confirmLottery(@Valid @RequestBody LotteryExecutionRequest request,
                                                            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        LotteryExecution result = lotteryService.confirmLottery(
                request.getYear(), request.getMonth(), currentUserId, request.getOrganizationId());
        return ResponseEntity.ok(result);
    }

    /**
     * 抽選実行履歴取得
     */
    @GetMapping("/executions")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<List<LotteryExecution>> getLotteryExecutions(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(
                lotteryExecutionRepository.findByTargetYearAndTargetMonth(year, month));
    }

    /**
     * ADMINスコープ検証（sessionIdベース）
     */
    private void validateAdminScopeBySessionId(Long sessionId, String role, Long adminOrgId) {
        if (!"ADMIN".equals(role)) return;

        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PracticeSession", sessionId));
        AdminScopeValidator.validateScope(role, adminOrgId, session.getOrganizationId(),
                "他団体の抽選は操作できません");
    }

}
