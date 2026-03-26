package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.ByeActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ByeActivityRepository extends JpaRepository<ByeActivity, Long> {

    @Query("SELECT b FROM ByeActivity b WHERE b.sessionDate = :sessionDate AND b.deletedAt IS NULL ORDER BY b.matchNumber")
    List<ByeActivity> findBySessionDateOrderByMatchNumber(@Param("sessionDate") LocalDate sessionDate);

    @Query("SELECT b FROM ByeActivity b WHERE b.sessionDate = :sessionDate AND b.matchNumber = :matchNumber AND b.deletedAt IS NULL")
    List<ByeActivity> findBySessionDateAndMatchNumber(@Param("sessionDate") LocalDate sessionDate, @Param("matchNumber") Integer matchNumber);

    @Query("SELECT b FROM ByeActivity b WHERE b.playerId = :playerId AND b.deletedAt IS NULL ORDER BY b.sessionDate DESC")
    List<ByeActivity> findByPlayerIdOrderBySessionDateDesc(@Param("playerId") Long playerId);

    @Query("SELECT b FROM ByeActivity b WHERE b.playerId = :playerId AND b.activityType = :activityType AND b.deletedAt IS NULL ORDER BY b.sessionDate DESC")
    List<ByeActivity> findByPlayerIdAndActivityTypeOrderBySessionDateDesc(@Param("playerId") Long playerId, @Param("activityType") ActivityType activityType);

    @Query("SELECT b FROM ByeActivity b WHERE b.sessionDate = :sessionDate AND b.matchNumber = :matchNumber AND b.playerId = :playerId AND b.deletedAt IS NULL")
    Optional<ByeActivity> findBySessionDateAndMatchNumberAndPlayerId(
            @Param("sessionDate") LocalDate sessionDate, @Param("matchNumber") Integer matchNumber, @Param("playerId") Long playerId);

    @Modifying
    @Query("UPDATE ByeActivity b SET b.deletedAt = CURRENT_TIMESTAMP WHERE b.sessionDate = :sessionDate AND b.matchNumber = :matchNumber AND b.deletedAt IS NULL")
    void softDeleteBySessionDateAndMatchNumber(@Param("sessionDate") LocalDate sessionDate, @Param("matchNumber") Integer matchNumber);
}
