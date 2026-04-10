package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MentorRelationship;
import com.karuta.matchtracker.entity.MentorRelationship.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MentorRelationshipRepository extends JpaRepository<MentorRelationship, Long> {

    List<MentorRelationship> findByMenteeIdAndStatus(Long menteeId, Status status);

    List<MentorRelationship> findByMentorIdAndStatus(Long mentorId, Status status);

    List<MentorRelationship> findByMentorIdAndStatusIn(Long mentorId, List<Status> statuses);
n    List<MentorRelationship> findByMenteeIdAndStatusIn(Long menteeId, List<Status> statuses);

    boolean existsByMentorIdAndMenteeIdAndOrganizationId(Long mentorId, Long menteeId, Long organizationId);

    Optional<MentorRelationship> findByMentorIdAndMenteeIdAndOrganizationIdAndStatus(
            Long mentorId, Long menteeId, Long organizationId, Status status);
}
