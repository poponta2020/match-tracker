package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineMessageLog;
import com.karuta.matchtracker.entity.LineNotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * LINE送信ログリポジトリ
 */
@Repository
public interface LineMessageLogRepository extends JpaRepository<LineMessageLog, Long> {

    /**
     * 重複送信チェック: 同一種別・同一プレイヤー・同一参照先で一定期間内に送信済みか
     */
    boolean existsByPlayerIdAndNotificationTypeAndReferenceIdAndSentAtAfter(
            Long playerId, LineNotificationType notificationType, Long referenceId, LocalDateTime after);

    /**
     * チャネルの指定期間内の送信ログを取得
     */
    List<LineMessageLog> findByLineChannelIdAndSentAtAfterOrderBySentAtDesc(
            Long lineChannelId, LocalDateTime after);
}
