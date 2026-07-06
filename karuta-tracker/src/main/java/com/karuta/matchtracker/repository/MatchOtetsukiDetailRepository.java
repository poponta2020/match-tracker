package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.MatchOtetsukiDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchOtetsukiDetailRepository extends JpaRepository<MatchOtetsukiDetail, Long> {

    List<MatchOtetsukiDetail> findByMatchIdAndPlayerIdOrderBySeqAsc(Long matchId, Long playerId);

    void deleteByMatchIdAndPlayerId(Long matchId, Long playerId);
}
