package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.DensukeUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DensukeUrlRepository extends JpaRepository<DensukeUrl, Long> {

    Optional<DensukeUrl> findByYearAndMonth(Integer year, Integer month);
}
