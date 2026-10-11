package com.charles.lolresults.service;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.MatchGameBan;
import com.charles.lolresults.domain.MatchGamePlayer;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.Position;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.StatsDto;
import com.charles.lolresults.repository.MatchGameBanRepository;
import com.charles.lolresults.repository.MatchGamePlayerRepository;
import com.charles.lolresults.repository.MatchGameRepository;
import com.charles.lolresults.repository.MatchRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statistiques (vue d'ensemble, picks/bans et resultats des champions, MVP, K/D/A et champions
 * de chaque joueur, cumul par poste et par equipe, records sur une manche) recalculees a la
 * volee a partir des details de match saisis, jamais stockees : meme principe que
 * StandingsService. Le perimetre est une competition, une saison (toutes competitions
 * confondues), ou tout l'historique quand ni l'un ni l'autre n'est precise.
 */
@Service
@Transactional(readOnly = true)
public class StatsService {

    private static final int TOP_CHAMPIONS_PER_POSITION = 5;
    private static final int TOP_CHAMPIONS_PER_PLAYER = 3;
    private static final int RECORDS = 5;

    private final MatchRepository matchRepository;
    private final MatchGameRepository gameRepository;
    private final MatchGamePlayerRepository gamePlayerRepository;
    private final MatchGameBanRepository gameBanRepository;

    public StatsService(
            MatchRepository matchRepository,
            MatchGameRepository gameRepository,
            MatchGamePlayerRepository gamePlayerRepository,
            MatchGameBanRepository gameBanRepository) {
        this.matchRepository = matchRepository;
        this.gameRepository = gameRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.gameBanRepository = gameBanRepository;
    }

    public StatsDto compute(Long competitionId, Integer season) {
        List<Match> matches = matchesInScope(competitionId, season);
        List<MatchGame> games = gamesInScope(competitionId, season);
        List<MatchGamePlayer> lines = linesInScope(competitionId, season);
        List<MatchGameBan> bans = bansInScope(competitionId, season);

        Map<Long, Profile> profiles = profiles(lines);
        Map<MatchGame, Map<Long, Long>> teamKills = StatTally.teamKills(lines);
        return new StatsDto(
                summary(lines, bans),
                championStats(lines, bans),
                mvpStats(matches, games, profiles),
                kdaStats(lines, profiles, teamKills),
                positionStats(lines),
                teamStats(lines, bans),
                new StatsDto.Records(
                        StatTally.top(lines, StatTally.MOST_KILLS, RECORDS),
                        StatTally.top(lines, StatTally.MOST_ASSISTS, RECORDS),
                        StatTally.top(lines, StatTally.BEST_KDA, RECORDS)));
    }

    private List<Match> matchesInScope(Long competitionId, Integer season) {
        if (competitionId != null) {
            return matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(competitionId);
        }
        if (season != null) {
            return matchRepository.findByCompetition_Season(season);
        }
        return matchRepository.findAll();
    }

    private List<MatchGame> gamesInScope(Long competitionId, Integer season) {
        if (competitionId != null) {
            return gameRepository.findByMatch_Competition_Id(competitionId);
        }
        if (season != null) {
            return gameRepository.findByMatch_Competition_Season(season);
        }
        return gameRepository.findAll();
    }

    private List<MatchGamePlayer> linesInScope(Long competitionId, Integer season) {
        if (competitionId != null) {
            return gamePlayerRepository.findByGame_Match_Competition_Id(competitionId);
        }
        if (season != null) {
            return gamePlayerRepository.findByGame_Match_Competition_Season(season);
        }
        return gamePlayerRepository.findAll();
    }

    private List<MatchGameBan> bansInScope(Long competitionId, Integer season) {
        if (competitionId != null) {
            return gameBanRepository.findByGame_Match_Competition_Id(competitionId);
        }
        if (season != null) {
            return gameBanRepository.findByGame_Match_Competition_Season(season);
        }
        return gameBanRepository.findAll();
    }

