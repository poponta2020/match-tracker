package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.RoomAvailabilityCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 部屋空き状況キャッシュリポジトリ
 */
@Repository
public interface RoomAvailabilityCacheRepository extends JpaRepository<RoomAvailabilityCache, Long> {

    /**
     * 部屋名・日付・時間帯で空き状況を取得
     */
    Optional<RoomAvailabilityCache> findByRoomNameAndTargetDateAndTimeSlot(
            String roomName, LocalDate targetDate, String timeSlot);
}
