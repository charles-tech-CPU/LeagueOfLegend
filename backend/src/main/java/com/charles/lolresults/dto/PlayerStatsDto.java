package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import java.util.List;

/**
 * Statistiques individuelles d'un joueur, calculees a la volee a partir de ses manches
 * saisies (champion, K/D/A, vainqueur) : carriere, par competition, par saison, par
 * champion, par poste et face a chaque adversaire. Listes vides si aucune manche saisie.
 */
public record PlayerStatsDto(
        Tally career,
        long seriesMvp,
        List<CompetitionLine> competitions,
        List<SeasonLine> seasons,
        List<ChampionLine> champions,
        List<PositionLine> positions,
        List<OpponentLine> opponents,
        List<StatsDto.GamePerformance> bestGames,
        List<StatsDto.GamePerformance> recentGames) {

    /**
     * Cumul de manches. games compte toutes les manches saisies, kdaGames celles ou le K/D/A
     * est saisi (base des moyennes) ; wins/losses celles dont le vainqueur est connu.
     */
    public record Tally(
            long games,
            long wins,
            long losses,
            long kdaGames,
            long kills,
            long deaths,
            long assists,
            double ratio,
            Double killParticipation,
            long perfectGames,
            long gameMvp,
            long championCount) {}

    /** L'equipe est celle de la derniere manche du joueur dans la competition. */
    public record CompetitionLine(
            Long competitionId,
            String code,
            String name,
            Integer season,
            Long teamId,
            String teamCode,
            boolean teamHasLogo,
            Tally stats,
            long seriesMvp,
            List<StatsDto.ChampionPick> topChampions) {}

    public record SeasonLine(Integer season, Tally stats, long seriesMvp, List<StatsDto.ChampionPick> topChampions) {}

    public record ChampionLine(String champion, Tally stats) {}

    public record PositionLine(Position position, Tally stats) {}

    public record OpponentLine(Long teamId, String teamCode, String teamName, boolean teamHasLogo, Tally stats) {}
}