    private StatsDto.Summary summary(List<MatchGamePlayer> lines, List<MatchGameBan> bans) {
        Set<MatchGame> games = new HashSet<>();
        Set<String> picked = new HashSet<>();
        long kills = 0;
        for (MatchGamePlayer line : lines) {
            if (line.getGame() != null) {
                games.add(line.getGame());
            }
            picked.add(line.getChampion());
            kills += orZero(line.getKills());
        }
        Set<String> touched = new HashSet<>(picked);
        for (MatchGameBan ban : bans) {
            if (ban.getGame() != null) {
                games.add(ban.getGame());
            }
            touched.add(ban.getChampion());
        }
        return new StatsDto.Summary(
                games.size(), kills, (double) kills / Math.max(games.size(), 1), picked.size(), touched.size());
    }

    private List<StatsDto.ChampionStat> championStats(List<MatchGamePlayer> lines, List<MatchGameBan> bans) {
        Map<String, StatTally> byChampion = new LinkedHashMap<>();
        Map<String, Long> banCounts = new HashMap<>();
        Map<String, Map<Position, Long>> byPosition = new LinkedHashMap<>();
        for (MatchGamePlayer line : lines) {
            byChampion.computeIfAbsent(line.getChampion(), c -> new StatTally()).add(line, 0);
            byPosition
                    .computeIfAbsent(line.getChampion(), c -> new EnumMap<>(Position.class))
                    .merge(line.getPosition(), 1L, Long::sum);
        }
        for (MatchGameBan ban : bans) {
            byChampion.computeIfAbsent(ban.getChampion(), c -> new StatTally());
            banCounts.merge(ban.getChampion(), 1L, Long::sum);
        }
        return byChampion.entrySet().stream()
                .map(e -> {
                    StatTally t = e.getValue();
                    long banned = banCounts.getOrDefault(e.getKey(), 0L);
                    return new StatsDto.ChampionStat(
                            e.getKey(),
                            t.games,
                            banned,
                            t.games + banned,
                            byPosition.getOrDefault(e.getKey(), Map.of()),
                            t.wins,
                            t.losses,
                            t.kills,
                            t.deaths,
                            t.assists,
                            t.ratio());
                })
                .sorted(Comparator.comparingLong(StatsDto.ChampionStat::pickAndBan)
                        .reversed())
                .toList();
    }

    private List<StatsDto.MvpStat> mvpStats(List<Match> matches, List<MatchGame> games, Map<Long, Profile> profiles) {
        Map<Long, long[]> byPlayer = new LinkedHashMap<>(); // [seriesMvp, gameMvp]
        Map<Long, Player> players = new LinkedHashMap<>();
        for (Match m : matches) {
            if (m.getMvp() != null) {
                byPlayer.computeIfAbsent(m.getMvp().getId(), id -> new long[2])[0]++;
                players.put(m.getMvp().getId(), m.getMvp());
            }
        }
        for (MatchGame g : games) {
            if (g.getMvp() != null) {
                byPlayer.computeIfAbsent(g.getMvp().getId(), id -> new long[2])[1]++;
                players.put(g.getMvp().getId(), g.getMvp());
            }
        }
        List<StatsDto.MvpStat> result = new ArrayList<>();
        byPlayer.forEach((playerId, counts) -> {
            Player p = players.get(playerId);
            Profile profile = profiles.getOrDefault(playerId, Profile.of(p));
            Team team = profile.team();
            result.add(new StatsDto.MvpStat(
                    playerId,
                    p.getPseudo(),
                    team != null ? team.getId() : null,
                    team != null ? team.getCode() : null,
                    team != null && team.getLogo() != null,
                    profile.position(),
                    counts[0],
                    counts[1]));
        });
        return result.stream()
                .sorted(Comparator.comparingLong((StatsDto.MvpStat s) -> s.seriesMvp() + s.gameMvp())
                        .reversed())
                .toList();
    }

