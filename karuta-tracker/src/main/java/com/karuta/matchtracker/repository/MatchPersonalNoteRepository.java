package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MatchPersonalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchPersonalNoteRepository extends JpaRepository<MatchPersonalNote, Long> {

    Optional<MatchPersonalNote> findByMatchIdAndPlayerId(Long matchId, Long playerId);

    List<MatchPersonalNote> findByPlayerIdAndMatchIdIn(Long playerId, List<Long> matchIds);
}
