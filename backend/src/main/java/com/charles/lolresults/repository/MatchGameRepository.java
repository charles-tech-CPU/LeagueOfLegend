package com.charles.lolresults.repository;

import com.charles.lolresults.domain.MatchGame;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGameRepository extends JpaRepository<MatchGame, Long> {
    List<MatchGame> findByMatchIdOrderByGameNumber(Long matchId);

    /** Manches des matchs d'une competition (pour le MVP de manche dans l'onglet Stats). */
    List<MatchGame> findByMatch_Competition_Id(Long competitionId);

    /** Manches des matchs d'une saison, toutes competitions confondues. */
    List<MatchGame> findByMatch_Competition_Season(Integer season);
}