    /** Joueurs ayant au moins une manche avec K/D/A saisi ; games compte ces manches-la. */
    private List<StatsDto.KdaStat> kdaStats(
            List<MatchGamePlayer> lines, Map<Long, Profile> profiles, Map<MatchGame, Map<Long, Long>> teamKills) {
        Map<Long, StatTally> byPlayer = new LinkedHashMap<>();
        Map<Long, Player> players = new LinkedHashMap<>();
        for (MatchGamePlayer line : lines) {
            byPlayer.computeIfAbsent(line.getPlayer().getId(), id -> new StatTally())
                    .add(line, StatTally.teamKillsOf(line, teamKills));
            players.put(line.getPlayer().getId(), line.getPlayer());
        }
        return byPlayer.entrySet().stream()
                .filter(e -> e.getValue().kdaGames > 0)
                .map(e -> {
                    Player p = players.get(e.getKey());
                    Profile profile = profiles.getOrDefault(p.getId(), Profile.of(p));
                    Team team = profile.team();
                    StatTally t = e.getValue();
                    return new StatsDto.KdaStat(
                            p.getId(),
                            p.getPseudo(),
                            team != null ? team.getId() : null,
                            team != null ? team.getCode() : null,
                            team != null && team.getLogo() != null,
                            profile.position(),
                            t.kdaGames,
                            t.kills,
                            t.deaths,
                            t.assists,
                            t.ratio(),
                            t.wins,
                            t.losses,
                            t.killParticipation(),
                            t.perfectGames,
                            t.champions.size(),
                            t.topChampions(TOP_CHAMPIONS_PER_PLAYER));
                })
                .sorted(Comparator.comparingDouble(StatsDto.KdaStat::ratio).reversed())
                .toList();
    }

