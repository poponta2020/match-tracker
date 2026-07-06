package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeDeletionCandidate;
import com.karuta.matchtracker.entity.DensukeDeletionCandidate.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DensukeDeletionCandidateRepository extends JpaRepository<DensukeDeletionCandidate, Long> {

    List<DensukeDeletionCandidate> findByDensukeUrlIdAndStatus(Long densukeUrlId, Status status);

    List<DensukeDeletionCandidate> findByOrganizationIdAndStatusOrderByDetectedAtDesc(Long organizationId, Status status);

    List<DensukeDeletionCandidate> findByOrganizationIdAndSessionDateAndStatus(
            Long organizationId, LocalDate sessionDate, Status status);

    List<DensukeDeletionCandidate> findByOrganizationIdInAndSessionDateBetweenAndStatus(
            List<Long> organizationIds, LocalDate startDate, LocalDate endDate, Status status);

    List<DensukeDeletionCandidate> findByOrganizationIdAndSessionDateAndStatusIn(
            Long organizationId, LocalDate sessionDate, List<Status> statuses);

    List<DensukeDeletionCandidate> findByOrganizationIdInAndSessionDateBetweenAndStatusIn(
            List<Long> organizationIds, LocalDate startDate, LocalDate endDate, List<Status> statuses);
}
