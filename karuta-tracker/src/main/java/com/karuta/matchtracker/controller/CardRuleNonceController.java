package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.CardRuleNonceDto;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.CardRuleNonceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 札ルール再生成カウンタ(nonce)のREST。
 * GET: 誰でも参照可（未登録日は0）。PUT: 「札を再生成」時に更新（対戦組み合わせ運用者）。
 */
@RestController
@RequestMapping("/api/card-rule-nonce")
@RequiredArgsConstructor
public class CardRuleNonceController {

    private final CardRuleNonceService service;

    @GetMapping
    public ResponseEntity<CardRuleNonceDto> getNonce(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(new CardRuleNonceDto(date, service.getNonce(date)));
    }

    @PutMapping
    @RequireRole({Role.SUPER_ADMIN, Role.ADMIN, Role.PLAYER})
    public ResponseEntity<CardRuleNonceDto> setNonce(@RequestBody CardRuleNonceDto request) {
        int nonce = service.setNonce(request.getDate(), request.getNonce() == null ? 0 : request.getNonce());
        return ResponseEntity.ok(new CardRuleNonceDto(request.getDate(), nonce));
    }
}
