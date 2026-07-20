package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.AuthToken;
import com.karuta.matchtracker.entity.Player;
import com.karuta.matchtracker.repository.AuthTokenRepository;
import com.karuta.matchtracker.repository.PlayerRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 認証トークンの発行・検証・失効を担うサービス
 *
 * トークンは 32 バイトの乱数を hex 64文字にしたもの（不透明トークン）。
 * DB には SHA-256 ハッシュのみを保存し、生トークンは保存しない。
 *
 * ハッシュに BCrypt を使わない理由: 毎リクエストの照合には重すぎるため。
 * 256bit の乱数には元々総当たり耐性があり、パスワードのような低エントロピー値とは
 * 前提が異なる（BCrypt はパスワードにのみ使う）。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthTokenService {

    /** トークンの有効期間（発行から約1年） */
    private static final int TOKEN_VALIDITY_DAYS = 365;

    /** 生トークンのバイト長。hex 化して 64 文字になる */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final AuthTokenRepository authTokenRepository;
    private final PlayerRepository playerRepository;

    /**
     * 指定選手に新しいトークンを発行する
     *
     * @param player トークンの持ち主
     * @return 生トークン（呼び出し元がクライアントへ返す。以後サーバ側では復元できない）
     */
    @Transactional
    public String issue(Player player) {
        byte[] bytes = new byte[TOKEN_BYTES];
        TOKEN_RANDOM.nextBytes(bytes);
        String rawToken = HEX.formatHex(bytes);

        LocalDateTime now = JstDateTimeUtil.now();
        authTokenRepository.save(AuthToken.builder()
                .playerId(player.getId())
                .tokenHash(hash(rawToken))
                .issuedAt(now)
                .expiresAt(now.plusDays(TOKEN_VALIDITY_DAYS))
                .build());

        log.info("Issued auth token for player id: {}", player.getId());
        return rawToken;
    }

    /**
     * 生トークンを検証し、対応する選手を解決する
     *
     * 有効と判定する条件: トークンが存在し、失効しておらず、期限内であり、
     * かつ持ち主の選手が論理削除されていないこと。
     *
     * @param rawToken 生トークン
     * @return 有効なら選手。無効なら empty
     */
    @Transactional(readOnly = true)
    public Optional<Player> verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }

        return authTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.isValidAt(JstDateTimeUtil.now()))
                .flatMap(token -> playerRepository.findById(token.getPlayerId()))
                .filter(player -> !player.isDeleted());
    }

    /**
     * 指定の生トークンのみを失効させる（ログアウト）
     *
     * @param rawToken 生トークン
     */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        authTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(JstDateTimeUtil.now());
                authTokenRepository.save(token);
                log.info("Revoked auth token for player id: {}", token.getPlayerId());
            }
        });
    }

    /**
     * 指定選手の有効なトークンをすべて失効させる
     * パスワード変更・選手の論理削除で呼ぶ
     *
     * @param playerId 対象の選手ID
     * @return 失効させた件数
     */
    @Transactional
    public int revokeAllForPlayer(Long playerId) {
        if (playerId == null) {
            return 0;
        }
        int revoked = authTokenRepository.revokeAllByPlayerId(playerId, JstDateTimeUtil.now());
        if (revoked > 0) {
            log.info("Revoked {} auth token(s) for player id: {}", revoked, playerId);
        }
        return revoked;
    }

    /**
     * 生トークンを SHA-256 hex に変換する
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は Java 標準で必ず利用可能
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
