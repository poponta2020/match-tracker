package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.entity.ByeActivity;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.ByeActivityRepository;
import com.karuta.matchtracker.repository.PracticeSessionRepository;
import com.karuta.matchtracker.service.ByeActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bye-activities")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class ByeActivityController {

    private final ByeActivityService byeActivityService;
    private final ByeActivityRepository byeActivityRepository;
    private final PracticeSessionRepository practiceSessionRepository;

    /**
     * 指定日の抜け番活動を取得（matchNumber指定時はその試合のみ）
     */
    @GetMapping
    public ResponseEntity<List<ByeActivityDto>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer matchNumber) {
        log.info("抜け番活動取得: date={}, matchNumber={}", date, matchNumber);
        List<ByeActivityDto> result;
        if (matchNumber != null) {
            result = byeActivityService.getByDateAndMatch(date, matchNumber);
        } else {
            result = byeActivityService.getByDate(date);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 選手別の活動履歴を取得（集計用）
     */
    @GetMapping("/player/{playerId}")
    public ResponseEntity<List<ByeActivityDto>> getByPlayer(
            @PathVariable Long playerId,
            @RequestParam(required = false) ActivityType type) {
        log.info("選手別抜け番活動取得: playerId={}, type={}", playerId, type);
        List<ByeActivityDto> result = byeActivityService.getByPlayer(playerId, type);
        return ResponseEntity.ok(result);
    }

    /**
     * 抜け番活動を作成（本人入力）
     * Service層でplayerId == userIdの検証を実施
     */
    @PostMapping
    public ResponseEntity<ByeActivityDto> create(
            @Valid @RequestBody ByeActivityCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("抜け番活動作成: {}", request);
        Long userId = (Long) httpRequest.getAttribute("currentUserId");
        ByeActivityDto created = byeActivityService.create(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 抜け番活動を一括作成（管理者用）
     */
    @PostMapping("/batch")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<ByeActivityDto>> createBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            @Valid @RequestBody List<ByeActivityBatchItemRequest> items,
            HttpServletRequest httpRequest) {
        log.info("抜け番活動一括作成: date={}, matchNumber={}, count={}", date, matchNumber, items.size());
        validateAdminScopeByDate(date, httpRequest);
        Long userId = (Long) httpRequest.getAttribute("currentUserId");
        List<ByeActivityDto> created = byeActivityService.createBatch(date, matchNumber, items, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * 抜け番活動を更新（本人 or 管理者）
     * 本人の場合はplayerIdの一致を検証
     */
    @PutMapping("/{id}")
    public ResponseEntity<ByeActivityDto> update(
            @PathVariable Long id,
            @Valid @RequestBody ByeActivityUpdateRequest request,
            HttpServletRequest httpRequest) {
        log.info("抜け番活動更新: id={}, type={}", id, request.getActivityType());
        Long userId = (Long) httpRequest.getAttribute("currentUserId");
        ByeActivityDto updated = byeActivityService.update(id, request, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 抜け番活動を削除（管理者のみ）
     */
    @DeleteMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        log.info("抜け番活動削除: id={}", id);
        ByeActivity activity = byeActivityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ByeActivity", id));
        validateAdminScopeByDate(activity.getSessionDate(), httpRequest);
        byeActivityService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * ADMINスコープ検証（日付ベース）
     */
    private void validateAdminScopeByDate(LocalDate date, HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute("currentUserRole");
        if (!"ADMIN".equals(role)) return;

        Long adminOrgId = (Long) httpRequest.getAttribute("adminOrganizationId");
        if (adminOrgId == null) {
            throw new ForbiddenException("他団体の抜け番活動は操作できません");
        }
        practiceSessionRepository.findBySessionDateAndOrganizationId(date, adminOrgId)
                .orElseThrow(() -> new ForbiddenException("他団体の抜け番活動は操作できません"));
    }
}
