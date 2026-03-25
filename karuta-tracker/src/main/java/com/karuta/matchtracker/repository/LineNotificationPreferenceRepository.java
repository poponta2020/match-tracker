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

    /** プレイヤーの通知設定を取得 */
    Optional<LineNotificationPreference> findByPlayerId(Long playerId);

    /** 指定通知種別がONのプレイヤーIDリストを取得 */
    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.lotteryResult = true")
    List<Long> findPlayerIdsWithLotteryResultEnabled();

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.waitlistOffer = true")
    List<Long> findPlayerIdsWithWaitlistOfferEnabled();

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.offerExpired = true")
    List<Long> findPlayerIdsWithOfferExpiredEnabled();

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.matchPairing = true")
    List<Long> findPlayerIdsWithMatchPairingEnabled();

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.practiceReminder = true")
    List<Long> findPlayerIdsWithPracticeReminderEnabled();

    @Query("SELECT p.playerId FROM LineNotificationPreference p WHERE p.deadlineReminder = true")
    List<Long> findPlayerIdsWithDeadlineReminderEnabled();
}
