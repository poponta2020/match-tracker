package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DensukeDeletionCandidateRepository extends JpaRepository<DensukeDeletionCandidate, Long> {

    Optional<DensukeDeletionCandidate> findByDensukeUrlIdAndSessionDateAndMatchNumber(
            Long densukeUrlId, LocalDate sessionDate, Integer matchNumber);

    List<DensukeDeletionCandidate> findByDensukeUrlIdAndStatus(Long densukeUrlId, Status status);

    boolean existsByDensukeUrlIdInAndStatus(List<Long> densukeUrlIds, Status status);

    List<DensukeDeletionCandidate> findByOrganizationIdAndStatusOrderByDetectedAtDesc(Long organizationId, Status status);

    List<DensukeDeletionCandidate> findByOrganizationIdAndSessionDateAndStatus(
            Long organizationId, LocalDate sessionDate, Status status);

    List<DensukeDeletionCandidate> findByOrganizationIdInAndSessionDateBetweenAndStatus(
            List<Long> organizationIds, LocalDate startDate, LocalDate endDate, Status status);
}
