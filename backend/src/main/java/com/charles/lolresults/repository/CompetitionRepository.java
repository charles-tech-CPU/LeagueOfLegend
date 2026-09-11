package com.charles.lolresults.repository;

import com.charles.lolresults.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    Optional<Competition> findByCodeAndSeason(String code, Integer season);
}
