package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineConfirmationToken;
import com.karuta.matchtracker.repository.LineConfirmationTokenRepository;
import com.karuta.matchtracker.util.JstDateTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LINE操作確認トークンの発行・検証・消費を担当するサービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineConfirmationService {

    private final LineConfirmationTokenRepository lineConfirmationTokenRepository;

    private static final int TOKEN_EXPIRY_MINUTES = 5;

    /**
     * 確認トークンを発行する。
     * 発行前に期限切れトークンをすべて削除する。
     *
     * @param action 元のアクション名（waitlist_accept等）
     * @param params 元のpostbackパラメータ（JSON文字列）
     * @param playerId 操作者のプレイヤーID
     * @return 発行されたトークン文字列
     */
    @Transactional
    public String createToken(String action, String params, Long playerId) {
        // 期限切れトークンをすべて削除
        lineConfirmationTokenRepository.deleteByExpiresAtBefore(JstDateTimeUtil.now());

        String token = UUID.randomUUID().toString();
        LocalDateTime now = JstDateTimeUtil.now();

        LineConfirmationToken confirmationToken = LineConfirmationToken.builder()
            .token(token)
            .action(action)
            .params(params)
            .playerId(playerId)
            .expiresAt(now.plusMinutes(TOKEN_EXPIRY_MINUTES))
            .build();

        lineConfirmationTokenRepository.save(confirmationToken);
        log.info("Issued confirmation token for player {}, action={}", playerId, action);
        return token;
    }

    /**
     * 確認トークンを検証・消費し、元のアクションとパラメータを返す。
     * 検証NGの場合はIllegalStateExceptionをスローする。
     *
     * @param token トークン文字列
     * @param playerId 操作者のプレイヤーID
     * @return 元のアクションとパラメータを含むLineConfirmationToken
     * @throws IllegalStateException トークンが無効な場合
     */
    @Transactional
    public LineConfirmationToken consumeToken(String token, Long playerId) {
        LineConfirmationToken confirmationToken = lineConfirmationTokenRepository.findByToken(token)
            .orElseThrow(() -> new IllegalStateException("この確認は期限切れです。もう一度操作してください。"));

        if (confirmationToken.isUsed()) {
            throw new IllegalStateException("この確認は期限切れです。もう一度操作してください。");
        }

        if (confirmationToken.isExpired()) {
            throw new IllegalStateException("この確認は期限切れです。もう一度操作してください。");
        }

        if (!confirmationToken.getPlayerId().equals(playerId)) {
            throw new IllegalStateException("この確認は期限切れです。もう一度操作してください。");
        }

        // トークンを使用済みにする
        confirmationToken.setUsedAt(JstDateTimeUtil.now());
        lineConfirmationTokenRepository.save(confirmationToken);

        log.info("Consumed confirmation token for player {}, action={}", playerId, confirmationToken.getAction());
        return confirmationToken;
    }
}
