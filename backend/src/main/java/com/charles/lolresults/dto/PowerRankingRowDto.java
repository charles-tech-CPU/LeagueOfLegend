package com.charles.lolresults.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Une ligne du power ranking d'une saison, recalcule a la volee a partir des series jouees
 * (jamais stocke, meme principe que StandingRowDto).
 *
 * <p>rankChange : places gagnees (positif) ou perdues depuis le classement d'il y a 7 jours,
 * nul si l'equipe n'etait pas encore classee. form : resultats des 5 dernieres series, de la
 * plus ancienne a la plus recente ("W", "L" ou "D" pour un BO2 nul). bestWin : la victoire
 * contre l'adversaire le mieux note au moment du match. league : ligue regionale principale
 * de l'equipe dans la saison (nulle si aucune), dont la note de depart startRating est le
 * coefficient.
 */
public record PowerRankingRowDto(
        int rank,
        Integer rankChange,
        Long teamId,
        String teamCode,
        String teamName,
        String region,
        boolean teamHasLogo,
        String league,
        int startRating,
        int rating,
        int ratingChange,
        int seriesWon,
        int seriesLost,
        int gamesWon,
        int gamesLost,
        List<String> form,
        BestWin bestWin,
        LocalDate lastPlayed) {

    public record BestWin(Long matchId, Long opponentId, String opponentCode, int opponentRating, String score) {}
}
