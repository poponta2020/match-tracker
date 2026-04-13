package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineMessageLog;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineMessageLog.MessageStatus;
import com.karuta.matchtracker.repository.LineMessageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.karuta.matchtracker.util.JstDateTimeUtil;
import java.time.LocalDateTime;

/**
 * LINE message log persistence service.
 *
 * Logs are written in an isolated transaction so that log write failures
 * never roll back the caller's business transaction.
 */
@Service
@RequiredArgsConstructor
public class LineMessageLogService {

    private final LineMessageLogRepository lineMessageLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long channelId, Long playerId, LineNotificationType type,
                     String message, MessageStatus status, String error) {
        save(channelId, playerId, type, message, status, error, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Long channelId, Long playerId, LineNotificationType type,
                     String message, MessageStatus status, String error, String dedupeKey) {
        lineMessageLogRepository.save(LineMessageLog.builder()
                .lineChannelId(channelId)
                .playerId(playerId)
                .notificationType(type)
                .messageContent(message)
                .status(status)
                .errorMessage(error)
                .dedupeKey(dedupeKey)
                .build());
    }

    @Transactional(readOnly = true)
    public boolean existsSuccessfulSince(Long playerId, LineNotificationType type,
                                         LocalDateTime since) {
        return lineMessageLogRepository.existsSuccessfulSince(playerId, type, since);
    }

    @Transactional(readOnly = true)
    public boolean existsSuccessfulSince(Long playerId, LineNotificationType type,
                                         String dedupeKey, LocalDateTime since) {
        return lineMessageLogRepository.existsSuccessfulSinceWithDedupeKey(
                playerId, type, dedupeKey, since);
    }

    /**
     * 原子的に送信権を確保する。
     * INSERT ... ON CONFLICT DO NOTHING により、同一キーで最初にINSERTできた処理のみがtrueを返す。
     * これにより並行実行時の二重送信を防止する。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAcquireSendRight(Long channelId, Long playerId, LineNotificationType type,
                                       String message, String dedupeKey) {
        return lineMessageLogRepository.tryAcquireSendRight(
                channelId, playerId, type.name(), message, dedupeKey, JstDateTimeUtil.now()) > 0;
    }

    /**
     * tryAcquireSendRight で確保した予約レコードを SUCCESS に変更する。
     * RESERVED → SUCCESS への変更により、送信完了を確定し重複送信を防止する。
     * @return 更新された行数（0の場合は不整合の可能性あり）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markReservationSucceeded(Long playerId, LineNotificationType type,
                                         String dedupeKey) {
        return lineMessageLogRepository.markReservationSucceeded(
                playerId, type.name(), dedupeKey, JstDateTimeUtil.today());
    }

    /**
     * tryAcquireSendRight で確保した予約レコードを FAILED に変更する。
     * RESERVED → FAILED への変更により、次回リトライ時に再度送信権を確保できるようになる。
     * @return 更新された行数（0の場合は不整合の可能性あり）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markReservationFailed(Long playerId, LineNotificationType type,
                                      String dedupeKey, String errorMessage) {
        return lineMessageLogRepository.markReservationFailed(
                playerId, type.name(), dedupeKey, errorMessage, JstDateTimeUtil.today());
    }

    /**
     * 指定時刻より古い RESERVED レコードを FAILED に解放する。
     * プロセス障害等で RESERVED が残留した場合の回復経路。
     * @return 解放された行数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int releaseStaleReservations(LocalDateTime cutoff) {
        return lineMessageLogRepository.releaseStaleReservations(cutoff);
    }
}

