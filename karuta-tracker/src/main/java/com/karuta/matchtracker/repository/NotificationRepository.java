package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.Notification;
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
}
