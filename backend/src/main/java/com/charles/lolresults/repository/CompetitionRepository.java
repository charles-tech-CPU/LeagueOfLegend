package com.charles.lolresults.repository;

import com.charles.lolresults.domain.Competition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    Optional<Competition> findByCodeAndSeason(String code, Integer season);

    List<Competition> findBySeason(Integer season);

    /** [saison, nombre de competitions], de la plus recente a la plus ancienne. */
    @Query("select c.season, count(c) from Competition c group by c.season order by c.season desc")
    List<Object[]> countBySeason();
}
