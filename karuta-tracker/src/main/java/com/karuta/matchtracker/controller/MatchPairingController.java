package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.*;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.MatchPairingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/match-pairings")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class MatchPairingController {

    private final MatchPairingService matchPairingService;

    /**
     * 指定日の対戦組み合わせを取得
     */
    @GetMapping("/date")
    public ResponseEntity<List<MatchPairingDto>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("対戦組み合わせ取得: 日付={}", date);
        List<MatchPairingDto> pairings = matchPairingService.getByDate(date);
        return ResponseEntity.ok(pairings);
    }

    /**
     * 指定日・試合番号の対戦組み合わせを取得
     */
    @GetMapping("/date-and-match")
    public ResponseEntity<List<MatchPairingDto>> getByDateAndMatchNumber(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber) {
        log.info("対戦組み合わせ取得: 日付={}, 試合番号={}", date, matchNumber);
        List<MatchPairingDto> pairings = matchPairingService.getByDateAndMatchNumber(date, matchNumber);
        return ResponseEntity.ok(pairings);
    }

    /**
     * 対戦組み合わせが存在するか確認
     */
    @GetMapping("/exists")
    public ResponseEntity<Boolean> exists(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber) {
        log.info("対戦組み合わせ存在確認: 日付={}, 試合番号={}", date, matchNumber);
        boolean exists = matchPairingService.existsByDateAndMatchNumber(date, matchNumber);
        return ResponseEntity.ok(exists);
    }

    /**
     * 対戦組み合わせを作成
     */
    @PostMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<MatchPairingDto> create(
            @RequestBody MatchPairingCreateRequest request) {
        log.info("対戦組み合わせ作成: {}", request);

        // TODO: UserDetailsからplayerIdを取得する実装が必要
        Long createdBy = 1L; // 仮のID

        MatchPairingDto created = matchPairingService.create(request, createdBy);
        return ResponseEntity.ok(created);
    }

    /**
     * 対戦組み合わせを一括作成
     */
    @PostMapping("/batch")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<List<MatchPairingDto>> createBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber,
            @RequestBody List<MatchPairingCreateRequest> requests) {
        log.info("対戦組み合わせ一括作成: 日付={}, 試合番号={}, 件数={}",
                 date, matchNumber, requests.size());

        // TODO: UserDetailsからplayerIdを取得する実装が必要
        Long createdBy = 1L; // 仮のID

        List<MatchPairingDto> created = matchPairingService.createBatch(date, matchNumber, requests, createdBy);
        return ResponseEntity.ok(created);
    }

    /**
     * 対戦組み合わせを削除
     */
    @DeleteMapping("/{id}")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("対戦組み合わせ削除: ID={}", id);
        matchPairingService.delete(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 指定日・試合番号の対戦組み合わせを削除
     */
    @DeleteMapping("/date-and-match")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<Void> deleteByDateAndMatchNumber(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam Integer matchNumber) {
        log.info("対戦組み合わせ削除: 日付={}, 試合番号={}", date, matchNumber);
        matchPairingService.deleteByDateAndMatchNumber(date, matchNumber);
        return ResponseEntity.ok().build();
    }

    /**
     * 自動マッチングを実行
     */
    @PostMapping("/auto-match")
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN})
    public ResponseEntity<AutoMatchingResult> autoMatch(@RequestBody AutoMatchingRequest request) {
        log.info("自動マッチング実行: {}", request);
        AutoMatchingResult result = matchPairingService.autoMatch(request);
        return ResponseEntity.ok(result);
    }
}
