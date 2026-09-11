package com.charles.lolresults.repository;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByCompetitionIdOrderByDateAscTimeAsc(Long competitionId);

    List<Match> findByCompetitionIdAndStatusOrderByDateAscTimeAsc(Long competitionId, MatchStatus status);

    List<Match> findByTeam1_IdOrTeam2_IdOrderByDateDesc(Long team1Id, Long team2Id);

    List<Match> findByStatusOrderByDateAscTimeAsc(MatchStatus status);
}
