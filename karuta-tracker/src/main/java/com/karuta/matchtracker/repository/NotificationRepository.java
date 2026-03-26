package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Notification;
import com.karuta.matchtracker.entity.Notification.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通知リポジトリ
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * 指定プレイヤーの通知を新しい順に取得
     */
    List<Notification> findByPlayerIdOrderByCreatedAtDesc(Long playerId);

    /**
     * 指定プレイヤーの未読通知数を取得
     */
    long countByPlayerIdAndIsReadFalse(Long playerId);

    /**
     * 指定プレイヤーの未読通知を取得
     */
    List<Notification> findByPlayerIdAndIsReadFalseOrderByCreatedAtDesc(Long playerId);

    /**
     * 指定された参照IDリストと通知種別で通知件数を取得（通知送信済みチェック用）
     */
    long countByReferenceIdInAndTypeIn(List<Long> referenceIds, List<NotificationType> types);
}
