package com.charles.lolresults.repository;

import com.charles.lolresults.domain.MatchGamePlayer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGamePlayerRepository extends JpaRepository<MatchGamePlayer, Long> {

    /** Toutes les lignes de stats des matchs d'une competition (pour l'onglet Stats et l'apercu compact). */
    List<MatchGamePlayer> findByGame_Match_Competition_Id(Long competitionId);

    /** Toutes les lignes de stats des matchs d'une saison, toutes competitions confondues. */
    List<MatchGamePlayer> findByGame_Match_Competition_Season(Integer season);
}
