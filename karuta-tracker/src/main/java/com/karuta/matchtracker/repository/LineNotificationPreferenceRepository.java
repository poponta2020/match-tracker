package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * LINE通知設定リポジトリ
 */
@Repository
public interface LineNotificationPreferenceRepository extends JpaRepository<LineNotificationPreference, Long> {

    /**
     * プレイヤーIDで取得
     */
    Optional<LineNotificationPreference> findByPlayerId(Long playerId);
}
