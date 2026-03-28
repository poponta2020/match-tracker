package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.LineNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * LINE通知設定リポジトリ
 */
@Repository
public interface LineNotificationPreferenceRepository extends JpaRepository<LineNotificationPreference, Long> {

    /** プレイヤーの通知設定を全団体分取得 */
    List<LineNotificationPreference> findByPlayerId(Long playerId);

    /** プレイヤーの団体別通知設定を取得 */
    Optional<LineNotificationPreference> findByPlayerIdAndOrganizationId(Long playerId, Long organizationId);

    /** 指定通知種別がONのプレイヤーIDリストを取得（団体指定） */
    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.lotteryResult = true AND p.organizationId = :orgId")
    List<Long> findPlayerIdsWithLotteryResultEnabled(@Param("orgId") Long organizationId);

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.waitlistOffer = true AND p.organizationId = :orgId")
    List<Long> findPlayerIdsWithWaitlistOfferEnabled(@Param("orgId") Long organizationId);

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.offerExpired = true AND p.organizationId = :orgId")
    List<Long> findPlayerIdsWithOfferExpiredEnabled(@Param("orgId") Long organizationId);

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.matchPairing = true AND p.organizationId = :orgId")
    List<Long> findPlayerIdsWithMatchPairingEnabled(@Param("orgId") Long organizationId);

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.practiceReminder = true AND p.organizationId = :orgId")
    List<Long> findPlayerIdsWithPracticeReminderEnabled(@Param("orgId") Long organizationId);

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.deadlineReminder = true AND p.organizationId = :orgId")
    List<Long> findPlayerIdsWithDeadlineReminderEnabled(@Param("orgId") Long organizationId);
}
