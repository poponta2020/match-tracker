package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PushNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Web Push通知設定リポジトリ
 */
@Repository
public interface PushNotificationPreferenceRepository extends JpaRepository<PushNotificationPreference, Long> {

    /** プレイヤーのWeb Push通知設定を全団体分取得 */
    List<PushNotificationPreference> findByPlayerId(Long playerId);

    /** プレイヤーの団体別Web Push通知設定を取得 */
    Optional<PushNotificationPreference> findByPlayerIdAndOrganizationId(Long playerId, Long organizationId);
}
