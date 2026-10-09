package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import java.util.List;
import java.util.Map;

/**
 * Statistiques calculees a la volee a partir des details de match saisis (jamais stockees,
 * meme principe que StandingRowDto/HeadToHeadCellDto). Les listes sont vides si aucun
 * detail n'a ete saisi dans le perimetre demande.
 *
 * <p>L'equipe d'un joueur est celle de sa derniere manche saisie dans le perimetre (a defaut
 * son equipe actuelle), son poste celui qu'il y a le plus joue (a defaut son poste actuel).
 */
public record StatsDto(
        List<ChampionStat> champions, List<MvpStat> mvps, List<KdaStat> kda, List<PositionStat> positions) {

    /** picksByPosition : picks du champion par poste (les bans ne sont rattaches a aucun poste). */
    public record ChampionStat(
            String champion, long picks, long bans, long pickAndBan, Map<Position, Long> picksByPosition) {}

    public record MvpStat(
            Long playerId,
            String pseudo,
            Long teamId,
            String teamCode,
            boolean teamHasLogo,
            Position position,
            long seriesMvp,
            long gameMvp) {}

    public record KdaStat(
            Long playerId,
            String pseudo,
            Long teamId,
            String teamCode,
            boolean teamHasLogo,
            Position position,
            long games,
            long kills,
            long deaths,
            long assists,
            double ratio) {}

    /** Cumul d'un poste, toutes equipes confondues, avec ses champions les plus joues. */
    public record PositionStat(
            Position position,
            long games,
            long kills,
            long deaths,
            long assists,
            double ratio,
            List<ChampionPick> topChampions) {}

    public record ChampionPick(String champion, long picks) {}
}
