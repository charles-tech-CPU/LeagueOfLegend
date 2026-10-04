package com.charles.lolresults.repository;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByCompetitionIdOrderByDateAscTimeAsc(Long competitionId);

    List<Match> findByCompetitionIdAndStatusOrderByDateAscTimeAsc(Long competitionId, MatchStatus status);

    List<Match> findByTeam1_IdOrTeam2_IdOrderByDateDesc(Long team1Id, Long team2Id);

    List<Match> findByStatusOrderByDateAscTimeAsc(MatchStatus status);

    List<Match> findByStatusAndDateGreaterThanEqualOrderByDateAscTimeAsc(MatchStatus status, LocalDate from);

    List<Match> findByStageId(Long stageId);

    /** [competitionId, nombre de matchs, nombre de matchs joues] pour chaque competition d'une saison. */
    @Query("select m.competition.id, count(m), sum(case when m.status = :completed then 1 else 0 end) "
            + "from Match m where m.competition.season = :season group by m.competition.id")
    List<Object[]> countBySeason(@Param("season") Integer season, @Param("completed") MatchStatus completed);
}
