package com.charles.lolresults.service;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.MatchGamePlayer;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.PlayerStatsDto;
import com.charles.lolresults.dto.StatsDto;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cumul de lignes de stats (les manches d'un joueur, d'un champion, d'une competition...) :
 * manches, victoires, K/D/A, participation aux kills, MVP de manche, champions joues.
 * Partage par StatsService et PlayerStatsService.
 */
final class StatTally {

    long games;
    long kdaGames;
    long wins;
    long losses;
    long kills;
    long deaths;
    long assists;
    long perfectGames;
    long gameMvp;
    /** Kills + assists du joueur et kills de son equipe, sur les manches ou l'equipe a au moins un kill saisi. */
    long kpContribution;

    long kpTeamKills;

    final Map<String, Long> champions = new HashMap<>();

    /** teamKills : kills de l'equipe du joueur sur la manche (0 si inconnu). */
    void add(MatchGamePlayer line, long teamKills) {
        games++;
        champions.merge(line.getChampion(), 1L, Long::sum);
        Boolean won = won(line);
        if (won != null) {
            if (won) {
                wins++;
            } else {
                losses++;
            }
        }
        MatchGame game = line.getGame();
        if (game != null
                && game.getMvp() != null
                && line.getPlayer() != null
                && game.getMvp().getId() != null
                && game.getMvp().getId().equals(line.getPlayer().getId())) {
            gameMvp++;
        }
        if (hasKda(line)) {
            long k = orZero(line.getKills());
            long d = orZero(line.getDeaths());
            long a = orZero(line.getAssists());
            kdaGames++;
            kills += k;
            deaths += d;
            assists += a;
            if (d == 0) {
                perfectGames++;
            }
            if (teamKills > 0) {
                kpContribution += k + a;
                kpTeamKills += teamKills;
            }
        }
    }

    double ratio() {
        return ratio(kills, deaths, assists);
    }

    /** Part des kills de l'equipe auxquels le joueur a participe, nulle si aucun kill d'equipe saisi. */
    Double killParticipation() {
        return kpTeamKills > 0 ? Math.min(1.0, (double) kpContribution / kpTeamKills) : null;
    }

    List<StatsDto.ChampionPick> topChampions(int limit) {
        return champions.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(c -> new StatsDto.ChampionPick(c.getKey(), c.getValue()))
                .toList();
    }

    PlayerStatsDto.Tally toDto() {
        return new PlayerStatsDto.Tally(
                games,
                wins,
                losses,
                kdaGames,
                kills,
                deaths,
                assists,
                ratio(),
                killParticipation(),
                perfectGames,
                gameMvp,
                champions.size());
    }

    /** Vrai/faux si le vainqueur de la manche est saisi, nul sinon. */
    static Boolean won(MatchGamePlayer line) {
        MatchGame game = line.getGame();
        if (game == null || game.getWinner() == null || line.getTeam() == null) {
            return null;
        }
        return sameTeam(game.getWinner(), line.getTeam());
    }

    static boolean sameTeam(Team a, Team b) {
        return a == b
                || (a != null && b != null && a.getId() != null && a.getId().equals(b.getId()));
    }

    /** Kills de chaque equipe sur chaque manche, a partir des lignes fournies. */
    static Map<MatchGame, Map<Long, Long>> teamKills(Collection<MatchGamePlayer> lines) {
        Map<MatchGame, Map<Long, Long>> result = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            if (line.getGame() == null || line.getTeam() == null) {
                continue;
            }
            result.computeIfAbsent(line.getGame(), g -> new HashMap<>())
                    .merge(line.getTeam().getId(), orZero(line.getKills()), Long::sum);
        }
        return result;
    }

    static long teamKillsOf(MatchGamePlayer line, Map<MatchGame, Map<Long, Long>> teamKills) {
        if (line.getGame() == null || line.getTeam() == null) {
            return 0;
        }
        return teamKills
                .getOrDefault(line.getGame(), Map.of())
                .getOrDefault(line.getTeam().getId(), 0L);
    }

    /** Une manche d'un joueur, avec son contexte (match, adversaire, resultat). */
    static StatsDto.GamePerformance performance(MatchGamePlayer line) {
        Team team = line.getTeam();
        Match match = line.getGame() != null ? line.getGame().getMatch() : null;
        Team opponent = null;
        if (match != null) {
            opponent = sameTeam(match.getTeam1(), team) ? match.getTeam2() : match.getTeam1();
        }
        long k = orZero(line.getKills());
        long d = orZero(line.getDeaths());
        long a = orZero(line.getAssists());
        return new StatsDto.GamePerformance(
                line.getPlayer().getId(),
                line.getPlayer().getPseudo(),
                team != null ? team.getId() : null,
                team != null ? team.getCode() : null,
                team != null && team.getLogo() != null,
                line.getChampion(),
                line.getPosition(),
                k,
                d,
                a,
                ratio(k, d, a),
                won(line),
                match != null ? match.getId() : null,
                match != null ? match.getDate() : null,
                match != null && match.getCompetition() != null
                        ? match.getCompetition().getCode()
                        : null,
                opponent != null ? opponent.getCode() : null);
    }

    /** Meilleures manches selon un critere, parmi les lignes ou le K/D/A est saisi. */
    static List<StatsDto.GamePerformance> top(
            Collection<MatchGamePlayer> lines, Comparator<StatsDto.GamePerformance> order, int limit) {
        return lines.stream()
                .filter(StatTally::hasKda)
                .map(StatTally::performance)
                .sorted(order)
                .limit(limit)
                .toList();
    }

    static final Comparator<StatsDto.GamePerformance> MOST_KILLS = Comparator.comparingLong(
                    StatsDto.GamePerformance::kills)
            .reversed()
            .thenComparing(
                    Comparator.comparingDouble(StatsDto.GamePerformance::ratio).reversed());

    static final Comparator<StatsDto.GamePerformance> MOST_ASSISTS = Comparator.comparingLong(
                    StatsDto.GamePerformance::assists)
            .reversed()
            .thenComparing(
                    Comparator.comparingDouble(StatsDto.GamePerformance::ratio).reversed());

    static final Comparator<StatsDto.GamePerformance> BEST_KDA = Comparator.comparingDouble(
                    StatsDto.GamePerformance::ratio)
            .reversed()
            .thenComparing(Comparator.comparingLong((StatsDto.GamePerformance p) -> p.kills() + p.assists())
                    .reversed());

    static final Comparator<StatsDto.GamePerformance> MOST_DEATHS = Comparator.comparingLong(
                    StatsDto.GamePerformance::deaths)
            .reversed()
            .thenComparingDouble(StatsDto.GamePerformance::ratio);

    static boolean hasKda(MatchGamePlayer line) {
        return line.getKills() != null || line.getDeaths() != null || line.getAssists() != null;
    }

    static double ratio(long kills, long deaths, long assists) {
        return (double) (kills + assists) / Math.max(deaths, 1);
    }

    static long orZero(Integer value) {
        return value != null ? value : 0;
    }
}
