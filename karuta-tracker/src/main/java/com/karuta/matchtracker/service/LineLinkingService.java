package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineChannelAssignment;
import com.karuta.matchtracker.entity.LineLinkingCode;
import com.karuta.matchtracker.entity.LineLinkingCode.CodeStatus;
import com.karuta.matchtracker.repository.LineChannelAssignmentRepository;
import com.karuta.matchtracker.repository.LineLinkingCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * LINEアカウント紐付け（ワンタイムコード）サービス
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineLinkingService {

    private final LineLinkingCodeRepository lineLinkingCodeRepository;
    private final LineChannelAssignmentRepository lineChannelAssignmentRepository;
    private final LineChannelService lineChannelService;

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_EXPIRY_MINUTES = 10;
    private static final int MAX_ATTEMPTS = 5;
    private final SecureRandom random = new SecureRandom();

    /**
     * ワンタイムコードを発行する
     */
    @Transactional
    public LineLinkingCode issueCode(Long playerId, Long channelId) {
        // 既存のACTIVEなコードを無効化
        lineLinkingCodeRepository.invalidateAllActiveByPlayerId(playerId);

        String code = generateUniqueCode();

        LineLinkingCode linkingCode = LineLinkingCode.builder()
            .playerId(playerId)
            .lineChannelId(channelId)
            .code(code)
            .expiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES))
            .build();

        lineLinkingCodeRepository.save(linkingCode);
        log.info("Issued linking code for player {} on channel {}", playerId, channelId);
        return linkingCode;
    }

    /**
     * ワンタイムコードを検証し、アカウントを紐付ける
     * @return 紐付け成功ならtrue
     */
    @Transactional
    public VerificationResult verifyCode(String code, String lineUserId, Long channelId) {
        Optional<LineLinkingCode> codeOpt = lineLinkingCodeRepository.findByCodeAndStatus(code, CodeStatus.ACTIVE);
        if (codeOpt.isEmpty()) {
            return VerificationResult.INVALID;
        }

        LineLinkingCode linkingCode = codeOpt.get();

        // チャネルIDの一致確認
        if (!linkingCode.getLineChannelId().equals(channelId)) {
            return VerificationResult.INVALID;
        }

        // 有効期限チェック
        if (linkingCode.isExpired()) {
            linkingCode.setStatus(CodeStatus.EXPIRED);
            lineLinkingCodeRepository.save(linkingCode);
            return VerificationResult.EXPIRED;
        }

        // 試行回数チェック
        linkingCode.setAttemptCount(linkingCode.getAttemptCount() + 1);
        if (linkingCode.isMaxAttemptsReached()) {
            linkingCode.setStatus(CodeStatus.INVALIDATED);
            lineLinkingCodeRepository.save(linkingCode);
            return VerificationResult.MAX_ATTEMPTS;
        }

        // 成功 → line_user_id保存
        linkingCode.setStatus(CodeStatus.USED);
        linkingCode.setUsedAt(LocalDateTime.now());
        lineLinkingCodeRepository.save(linkingCode);

        // チャネルをLINKED状態にする
        lineChannelService.linkChannel(channelId, lineUserId);

        log.info("Successfully linked player {} via code on channel {}", linkingCode.getPlayerId(), channelId);
        return VerificationResult.SUCCESS;
    }

    /**
     * ワンタイムコードを再発行する
     */
    @Transactional
    public LineLinkingCode reissueCode(Long playerId) {
        Optional<LineChannelAssignment> assignmentOpt = lineChannelAssignmentRepository.findActiveByPlayerId(playerId);
        if (assignmentOpt.isEmpty()) {
            throw new IllegalStateException("LINE通知が有効化されていません");
        }

        LineChannelAssignment assignment = assignmentOpt.get();
        if (assignment.getStatus() != LineChannelAssignment.AssignmentStatus.PENDING) {
            throw new IllegalStateException("既にLINE連携が完了しています");
        }

        return issueCode(playerId, assignment.getLineChannelId());
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            String code = generateCode();
            if (lineLinkingCodeRepository.findByCodeAndStatus(code, CodeStatus.ACTIVE).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("ユニークなコードの生成に失敗しました");
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    public enum VerificationResult {
        SUCCESS,
        INVALID,
        EXPIRED,
        MAX_ATTEMPTS
    }
}
