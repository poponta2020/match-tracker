package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.ActivityType;
import com.karuta.matchtracker.entity.ByeActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ByeActivityRepository extends JpaRepository<ByeActivity, Long> {

    List<ByeActivity> findBySessionDateOrderByMatchNumber(LocalDate sessionDate);

    List<ByeActivity> findBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);

    List<ByeActivity> findByPlayerIdOrderBySessionDateDesc(Long playerId);

    List<ByeActivity> findByPlayerIdAndActivityTypeOrderBySessionDateDesc(Long playerId, ActivityType activityType);

    Optional<ByeActivity> findBySessionDateAndMatchNumberAndPlayerId(
            LocalDate sessionDate, Integer matchNumber, Long playerId);

    void deleteBySessionDateAndMatchNumber(LocalDate sessionDate, Integer matchNumber);
}
