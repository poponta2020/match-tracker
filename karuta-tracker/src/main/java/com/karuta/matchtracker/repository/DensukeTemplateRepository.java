package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DensukeTemplateRepository extends JpaRepository<DensukeTemplate, Long> {

    Optional<DensukeTemplate> findByOrganizationId(Long organizationId);
}
