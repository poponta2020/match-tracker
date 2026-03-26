package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.ByeActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bye-activities")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class ByeActivityController {

    private final ByeActivityService byeActivityService;

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
    public ResponseEntity<ByeActivityDto> create(@Valid @RequestBody ByeActivityCreateRequest request) {
        log.info("抜け番活動作成: {}", request);
        // TODO: UserDetailsから取得。現在はリクエストのplayerIdをuserIdとして使用
        Long userId = request.getPlayerId();
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
            @Valid @RequestBody List<ByeActivityBatchItemRequest> items) {
        log.info("抜け番活動一括作成: date={}, matchNumber={}, count={}", date, matchNumber, items.size());
        Long userId = 1L; // TODO: UserDetailsから取得
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
            @Valid @RequestBody ByeActivityUpdateRequest request) {
        log.info("抜け番活動更新: id={}, type={}", id, request.getActivityType());
        // TODO: UserDetailsから取得。現在は対象レコードのplayerIdをuserIdとして使用
        Long userId = byeActivityService.getPlayerIdForActivity(id);
        ByeActivityDto updated = byeActivityService.update(id, request, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 抜け番活動を削除（管理者のみ）
     */
    @DeleteMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("抜け番活動削除: id={}", id);
        byeActivityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
