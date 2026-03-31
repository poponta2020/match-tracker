package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeMemberMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DensukeMemberMappingRepository extends JpaRepository<DensukeMemberMapping, Long> {

    Optional<DensukeMemberMapping> findByDensukeUrlIdAndPlayerId(Long densukeUrlId, Long playerId);

    List<DensukeMemberMapping> findByDensukeUrlId(Long densukeUrlId);
}
