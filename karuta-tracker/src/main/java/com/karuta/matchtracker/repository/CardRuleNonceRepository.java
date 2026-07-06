package com.karuta.matchtracker.repository;

import com.karuta.matchtracker.entity.CardRuleNonce;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface CardRuleNonceRepository extends JpaRepository<CardRuleNonce, Long> {
    Optional<CardRuleNonce> findBySessionDate(LocalDate sessionDate);
}
