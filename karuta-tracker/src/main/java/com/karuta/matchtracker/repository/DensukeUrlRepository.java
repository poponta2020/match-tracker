package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DensukeUrlRepository extends JpaRepository<DensukeUrl, Long> {

    Optional<DensukeUrl> findByYearAndMonthAndOrganizationId(Integer year, Integer month, Long organizationId);

    List<DensukeUrl> findByYearAndMonth(Integer year, Integer month);
}
