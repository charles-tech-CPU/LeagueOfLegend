package com.charles.lolresults.repository;

import com.charles.lolresults.domain.Competition;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    Optional<Competition> findByCodeAndSeason(String code, Integer season);
}
