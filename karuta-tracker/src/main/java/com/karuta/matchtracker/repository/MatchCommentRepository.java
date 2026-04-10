package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MatchComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchCommentRepository extends JpaRepository<MatchComment, Long> {

    @Query("SELECT c FROM MatchComment c WHERE c.matchId = :matchId AND c.menteeId = :menteeId AND c.deletedAt IS NULL ORDER BY c.createdAt ASC")
    List<MatchComment> findByMatchIdAndMenteeId(@Param("matchId") Long matchId, @Param("menteeId") Long menteeId);

    @Query("SELECT c FROM MatchComment c WHERE c.id = :id AND c.deletedAt IS NULL")
    Optional<MatchComment> findActiveById(@Param("id") Long id);
}
