package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知リポジトリ
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 指定プレイヤーの通知を新しい順に取得（論理削除済みを除外）
     */
    List<Notification> findByPlayerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long playerId);

    /**
     * 指定プレイヤーの未読通知数を取得（論理削除済みを除外）
     */
    long countByPlayerIdAndIsReadFalseAndDeletedAtIsNull(Long playerId);

    /**
     * 指定プレイヤーの未読通知を取得（論理削除済みを除外）
     */
    List<Notification> findByPlayerIdAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Long playerId);

    /**
     * 指定された参照IDリストと通知種別で通知件数を取得（通知送信済みチェック用）
     */
    long countByReferenceIdInAndTypeIn(List<Long> referenceIds, List<NotificationType> types);

    /**
     * 指定プレイヤー・通知種別で通知を取得（削除済み含む、重複チェック用）
     * 新しい順に返す
     */
    List<Notification> findByPlayerIdAndTypeOrderByCreatedAtDesc(Long playerId, NotificationType type);

    /**
     * 指定プレイヤーの全通知を論理削除する
     */
    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = :now WHERE n.playerId = :playerId AND n.deletedAt IS NULL")
    int softDeleteAllByPlayerId(@Param("playerId") Long playerId, @Param("now") LocalDateTime now);
}
