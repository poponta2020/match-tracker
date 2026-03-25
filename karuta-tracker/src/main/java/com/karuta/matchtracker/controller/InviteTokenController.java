package com.karuta.matchtracker.controller;

import com.karuta.matchtracker.annotation.RequireRole;
import com.karuta.matchtracker.dto.InviteTokenResponse;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.dto.PublicRegisterRequest;
import com.karuta.matchtracker.entity.InviteToken.TokenType;
import com.karuta.matchtracker.entity.Player.Role;
import com.karuta.matchtracker.service.InviteTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 招待トークン管理のRESTコントローラ
 */
@RestController
@RequestMapping("/api/invite-tokens")
@RequiredArgsConstructor
@Slf4j
public class InviteTokenController {

    private final InviteTokenService inviteTokenService;

    /**
     * 招待トークンを生成（ADMIN以上）
     *
     * @param type トークン種別（MULTI_USE / SINGLE_USE）
     * @param createdBy 発行者の選手ID
     * @return トークン情報
     */
    @PostMapping
    @RequireRole({Role.ADMIN, Role.SUPER_ADMIN})
    public ResponseEntity<InviteTokenResponse> createToken(
            @RequestParam TokenType type,
            @RequestParam Long createdBy) {
        log.info("POST /api/invite-tokens - Creating token: type={}, createdBy={}", type, createdBy);
        InviteTokenResponse response = inviteTokenService.createToken(type, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * トークンの有効性を検証（認証不要）
     *
     * @param token トークン文字列
     * @return トークン情報
     */
    @GetMapping("/validate/{token}")
    public ResponseEntity<InviteTokenResponse> validateToken(@PathVariable String token) {
        log.debug("GET /api/invite-tokens/validate/{} - Validating token", token);
        InviteTokenResponse response = inviteTokenService.validateToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * 招待トークンを使った公開登録（認証不要）
     *
     * @param request 登録リクエスト
     * @return 登録された選手情報
     */
    @PostMapping("/register")
    public ResponseEntity<PlayerDto> registerWithToken(@Valid @RequestBody PublicRegisterRequest request) {
        log.info("POST /api/invite-tokens/register - Registering with invite token: name={}", request.getName());
        PlayerDto player = inviteTokenService.registerWithToken(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(player);
    }
}
