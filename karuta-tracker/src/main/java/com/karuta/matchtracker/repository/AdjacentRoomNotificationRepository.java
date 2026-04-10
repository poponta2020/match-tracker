package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.AdjacentRoomNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 隣室通知重複防止リポジトリ
 */
@Repository
public interface AdjacentRoomNotificationRepository extends JpaRepository<AdjacentRoomNotification, Long> {

    /**
     * 指定セッション・残り人数の段階で通知済みかどうかを判定
     */
    boolean existsBySessionIdAndRemainingCount(Long sessionId, Integer remainingCount);
}
