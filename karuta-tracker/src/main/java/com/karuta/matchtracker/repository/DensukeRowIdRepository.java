package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeRowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DensukeRowIdRepository extends JpaRepository<DensukeRowId, Long> {

    Optional<DensukeRowId> findByDensukeUrlIdAndSessionDateAndMatchNumber(
            Long densukeUrlId, LocalDate sessionDate, Integer matchNumber);

    List<DensukeRowId> findByDensukeUrlId(Long densukeUrlId);
}
