package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PushNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Web Push通知設定リポジトリ
 */
@Repository
public interface PushNotificationPreferenceRepository extends JpaRepository<PushNotificationPreference, Long> {

    /** プレイヤーのWeb Push通知設定を取得 */
    Optional<PushNotificationPreference> findByPlayerId(Long playerId);
}
