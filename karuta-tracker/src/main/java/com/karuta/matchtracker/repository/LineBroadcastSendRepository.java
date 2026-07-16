package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineBroadcastSend;
import com.karuta.matchtracker.entity.LineBroadcastSend.BroadcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全体LINE配信ログ兼 dedupe リポジトリ。
 *
 * <p>個人通知の {@code LineMessageLogRepository#tryAcquireSendRight} と同型の
 * {@code INSERT ... ON CONFLICT DO NOTHING} により、(配信グループ, セッション) 単位で送信権を原子的に確保する。
 */
@Repository
public interface LineBroadcastSendRepository extends JpaRepository<LineBroadcastSend, Long> {

    /**
     * 原子的に送信権を確保する。
     * RESERVED で挿入し、送信成功後に {@link #markBroadcastSucceeded} で SUCCESS に更新する。
     * 同一 (broadcast_group_id, session_id) で SUCCESS または RESERVED が存在する場合は挿入されない
     * （部分ユニークインデックス idx_lbs_dedupe）。
     *
     * @return 挿入された行数（1=送信権確保成功、0=既に送信済み/送信中）
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO line_broadcast_send (broadcast_group_id, session_id, line_channel_id, recipient_count, status, sent_at)
        VALUES (:groupId, :sessionId, :channelId, :recipientCount, 'RESERVED', :sentAt)
        ON CONFLICT (broadcast_group_id, session_id)
        WHERE status IN ('SUCCESS', 'RESERVED')
        DO NOTHING
        """, nativeQuery = true)
    int tryAcquireBroadcastRight(@Param("groupId") Long groupId,
                                 @Param("sessionId") Long sessionId,
                                 @Param("channelId") Long channelId,
                                 @Param("recipientCount") Integer recipientCount,
                                 @Param("sentAt") LocalDateTime sentAt);

    /**
     * 確保した RESERVED レコードを SUCCESS に確定する。
     * @return 更新された行数（0の場合は不整合の可能性あり）
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE line_broadcast_send
        SET status = 'SUCCESS'
        WHERE broadcast_group_id = :groupId
        AND session_id = :sessionId
        AND status = 'RESERVED'
        """, nativeQuery = true)
    int markBroadcastSucceeded(@Param("groupId") Long groupId,
                               @Param("sessionId") Long sessionId);

    /**
     * 送信失敗時に RESERVED レコードを FAILED に変更する（次回のリトライを可能にする）。
     * @return 更新された行数
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE line_broadcast_send
        SET status = 'FAILED', error_message = :errorMessage
        WHERE broadcast_group_id = :groupId
        AND session_id = :sessionId
        AND status = 'RESERVED'
        """, nativeQuery = true)
    int markBroadcastFailed(@Param("groupId") Long groupId,
                            @Param("sessionId") Long sessionId,
                            @Param("errorMessage") String errorMessage);

    /**
     * 指定時刻より古い RESERVED レコードを FAILED に解放する。
     * プロセス障害等で RESERVED が残留した場合の回復経路（送信権確保後に落ちた回を
     * 同一ウィンドウ内で再試行可能にする）。cutoff は配信ウィンドウ幅より短く設定すること。
     * @return 解放された行数
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE line_broadcast_send
        SET status = 'FAILED', error_message = 'RESERVED timeout - auto-released'
        WHERE status = 'RESERVED'
        AND sent_at < :cutoff
        """, nativeQuery = true)
    int releaseStaleBroadcastReservations(@Param("cutoff") LocalDateTime cutoff);

    /** (グループ, セッション) に SUCCESS または RESERVED が既に存在するか（冪等の短絡判定） */
    boolean existsByBroadcastGroupIdAndSessionIdAndStatusIn(
            Long broadcastGroupId, Long sessionId, List<BroadcastStatus> statuses);

    /** (グループ, セッション) に指定時刻以降の SKIPPED 記録があるか（SKIPPED ログの重複記録抑止） */
    boolean existsByBroadcastGroupIdAndSessionIdAndStatusAndSentAtGreaterThanEqual(
            Long broadcastGroupId, Long sessionId, BroadcastStatus status, LocalDateTime since);

    /** 配信ログ（新しい順・管理画面用） */
    List<LineBroadcastSend> findTop100ByBroadcastGroupIdOrderBySentAtDesc(Long broadcastGroupId);
}