    /** Cumul par poste, dans l'ordre TOP -> SUPP ; les postes sans aucune manche saisie sont omis. */
    private List<StatsDto.PositionStat> positionStats(List<MatchGamePlayer> lines) {
        Map<Position, long[]> tallies = new EnumMap<>(Position.class); // [kills, deaths, assists, manches]
        Map<Position, Map<String, Long>> picks = new EnumMap<>(Position.class);
        for (MatchGamePlayer line : lines) {
            long[] tally = tallies.computeIfAbsent(line.getPosition(), pos -> new long[4]);
            tally[0] += orZero(line.getKills());
            tally[1] += orZero(line.getDeaths());
            tally[2] += orZero(line.getAssists());
            tally[3]++;
            picks.computeIfAbsent(line.getPosition(), pos -> new HashMap<>()).merge(line.getChampion(), 1L, Long::sum);
        }
        return tallies.entrySet().stream()
                .map(e -> {
                    long[] tally = e.getValue();
                    List<StatsDto.ChampionPick> top = picks.get(e.getKey()).entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue()
                                    .reversed()
                                    .thenComparing(Map.Entry.comparingByKey()))
                            .limit(TOP_CHAMPIONS_PER_POSITION)
                            .map(c -> new StatsDto.ChampionPick(c.getKey(), c.getValue()))
                            .toList();
                    return new StatsDto.PositionStat(
                            e.getKey(),
                            tally[3],
                            tally[0],
                            tally[1],
                            tally[2],
                            ratio(tally[0], tally[1], tally[2]),
                            top);
                })
                .toList();
    }

    /** Cumul par equipe, de la plus victorieuse a la moins victorieuse. */
    private List<StatsDto.TeamStat> teamStats(List<MatchGamePlayer> lines, List<MatchGameBan> bans) {
        Map<Long, Team> teams = new LinkedHashMap<>();
        Map<Long, Set<MatchGame>> games = new HashMap<>();
        Map<Long, long[]> killsDeaths = new HashMap<>(); // [kills, deaths]
        Map<Long, Map<String, Long>> picks = new HashMap<>();
        Map<Long, Map<String, Long>> banned = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            Team team = line.getTeam();
            if (team == null) {
                continue;
            }
            teams.put(team.getId(), team);
            if (line.getGame() != null) {
                games.computeIfAbsent(team.getId(), id -> new HashSet<>()).add(line.getGame());
            }
            long[] tally = killsDeaths.computeIfAbsent(team.getId(), id -> new long[2]);
            tally[0] += orZero(line.getKills());
            tally[1] += orZero(line.getDeaths());
            picks.computeIfAbsent(team.getId(), id -> new HashMap<>()).merge(line.getChampion(), 1L, Long::sum);
        }
        for (MatchGameBan ban : bans) {
            if (ban.getTeam() != null && teams.containsKey(ban.getTeam().getId())) {
                banned.computeIfAbsent(ban.getTeam().getId(), id -> new HashMap<>())
                        .merge(ban.getChampion(), 1L, Long::sum);
            }
        }
        return teams.values().stream()
                .map(team -> {
                    Set<MatchGame> played = games.getOrDefault(team.getId(), Set.of());
                    long wins = played.stream()
                            .filter(g -> g.getWinner() != null && StatTally.sameTeam(g.getWinner(), team))
                            .count();
                    long losses = played.stream()
                            .filter(g -> g.getWinner() != null && !StatTally.sameTeam(g.getWinner(), team))
                            .count();
                    long[] tally = killsDeaths.get(team.getId());
                    return new StatsDto.TeamStat(
                            team.getId(),
                            team.getCode(),
                            team.getName(),
                            team.getLogo() != null,
                            played.size(),
                            wins,
                            losses,
                            tally[0],
                            tally[1],
                            (double) tally[0] / Math.max(played.size(), 1),
                            mostFrequent(picks.get(team.getId())),
                            mostFrequent(banned.get(team.getId())));
                })
                .sorted(Comparator.comparingLong(StatsDto.TeamStat::wins)
                        .reversed()
                        .thenComparingLong(StatsDto.TeamStat::losses)
                        .thenComparing(StatsDto.TeamStat::teamCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Champion le plus frequent ; a egalite, le premier dans l'ordre alphabetique. */
    private static StatsDto.ChampionPick mostFrequent(Map<String, Long> counts) {
        if (counts == null) {
            return null;
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(e -> new StatsDto.ChampionPick(e.getKey(), e.getValue()))
                .orElse(null);
    }

    /**
     * Equipe et poste de chaque joueur dans le perimetre : l'equipe de sa derniere manche
     * saisie (un joueur transfere apparait avec sa derniere equipe), le poste le plus joue.
     */
    private Map<Long, Profile> profiles(List<MatchGamePlayer> lines) {
        Map<Long, MatchGamePlayer> latest = new HashMap<>();
        Map<Long, Map<Position, Long>> positions = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            Long playerId = line.getPlayer().getId();
            latest.merge(playerId, line, (a, b) -> CHRONOLOGICAL.compare(a, b) >= 0 ? a : b);
            positions
                    .computeIfAbsent(playerId, id -> new EnumMap<>(Position.class))
                    .merge(line.getPosition(), 1L, Long::sum);
        }
        Map<Long, Profile> result = new HashMap<>();
        latest.forEach((playerId, line) -> {
            // Poste le plus joue ; a egalite, le premier dans l'ordre TOP -> SUPP.
            Position main = positions.get(playerId).entrySet().stream()
                    .max(Map.Entry.<Position, Long>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                    .map(Map.Entry::getKey)
                    .orElse(line.getPosition());
            result.put(playerId, new Profile(line.getTeam(), main));
        });
        return result;
    }

    /** Ordre chronologique des manches : date puis heure du match, puis numero de manche. */
    static final Comparator<MatchGamePlayer> CHRONOLOGICAL = Comparator.comparing(
                    (MatchGamePlayer l) -> matchOf(l) != null ? matchOf(l).getDate() : null,
                    Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(
                    l -> matchOf(l) != null ? matchOf(l).getTime() : null,
                    Comparator.nullsFirst(Comparator.<LocalTime>naturalOrder()))
            .thenComparing(
                    l -> l.getGame() != null ? l.getGame().getGameNumber() : null,
                    Comparator.nullsFirst(Comparator.<Integer>naturalOrder()));

    private static Match matchOf(MatchGamePlayer line) {
        return line.getGame() != null ? line.getGame().getMatch() : null;
    }

    /** Equipe et poste retenus pour afficher un joueur dans les stats. */
    private record Profile(Team team, Position position) {
        static Profile of(Player player) {
            return new Profile(player.getTeam(), player.getPosition());
        }
    }

    private static double ratio(long kills, long deaths, long assists) {
        return StatTally.ratio(kills, deaths, assists);
    }

    private static long orZero(Integer value) {
        return StatTally.orZero(value);
    }
}
