package com.karuta.matchtracker.service;

import com.karuta.matchtracker.entity.LineMessageLog;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import com.karuta.matchtracker.entity.LineMessageLog.MessageStatus;
import com.karuta.matchtracker.repository.LineMessageLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
}

