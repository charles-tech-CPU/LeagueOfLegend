package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Statistiques calculees a la volee a partir des details de match saisis (jamais stockees,
 * meme principe que StandingRowDto/HeadToHeadCellDto). Les listes sont vides si aucun
 * detail n'a ete saisi dans le perimetre demande.
 *
 * <p>L'equipe d'un joueur est celle de sa derniere manche saisie dans le perimetre (a defaut
 * son equipe actuelle), son poste celui qu'il y a le plus joue (a defaut son poste actuel).
 *
 * <p>Victoires et defaites ne comptent que les manches dont le vainqueur est saisi ; la
 * participation aux kills (killParticipation, entre 0 et 1) les manches ou l'equipe a au
 * moins un kill saisi, elle est nulle sinon.
 */
public record StatsDto(
        Summary summary,
        List<ChampionStat> champions,
        List<MvpStat> mvps,
        List<KdaStat> kda,
        List<PositionStat> positions,
        List<TeamStat> teams,
        Records records,
        Bans bans) {

    /** Vue d'ensemble du perimetre : manches saisies, kills, diversite des champions. */
    public record Summary(
            long games, long kills, double killsPerGame, long championsPicked, long championsPickedOrBanned) {}

    /** picksByPosition : picks du champion par poste (les bans ne sont rattaches a aucun poste). */
    public record ChampionStat(
            String champion,
            long picks,
            long bans,
            long pickAndBan,
            Map<Position, Long> picksByPosition,
            long wins,
            long losses,
            long kills,
            long deaths,
            long assists,
            double ratio) {}

    public record MvpStat(
            Long playerId,
            String pseudo,
            Long teamId,
            String teamCode,
            boolean teamHasLogo,
            Position position,
            long seriesMvp,
            long gameMvp) {}

    /** topChampions : champions les plus joues par le joueur dans le perimetre. */
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
            double ratio,
            long wins,
            long losses,
            Double killParticipation,
            long perfectGames,
            long championCount,
            List<ChampionPick> topChampions) {}

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

    /**
     * Cumul d'une equipe : manches jouees, victoires, kills/morts/assists, ecart de kills moyen
     * avec l'adversaire (manches ou l'adversaire est saisi), nombre de champions differents
     * joues, champion le plus joue, le plus banni par l'equipe et le plus banni contre elle
     * (nuls si aucun).
     */
    public record TeamStat(
            Long teamId,
            String teamCode,
            String teamName,
            boolean teamHasLogo,
            long games,
            long wins,
            long losses,
            long kills,
            long deaths,
            long assists,
            double killsPerGame,
            Double killDiffPerGame,
            long championPool,
            ChampionPick mostPicked,
            ChampionPick mostBanned,
            ChampionPick mostBannedAgainst) {}

    /** Meilleures performances sur une manche, individuelles puis par equipe. */
    public record Records(
            List<GamePerformance> mostKills,
            List<GamePerformance> mostAssists,
            List<GamePerformance> bestKda,
            List<GamePerformance> mostDeaths,
            List<TeamGame> mostTeamKills,
            List<TeamGame> biggestKillGap) {}

    /** Une manche d'une equipe ; won est nul si le vainqueur de la manche n'est pas saisi. */
    public record TeamGame(
            Long teamId,
            String teamCode,
            boolean teamHasLogo,
            long kills,
            long opponentKills,
            Boolean won,
            Long matchId,
            LocalDate date,
            String competitionCode,
            String opponentCode) {}

    /**
     * Bannissements : ceux faits et subis par chaque equipe, et les joueurs les plus cibles
     * (bans adverses, sur une manche qu'ils jouent, d'un champion de leur pool : joue au moins
     * deux fois dans le perimetre).
     */
    public record Bans(List<TeamBans> teams, List<PlayerBans> players) {}

    public record TeamBans(
            Long teamId,
            String teamCode,
            String teamName,
            boolean teamHasLogo,
            long games,
            long bansMade,
            List<ChampionPick> topMade,
            long bansReceived,
            List<ChampionPick> topReceived) {}

    public record PlayerBans(
            Long playerId,
            String pseudo,
            Long teamId,
            String teamCode,
            boolean teamHasLogo,
            Position position,
            long games,
            long targetedBans,
            double targetedPerGame,
            List<ChampionPick> topTargeted) {}

    /** Une manche d'un joueur ; won est nul si le vainqueur de la manche n'est pas saisi. */
    public record GamePerformance(
            Long playerId,
            String pseudo,
            Long teamId,
            String teamCode,
            boolean teamHasLogo,
            String champion,
            Position position,
            long kills,
            long deaths,
            long assists,
            double ratio,
            Boolean won,
            Long matchId,
            LocalDate date,
            String competitionCode,
            String opponentCode) {}
}
