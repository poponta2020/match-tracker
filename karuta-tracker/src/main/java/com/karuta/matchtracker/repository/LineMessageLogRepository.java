package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineMessageLog;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LINE送信ログリポジトリ
 */
@Repository
public interface LineMessageLogRepository extends JpaRepository<LineMessageLog, Long> {

    /** チャネルの送信ログ一覧 */
    List<LineMessageLog> findByLineChannelIdOrderBySentAtDesc(Long lineChannelId);

    /** 重複送信チェック（同一プレイヤー・同一種別・指定期間内） */
    @Query("""
        SELECT COUNT(l) > 0 FROM LineMessageLog l
        WHERE l.playerId = :playerId
        AND l.notificationType = :type
        AND l.status = 'SUCCESS'
        AND l.sentAt >= :since
        """)
    boolean existsSuccessfulSince(
        @Param("playerId") Long playerId,
        @Param("type") LineNotificationType type,
        @Param("since") LocalDateTime since
    );

    /** チャネルの当月送信成功数を集計 */
    @Query("""
        SELECT COUNT(l) FROM LineMessageLog l
        WHERE l.lineChannelId = :channelId
        AND l.status = 'SUCCESS'
        AND l.sentAt >= :monthStart
        """)
    long countSuccessfulSinceForChannel(
        @Param("channelId") Long channelId,
        @Param("monthStart") LocalDateTime monthStart
    );
}
