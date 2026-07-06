package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MatchCardPlacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchCardPlacementRepository extends JpaRepository<MatchCardPlacement, Long> {

    List<MatchCardPlacement> findByMatchIdAndPlayerId(Long matchId, Long playerId);

    void deleteByMatchIdAndPlayerId(Long matchId, Long playerId);
}
