package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.PlayerOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlayerOrganizationRepository extends JpaRepository<PlayerOrganization, Long> {

    List<PlayerOrganization> findByPlayerId(Long playerId);

    List<PlayerOrganization> findByOrganizationId(Long organizationId);

    boolean existsByPlayerIdAndOrganizationId(Long playerId, Long organizationId);

    void deleteByPlayerId(Long playerId);

    void deleteByPlayerIdAndOrganizationId(Long playerId, Long organizationId);
}
