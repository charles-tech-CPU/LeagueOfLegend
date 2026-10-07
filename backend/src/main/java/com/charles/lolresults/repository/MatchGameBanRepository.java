package com.charles.lolresults.repository;

import com.charles.lolresults.domain.MatchGameBan;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGameBanRepository extends JpaRepository<MatchGameBan, Long> {

    /** Tous les bans des matchs d'une competition (pour l'onglet Stats). */
    List<MatchGameBan> findByGame_Match_Competition_Id(Long competitionId);

    /** Tous les bans des matchs d'une saison, toutes competitions confondues. */
    List<MatchGameBan> findByGame_Match_Competition_Season(Integer season);
}
