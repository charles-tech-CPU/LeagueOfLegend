package com.charles.lolresults.repository;

import com.charles.lolresults.domain.CompetitionStage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionStageRepository extends JpaRepository<CompetitionStage, Long> {
    List<CompetitionStage> findByCompetitionIdOrderByPosition(Long competitionId);
}
