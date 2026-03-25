package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.LotteryExecution;
import com.karuta.matchtracker.entity.LotteryExecution.ExecutionType;
import com.karuta.matchtracker.entity.ParticipantStatus;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.PracticeParticipant;
import com.karuta.matchtracker.entity.PracticeSession;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.LotteryExecutionRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.repository.PracticeParticipantRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.LineNotificationService;
import com.karuta.matchtracker.service.LotteryService;
import com.karuta.matchtracker.service.PracticeSessionService;
import com.karuta.matchtracker.service.WaitlistPromotionService;
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
    private final PracticeParticipantRepository practiceParticipantRepository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final LotteryExecutionRepository lotteryExecutionRepository;
    private final PlayerRepository playerRepository;

    /**
     * 手動抽選実行
     */
    @PostMapping("/execute")
    @RequireRole(Role.SUPER_ADMIN)
    public ResponseEntity<LotteryExecution> executeLottery(@Valid @RequestBody LotteryExecutionRequest request) {
        Long currentUserId = 1L; // TODO: 認証から取得
        LotteryExecution result = lotteryService.executeLottery(
                request.getYear(), request.getMonth(), currentUserId, ExecutionType.MANUAL);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * セッション再抽選
     */
    @PostMapping("/re-execute/{sessionId}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<LotteryExecution> reExecuteLottery(@PathVariable Long sessionId) {
        Long currentUserId = 1L; // TODO: 認証から取得
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
            results.add(buildLotteryResult(session));
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
        return ResponseEntity.ok(buildLotteryResult(session));
    }

    /**
     * 自分の抽選結果取得
     */
    @GetMapping("/my-results")
    public ResponseEntity<List<LotteryResultDto>> getMyLotteryResults(
            @RequestParam int year, @RequestParam int month, @RequestParam Long playerId) {

        List<PracticeSession> sessions = practiceSessionRepository.findByYearAndMonth(year, month);
        List<LotteryResultDto> results = new ArrayList<>();

        for (PracticeSession session : sessions) {
            // プレイヤーが関与しているセッションのみ
            boolean involved = practiceParticipantRepository.existsBySessionIdAndPlayerId(
                    session.getId(), playerId);
            if (involved) {
                results.add(buildLotteryResult(session));
            }
        }

        return ResponseEntity.ok(results);
    }

    /**
     * 参加キャンセル
     */
    @PostMapping("/cancel")
    public ResponseEntity<Map<String, String>> cancelParticipation(@Valid @RequestBody CancelRequest request) {
        ParticipantStatus status = waitlistPromotionService.cancelParticipation(request.getParticipantId());
        return ResponseEntity.ok(Map.of("status", status.name()));
    }

    /**
     * 繰り上げオファーへの応答
     */
    @PostMapping("/respond-offer")
    public ResponseEntity<Map<String, String>> respondToOffer(@Valid @RequestBody OfferResponseRequest request) {
        // 応答前にparticipant情報を取得（応答後はステータスが変わるため）
        PracticeParticipant participant = practiceParticipantRepository.findById(request.getParticipantId())
                .orElse(null);

        waitlistPromotionService.respondToOffer(request.getParticipantId(), request.getAccept());

        // Webアプリから応答した場合、LINEに確認通知を送信
        if (participant != null) {
            lineNotificationService.sendOfferResponseConfirmation(participant, request.getAccept());
        }

        return ResponseEntity.ok(Map.of("result", request.getAccept() ? "accepted" : "declined"));
    }

    /**
     * キャンセル待ち状況取得
     */
    @GetMapping("/waitlist-status")
    public ResponseEntity<WaitlistStatusDto> getWaitlistStatus(@RequestParam Long playerId) {
        List<PracticeParticipant> waitlisted = practiceParticipantRepository.findByPlayerId(playerId)
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
     * 抽選実行履歴取得
     */
    @GetMapping("/executions")
    public ResponseEntity<List<LotteryExecution>> getLotteryExecutions(
            @RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(
                lotteryExecutionRepository.findByTargetYearAndTargetMonth(year, month));
    }

    // ============================================================
    // Private helpers
    // ============================================================

    private LotteryResultDto buildLotteryResult(PracticeSession session) {
        List<PracticeParticipant> participants = practiceParticipantRepository
                .findBySessionIdOrderByMatchAndStatus(session.getId());

        // プレイヤー情報をまとめて取得
        Set<Long> playerIds = participants.stream()
                .map(PracticeParticipant::getPlayerId)
                .collect(Collectors.toSet());
        Map<Long, Player> playersMap = playerRepository.findAllById(playerIds)
                .stream().collect(Collectors.toMap(Player::getId, p -> p));

        // 試合番号でグループ化
        Map<Integer, List<PracticeParticipant>> byMatch = participants.stream()
                .filter(p -> p.getMatchNumber() != null)
                .collect(Collectors.groupingBy(PracticeParticipant::getMatchNumber, TreeMap::new, Collectors.toList()));

        Map<Integer, LotteryResultDto.MatchResult> matchResults = new TreeMap<>();
        int capacity = session.getCapacity() != null ? session.getCapacity() : Integer.MAX_VALUE;

        for (Map.Entry<Integer, List<PracticeParticipant>> entry : byMatch.entrySet()) {
            List<LotteryResultDto.ParticipantResult> winners = new ArrayList<>();
            List<LotteryResultDto.ParticipantResult> waitlisted = new ArrayList<>();

            for (PracticeParticipant p : entry.getValue()) {
                Player player = playersMap.get(p.getPlayerId());
                if (player == null) continue;

                LotteryResultDto.ParticipantResult result = LotteryResultDto.ParticipantResult.builder()
                        .playerId(p.getPlayerId())
                        .playerName(player.getName())
                        .kyuRank(player.getKyuRank())
                        .danRank(player.getDanRank())
                        .status(p.getStatus())
                        .waitlistNumber(p.getWaitlistNumber())
                        .build();

                if (p.getStatus() == ParticipantStatus.WON) {
                    winners.add(result);
                } else if (p.getStatus() == ParticipantStatus.WAITLISTED
                        || p.getStatus() == ParticipantStatus.OFFERED
                        || p.getStatus() == ParticipantStatus.DECLINED) {
                    waitlisted.add(result);
                }
            }

            // キャンセル待ちは番号順にソート
            waitlisted.sort(Comparator.comparing(r -> r.getWaitlistNumber() != null ? r.getWaitlistNumber() : Integer.MAX_VALUE));

            matchResults.put(entry.getKey(), LotteryResultDto.MatchResult.builder()
                    .matchNumber(entry.getKey())
                    .capacity(capacity)
                    .lotteryRequired(entry.getValue().size() > capacity)
                    .winners(winners)
                    .waitlisted(waitlisted)
                    .build());
        }

        return LotteryResultDto.builder()
                .sessionId(session.getId())
                .sessionDate(session.getSessionDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .capacity(session.getCapacity())
                .matchResults(matchResults)
                .build();
    }
}
