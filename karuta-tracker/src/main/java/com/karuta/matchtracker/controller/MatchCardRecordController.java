package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.MatchCardRecordDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.exception.ForbiddenException;
import com.karuta.matchtracker.service.MatchCardRecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 取り札記録（配置＋お手付き詳細）のREST。
 * オーナーはヘッダ X-User-Id（currentUserId）＝記録者本人。他人分は扱わない。
 */
@RestController
@RequestMapping("/api/matches/{matchId}/card-record")
@RequiredArgsConstructor
public class MatchCardRecordController {

    private final MatchCardRecordService service;

    /** 自分自身の取り札記録を取得 */
    @GetMapping
    public ResponseEntity<MatchCardRecordDto> getRecord(
            @PathVariable Long matchId,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId == null) {
            throw new ForbiddenException("ログインが必要です");
        }
        return ResponseEntity.ok(service.getRecord(matchId, currentUserId));
    }

    /** 自分自身の取り札記録を保存（全置換） */
    @PutMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<MatchCardRecordDto> saveRecord(
            @PathVariable Long matchId,
            @RequestBody MatchCardRecordDto request,
            HttpServletRequest httpRequest) {
        Long currentUserId = (Long) httpRequest.getAttribute("currentUserId");
        if (currentUserId == null) {
            throw new ForbiddenException("ログインが必要です");
        }
        return ResponseEntity.ok(service.saveRecord(matchId, currentUserId, request));
    }
}
