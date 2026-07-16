package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineChatReservation;
import com.karuta.matchtracker.entity.LineChatReservation.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * LINEチャット予約キューのリポジトリ（line-chat-reserve-broadcast）。
 *
 * <p>(broadcast_group_id, session_id) の非CANCELLED行は常に1件（部分ユニークインデックス
 * {@code idx_lcr_group_session_active}）。20:00バッチの冪等生成は {@link #tryInsertPendingReservation}
 * の {@code ON CONFLICT DO NOTHING} で担保する。
 */
@Repository
public interface LineChatReservationRepository extends JpaRepository<LineChatReservation, Long> {

    /**
     * (グループ, セッション) の active（非CANCELLED）予約を取得する。
     * 部分ユニークインデックスにより高々1件。スケジューラのリコンサイル・フォールバックガードが使う。
     */
    Optional<LineChatReservation> findFirstByBroadcastGroupIdAndSessionIdAndStatusNot(
            Long broadcastGroupId, Long sessionId, ReservationStatus status);

    /**
     * PENDING 予約を冪等に生成する（INSERT ... ON CONFLICT DO NOTHING）。
     * 部分ユニークインデックス idx_lcr_group_session_active により、(グループ, セッション) に
     * 非CANCELLED行が既にあれば挿入されない（20:00バッチの再実行で重複しない）。
     *
     * <p>ネイティブ INSERT は entity ライフサイクル（@PrePersist）を通らないため、NOT NULL の
     * attempt_count/created_at/updated_at を明示設定する。監査時刻は JST 基準で揃えるため呼び出し側の
     * 注入 {@code now}（{@code JstDateTimeUtil.now()}）を渡す（テストの決定論性も担保）。
     *
     * @return 挿入行数（1=新規作成、0=既に active 行が存在）
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        INSERT INTO line_chat_reservations
            (broadcast_group_id, session_id, status, message_text, scheduled_send_at,
             attempt_count, created_at, updated_at)
        VALUES (:groupId, :sessionId, 'PENDING', :messageText, :scheduledSendAt,
             0, :now, :now)
        ON CONFLICT (broadcast_group_id, session_id)
        WHERE status <> 'CANCELLED'
        DO NOTHING
        """, nativeQuery = true)
    int tryInsertPendingReservation(@Param("groupId") Long groupId,
                                    @Param("sessionId") Long sessionId,
                                    @Param("messageText") String messageText,
                                    @Param("scheduledSendAt") LocalDateTime scheduledSendAt,
                                    @Param("now") LocalDateTime now);

    /** 指定ステータスの予約一覧（リコンサイル・アラート集計用）。 */
    List<LineChatReservation> findByStatusIn(List<ReservationStatus> statuses);

    /** 指定ステータスの予約を送信予定時刻の昇順で取得する（ワーカーのポーリング取得順）。 */
    List<LineChatReservation> findByStatusInOrderByScheduledSendAtAsc(List<ReservationStatus> statuses);

    /** 指定ステータスかつ最終更新が cutoff より前の予約（RESERVING 滞留の昇格判定用）。 */
    List<LineChatReservation> findByStatusAndUpdatedAtBefore(ReservationStatus status, LocalDateTime cutoff);

    /** 指定ステータスかつ送信予定が cutoff より前の予約（マージン切れ PENDING の失効判定用）。 */
    List<LineChatReservation> findByStatusAndScheduledSendAtBefore(ReservationStatus status, LocalDateTime cutoff);

    /** 送信予定が未来の active（非CANCELLED）予約（リコンサイルの変更検知対象。過去分＝送信済みは触らない）。 */
    List<LineChatReservation> findByStatusNotAndScheduledSendAtAfter(ReservationStatus status, LocalDateTime after);

    /** (グループ, セッション) に指定ステータスの行が存在するか（取消後の再予約対象かの判定＝CANCELLED 有無）。 */
    boolean existsByBroadcastGroupIdAndSessionIdAndStatus(
            Long broadcastGroupId, Long sessionId, ReservationStatus status);

    /** 管理スコープ（複数団体グループ）の予約を送信予定時刻の新しい順に取得する（管理画面一覧・直近200件）。 */
    List<LineChatReservation> findTop200ByBroadcastGroupIdInOrderByScheduledSendAtDescIdDesc(
            List<Long> broadcastGroupIds);
}
