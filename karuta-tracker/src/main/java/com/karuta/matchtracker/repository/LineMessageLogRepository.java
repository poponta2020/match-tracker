package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineMessageLog;
import com.karuta.matchtracker.entity.LineMessageLog.LineNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
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

    /** 重複送信チェック（同一プレイヤー・同一種別・同一dedupeKey・指定期間内） */
    @Query("""
        SELECT COUNT(l) > 0 FROM LineMessageLog l
        WHERE l.playerId = :playerId
        AND l.notificationType = :type
        AND l.dedupeKey = :dedupeKey
        AND l.status = 'SUCCESS'
        AND l.sentAt >= :since
        """)
    boolean existsSuccessfulSinceWithDedupeKey(
        @Param("playerId") Long playerId,
        @Param("type") LineNotificationType type,
        @Param("dedupeKey") String dedupeKey,
        @Param("since") LocalDateTime since
    );

    /**
     * 原子的に送信権を確保する（INSERT ... ON CONFLICT DO NOTHING）。
     * RESERVED ステータスで挿入し、送信成功後に markReservationSucceeded で SUCCESS に更新する。
     * 同一 (player_id, notification_type, dedupe_key, 日付) で SUCCESS または RESERVED ログが存在する場合は挿入されない。
     * sent_at にはサービス層から JstDateTimeUtil.now() を渡し、他のJPA保存経路と時刻基準を統一する。
     * @return 挿入された行数（1=送信権確保成功、0=既に送信済み）
     */
    @Modifying
    @Query(value = """
        INSERT INTO line_message_log (line_channel_id, player_id, notification_type, message_content, status, dedupe_key, sent_at)
        VALUES (:channelId, :playerId, CAST(:type AS VARCHAR), :message, 'RESERVED', :dedupeKey, :sentAt)
        ON CONFLICT (player_id, notification_type, dedupe_key, (sent_at::date))
        WHERE status IN ('SUCCESS', 'RESERVED') AND dedupe_key IS NOT NULL
        DO NOTHING
        """, nativeQuery = true)
    int tryAcquireSendRight(@Param("channelId") Long channelId,
                            @Param("playerId") Long playerId,
                            @Param("type") String type,
                            @Param("message") String message,
                            @Param("dedupeKey") String dedupeKey,
                            @Param("sentAt") LocalDateTime sentAt);

    /**
     * 送信成功時に予約レコードのステータスを SUCCESS に更新する。
     * tryAcquireSendRight で確保した RESERVED レコードを SUCCESS に変更し、
     * 以降の重複送信を防止する。
     * sentDate にはサービス層から JstDateTimeUtil.today() を渡し、JST日付基準で照合する。
     */
    @Modifying
    @Query(value = """
        UPDATE line_message_log
        SET status = 'SUCCESS'
        WHERE player_id = :playerId
        AND notification_type = CAST(:type AS VARCHAR)
        AND dedupe_key = :dedupeKey
        AND status = 'RESERVED'
        AND sent_at::date = :sentDate
        """, nativeQuery = true)
    int markReservationSucceeded(@Param("playerId") Long playerId,
                                 @Param("type") String type,
                                 @Param("dedupeKey") String dedupeKey,
                                 @Param("sentDate") LocalDate sentDate);

    /**
     * 送信失敗時に予約レコードのステータスを FAILED に更新する。
     * tryAcquireSendRight で確保した RESERVED レコードを FAILED に変更し、
     * 次回のリトライを可能にする。
     * sentDate にはサービス層から JstDateTimeUtil.today() を渡し、JST日付基準で照合する。
     */
    @Modifying
    @Query(value = """
        UPDATE line_message_log
        SET status = 'FAILED', error_message = :errorMessage
        WHERE player_id = :playerId
        AND notification_type = CAST(:type AS VARCHAR)
        AND dedupe_key = :dedupeKey
        AND status = 'RESERVED'
        AND sent_at::date = :sentDate
        """, nativeQuery = true)
    int markReservationFailed(@Param("playerId") Long playerId,
                              @Param("type") String type,
                              @Param("dedupeKey") String dedupeKey,
                              @Param("errorMessage") String errorMessage,
                              @Param("sentDate") LocalDate sentDate);

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
