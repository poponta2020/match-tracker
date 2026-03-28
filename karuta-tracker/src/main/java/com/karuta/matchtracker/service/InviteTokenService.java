package com.karuta.matchtracker.service;

import com.karuta.matchtracker.dto.InviteTokenResponse;
import com.karuta.matchtracker.dto.PublicRegisterRequest;
import com.karuta.matchtracker.dto.PlayerDto;
import com.karuta.matchtracker.entity.InviteToken;
import com.karuta.matchtracker.entity.InviteToken.TokenType;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.entity.PlayerOrganization;
import com.karuta.matchtracker.exception.DuplicateResourceException;
import com.karuta.matchtracker.exception.ResourceNotFoundException;
import com.karuta.matchtracker.repository.InviteTokenRepository;
import com.karuta.matchtracker.repository.PlayerOrganizationRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 招待トークンサービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InviteTokenService {

    private final InviteTokenRepository inviteTokenRepository;
    private final PlayerRepository playerRepository;
    private final PlayerOrganizationRepository playerOrganizationRepository;

    /** グループ用トークンの有効期限（時間） */
    private static final int MULTI_USE_EXPIRY_HOURS = 72;

    /** 個人用トークンの有効期限（時間） */
    private static final int SINGLE_USE_EXPIRY_HOURS = 72;

    /**
     * 招待トークンを生成
     *
     * @param type トークン種別
     * @param createdBy 発行者の選手ID
     * @return 生成されたトークン情報
     */
    @Transactional
    public InviteTokenResponse createToken(TokenType type, Long createdBy, Long organizationId) {
        log.info("Creating invite token: type={}, createdBy={}, organizationId={}", type, createdBy, organizationId);

        int expiryHours = type == TokenType.MULTI_USE ? MULTI_USE_EXPIRY_HOURS : SINGLE_USE_EXPIRY_HOURS;

        InviteToken token = InviteToken.builder()
                .token(UUID.randomUUID().toString())
                .type(type)
                .expiresAt(JstDateTimeUtil.now().plusHours(expiryHours))
                .createdBy(createdBy)
                .organizationId(organizationId)
                .build();

        InviteToken saved = inviteTokenRepository.save(token);
        log.info("Successfully created invite token: {}", saved.getToken());

        return InviteTokenResponse.fromEntity(saved);
    }

    /**
     * トークンの有効性を検証
     *
     * @param token トークン文字列
     * @return トークン情報
     */
    public InviteTokenResponse validateToken(String token) {
        log.debug("Validating invite token: {}", token);

        InviteToken inviteToken = inviteTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("InviteToken", "token", token));

        if (!inviteToken.isValid()) {
            throw new IllegalStateException("この招待リンクは無効または期限切れです");
        }

        return InviteTokenResponse.fromEntity(inviteToken);
    }

    /**
     * 招待トークンを使って選手を登録
     *
     * @param request 登録リクエスト
     * @return 登録された選手情報
     */
    @Transactional
    public PlayerDto registerWithToken(PublicRegisterRequest request) {
        log.info("Registering player with invite token: name={}", request.getName());

        // トークン検証
        InviteToken inviteToken = inviteTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException("InviteToken", "token", request.getToken()));

        if (!inviteToken.isValid()) {
            throw new IllegalStateException("この招待リンクは無効または期限切れです");
        }

        // 名前の重複チェック
        playerRepository.findByNameAndActive(request.getName())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("Player", "name", request.getName());
                });

        // 選手を登録
        Player player = request.toEntity();
        Player saved = playerRepository.save(player);

        // SINGLE_USE トークンは使用済みにする
        if (inviteToken.getType() == TokenType.SINGLE_USE) {
            inviteToken.setUsedAt(JstDateTimeUtil.now());
            inviteToken.setUsedBy(saved.getId());
            inviteTokenRepository.save(inviteToken);
        }

        // トークンの団体にplayer_organizationsレコードを作成
        playerOrganizationRepository.save(PlayerOrganization.builder()
                .playerId(saved.getId())
                .organizationId(inviteToken.getOrganizationId())
                .build());

        log.info("Successfully registered player with id: {} via invite token (org: {})", saved.getId(), inviteToken.getOrganizationId());
        return PlayerDto.fromEntity(saved);
    }
}
