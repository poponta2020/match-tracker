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

    /**
     * 手動抽選実行
     */
    @PostMapping("/execute")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<LotteryExecution> executeLottery(@Valid @RequestBody LotteryExecutionRequest request,
                                                              HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        LotteryExecution result = lotteryService.executeLottery(
                request.getYear(), request.getMonth(), currentUserId, ExecutionType.MANUAL);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * セッション再抽選
     */
    @PostMapping("/re-execute/{sessionId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LotteryExecution> reExecuteLottery(@PathVariable Long sessionId,
                                                                HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        LotteryExecution result = lotteryService.reExecuteLottery(sessionId, currentUserId);
        return ResponseEntity.ok(result);
    }

    /**
     * 月別抽選結果取得
     */
    @GetMapping("/results")
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
        Role currentUserRole = (Role) httpRequest.getAttribute("currentUserRole");

        List<Long> ids = request.getEffectiveParticipantIds();
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "参加者IDが指定されていません"));
        }

        // PLAYER ロールは自分の参加のみキャンセル可能
        if (currentUserRole == Role.PLAYER) {
            for (Long pid : ids) {
                PracticeParticipant p = practiceParticipantRepository.findById(pid).orElse(null);
                if (p != null && !p.getPlayerId().equals(currentUserId)) {
                    throw new ForbiddenException("他の参加者のキャンセルはできません");
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
        Role currentUserRole = (Role) httpRequest.getAttribute("currentUserRole");

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
        Role currentUserRole = (Role) httpRequest.getAttribute("currentUserRole");

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
     * 管理者による参加者手動編集
     */
    @PutMapping("/admin/edit-participants")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> editParticipants(@Valid @RequestBody AdminEditParticipantsRequest request) {
        // 参加者追加
        if (request.getAdditions() != null) {
            for (AdminEditParticipantsRequest.AddParticipant add : request.getAdditions()) {
                PracticeParticipant participant = PracticeParticipant.builder()
                        .sessionId(request.getSessionId())
                        .playerId(add.getPlayerId())
                        .matchNumber(request.getMatchNumber())
                        .status(add.getStatus() != null ? add.getStatus() : ParticipantStatus.WON)
                        .build();
                practiceParticipantRepository.save(participant);
            }
        }

        // ステータス変更
        if (request.getStatusChanges() != null) {
            for (AdminEditParticipantsRequest.StatusChange change : request.getStatusChanges()) {
                PracticeParticipant p = practiceParticipantRepository.findById(change.getParticipantId())
                        .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", change.getParticipantId()));
                p.setStatus(change.getNewStatus());
                if (change.getWaitlistNumber() != null) {
                    p.setWaitlistNumber(change.getWaitlistNumber());
                }
                practiceParticipantRepository.save(p);
            }
        }

        // キャンセル待ち順番変更
        if (request.getWaitlistReorders() != null) {
            for (AdminEditParticipantsRequest.WaitlistReorder reorder : request.getWaitlistReorders()) {
                PracticeParticipant p = practiceParticipantRepository.findById(reorder.getParticipantId())
                        .orElseThrow(() -> new ResourceNotFoundException("PracticeParticipant", reorder.getParticipantId()));
                p.setWaitlistNumber(reorder.getNewWaitlistNumber());
                practiceParticipantRepository.save(p);
            }
        }

        return ResponseEntity.ok().build();
    }

    /**
     * 抽選結果通知の送信済みチェック
     */
    @GetMapping("/notify-status")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Map<String, Object>> getNotifyStatus(
            @RequestParam int year, @RequestParam int month) {

        // 対象月のセッションに紐づく参加者IDを取得
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        List<Long> participantIds = sessions.stream()
                .flatMap(s -> practiceParticipantRepository.findBySessionId(s.getId()).stream())
                .map(PracticeParticipant::getId)
                .collect(Collectors.toList());

        if (participantIds.isEmpty()) {
            return ResponseEntity.ok(Map.of("sent", false, "sentCount", 0));
        }

        long sentCount = notificationRepository.countByReferenceIdInAndTypeIn(
                participantIds,
                List.of(NotificationType.LOTTERY_WON, NotificationType.LOTTERY_WAITLISTED));

        return ResponseEntity.ok(Map.of("sent", sentCount > 0, "sentCount", sentCount));
    }

    /**
     * 抽選結果通知の統合送信（アプリ内通知 + LINE通知）
     */
    @PostMapping("/notify-results")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Map<String, Object>> notifyResults(@RequestBody Map<String, Integer> body) {
        int year = body.getOrDefault("year", 0);
        int month = body.getOrDefault("month", 0);

        // アプリ内通知を生成
        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        int inAppCount = 0;
        for (PracticeSession session : sessions) {
            List<PracticeParticipant> processed = practiceParticipantRepository
                    .findBySessionId(session.getId())
                    .stream()
                    .filter(p -> p.getStatus() == ParticipantStatus.WON
                            || p.getStatus() == ParticipantStatus.WAITLISTED)
                    .collect(Collectors.toList());
            notificationService.createLotteryResultNotifications(processed);
            inAppCount += processed.size();
        }

        // LINE通知を送信
        var lineResult = lineNotificationService.sendLotteryResults(year, month);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("inAppCount", inAppCount);
        result.put("lineSent", lineResult.getSentCount());
        result.put("lineFailed", lineResult.getFailedCount());
        result.put("lineSkipped", lineResult.getSkippedCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 抽選実行履歴取得
     */
    @GetMapping("/executions")
    public ResponseEntity<List<LotteryExecution>> getLotteryExecutions(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(
                lotteryExecutionRepository.findByTargetYearAndTargetMonth(year, month));
    }

}
