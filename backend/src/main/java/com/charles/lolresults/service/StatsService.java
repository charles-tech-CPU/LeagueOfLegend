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
 * de chaque joueur, cumul par poste et par equipe, records sur une manche, bannissements par
 * equipe et joueurs cibles) recalculees a la
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
    private static final int TOP_BANS = 3;
    private static final int TARGETED_PLAYERS = 25;
    /** Un champion fait partie du pool d'un joueur s'il l'a joue au moins ce nombre de fois. */
    private static final int POOL_MIN_GAMES = 2;

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
                teamStats(lines, bans, teamKills),
                new StatsDto.Records(
                        StatTally.top(lines, StatTally.MOST_KILLS, RECORDS),
                        StatTally.top(lines, StatTally.MOST_ASSISTS, RECORDS),
                        StatTally.top(lines, StatTally.BEST_KDA, RECORDS),
                        StatTally.top(lines, StatTally.MOST_DEATHS, RECORDS),
                        topTeamGames(lines, teamKills, MOST_TEAM_KILLS),
                        topTeamGames(lines, teamKills, BIGGEST_KILL_GAP)),
                new StatsDto.Bans(teamBans(lines, bans), playerBans(lines, bans, profiles)));
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
    private List<StatsDto.TeamStat> teamStats(
            List<MatchGamePlayer> lines, List<MatchGameBan> bans, Map<MatchGame, Map<Long, Long>> teamKills) {
        Map<Long, Team> teams = new LinkedHashMap<>();
        Map<Long, Set<MatchGame>> games = new HashMap<>();
        Map<Long, long[]> tallies = new HashMap<>(); // [kills, deaths, assists]
        Map<Long, Map<String, Long>> picks = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            Team team = line.getTeam();
            if (team == null) {
                continue;
            }
            teams.put(team.getId(), team);
            if (line.getGame() != null) {
                games.computeIfAbsent(team.getId(), id -> new HashSet<>()).add(line.getGame());
            }
            long[] tally = tallies.computeIfAbsent(team.getId(), id -> new long[3]);
            tally[0] += orZero(line.getKills());
            tally[1] += orZero(line.getDeaths());
            tally[2] += orZero(line.getAssists());
            picks.computeIfAbsent(team.getId(), id -> new HashMap<>()).merge(line.getChampion(), 1L, Long::sum);
        }
        BanCounts banCounts = banCounts(lines, bans);
        return teams.values().stream()
                .map(team -> {
                    Set<MatchGame> played = games.getOrDefault(team.getId(), Set.of());
                    long wins = played.stream()
                            .filter(g -> g.getWinner() != null && StatTally.sameTeam(g.getWinner(), team))
                            .count();
                    long losses = played.stream()
                            .filter(g -> g.getWinner() != null && !StatTally.sameTeam(g.getWinner(), team))
                            .count();
                    long[] tally = tallies.get(team.getId());
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
                            tally[2],
                            (double) tally[0] / Math.max(played.size(), 1),
                            killDiffPerGame(team.getId(), played, teamKills),
                            picks.getOrDefault(team.getId(), Map.of()).size(),
                            mostFrequent(picks.get(team.getId())),
                            mostFrequent(banCounts.made().get(team.getId())),
                            mostFrequent(banCounts.received().get(team.getId())));
                })
                .sorted(Comparator.comparingLong(StatsDto.TeamStat::wins)
                        .reversed()
                        .thenComparingLong(StatsDto.TeamStat::losses)
                        .thenComparing(StatsDto.TeamStat::teamCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /** Ecart de kills moyen avec l'adversaire, sur les manches ou les deux equipes sont saisies. */
    private static Double killDiffPerGame(
            Long teamId, Set<MatchGame> played, Map<MatchGame, Map<Long, Long>> teamKills) {
        long diff = 0;
        long counted = 0;
        for (MatchGame game : played) {
            Map<Long, Long> kills = teamKills.getOrDefault(game, Map.of());
            if (kills.size() != 2 || !kills.containsKey(teamId)) {
                continue;
            }
            long own = kills.get(teamId);
            long other = kills.entrySet().stream()
                    .filter(e -> !e.getKey().equals(teamId))
                    .mapToLong(Map.Entry::getValue)
                    .sum();
            diff += own - other;
            counted++;
        }
        return counted > 0 ? (double) diff / counted : null;
    }

    static final Comparator<StatsDto.TeamGame> MOST_TEAM_KILLS = Comparator.comparingLong(StatsDto.TeamGame::kills)
            .reversed()
            .thenComparingLong(StatsDto.TeamGame::opponentKills);

    static final Comparator<StatsDto.TeamGame> BIGGEST_KILL_GAP = Comparator.comparingLong(
                    (StatsDto.TeamGame g) -> g.kills() - g.opponentKills())
            .reversed()
            .thenComparing(Comparator.comparingLong(StatsDto.TeamGame::kills).reversed());

    /** Meilleures manches d'equipe selon un critere, parmi celles ou les deux equipes sont saisies. */
    private static List<StatsDto.TeamGame> topTeamGames(
            List<MatchGamePlayer> lines,
            Map<MatchGame, Map<Long, Long>> teamKills,
            Comparator<StatsDto.TeamGame> order) {
        Map<Long, Team> teams = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            if (line.getTeam() != null) {
                teams.put(line.getTeam().getId(), line.getTeam());
            }
        }
        List<StatsDto.TeamGame> result = new ArrayList<>();
        teamKills.forEach((game, kills) -> {
            if (kills.size() != 2) {
                return;
            }
            List<Long> ids = new ArrayList<>(kills.keySet());
            Match match = game.getMatch();
            for (int i = 0; i < 2; i++) {
                Team team = teams.get(ids.get(i));
                Team opponent = teams.get(ids.get(1 - i));
                result.add(new StatsDto.TeamGame(
                        team.getId(),
                        team.getCode(),
                        team.getLogo() != null,
                        kills.get(team.getId()),
                        kills.get(opponent.getId()),
                        game.getWinner() != null ? StatTally.sameTeam(game.getWinner(), team) : null,
                        match != null ? match.getId() : null,
                        match != null ? match.getDate() : null,
                        match != null && match.getCompetition() != null
                                ? match.getCompetition().getCode()
                                : null,
                        opponent.getCode()));
            }
        });
        return result.stream().sorted(order).limit(RECORDS).toList();
    }

    /** Bans faits par chaque equipe, et subis (faits par son adversaire de la manche). */
    private record BanCounts(Map<Long, Map<String, Long>> made, Map<Long, Map<String, Long>> received) {}

    private static BanCounts banCounts(List<MatchGamePlayer> lines, List<MatchGameBan> bans) {
        Map<MatchGame, Set<Long>> teamsOfGame = teamsOfGame(lines, bans);
        Map<Long, Map<String, Long>> made = new HashMap<>();
        Map<Long, Map<String, Long>> received = new HashMap<>();
        for (MatchGameBan ban : bans) {
            if (ban.getTeam() == null) {
                continue;
            }
            Long by = ban.getTeam().getId();
            made.computeIfAbsent(by, id -> new HashMap<>()).merge(ban.getChampion(), 1L, Long::sum);
            for (Long other : teamsOfGame.getOrDefault(ban.getGame(), Set.of())) {
                if (!other.equals(by)) {
                    received.computeIfAbsent(other, id -> new HashMap<>()).merge(ban.getChampion(), 1L, Long::sum);
                }
            }
        }
        return new BanCounts(made, received);
    }

    /** Equipes de chaque manche, d'apres les joueurs et les bans saisis. */
    private static Map<MatchGame, Set<Long>> teamsOfGame(List<MatchGamePlayer> lines, List<MatchGameBan> bans) {
        Map<MatchGame, Set<Long>> result = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            if (line.getGame() != null && line.getTeam() != null) {
                result.computeIfAbsent(line.getGame(), g -> new HashSet<>())
                        .add(line.getTeam().getId());
            }
        }
        for (MatchGameBan ban : bans) {
            if (ban.getGame() != null && ban.getTeam() != null) {
                result.computeIfAbsent(ban.getGame(), g -> new HashSet<>())
                        .add(ban.getTeam().getId());
            }
        }
        return result;
    }

    /** Bans faits et subis par equipe, des equipes ayant le plus de manches aux autres. */
    private List<StatsDto.TeamBans> teamBans(List<MatchGamePlayer> lines, List<MatchGameBan> bans) {
        Map<Long, Team> teams = new LinkedHashMap<>();
        Map<Long, Set<MatchGame>> games = new HashMap<>();
        for (MatchGameBan ban : bans) {
            if (ban.getTeam() != null && ban.getGame() != null) {
                teams.put(ban.getTeam().getId(), ban.getTeam());
                games.computeIfAbsent(ban.getTeam().getId(), id -> new HashSet<>())
                        .add(ban.getGame());
            }
        }
        BanCounts counts = banCounts(lines, bans);
        return teams.values().stream()
                .map(team -> {
                    Map<String, Long> made = counts.made().getOrDefault(team.getId(), Map.of());
                    Map<String, Long> received = counts.received().getOrDefault(team.getId(), Map.of());
                    return new StatsDto.TeamBans(
                            team.getId(),
                            team.getCode(),
                            team.getName(),
                            team.getLogo() != null,
                            games.get(team.getId()).size(),
                            total(made),
                            mostFrequent(made, TOP_BANS),
                            total(received),
                            mostFrequent(received, TOP_BANS));
                })
                .sorted(Comparator.comparingLong(StatsDto.TeamBans::games)
                        .reversed()
                        .thenComparing(StatsDto.TeamBans::teamCode, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Joueurs les plus cibles : bans adverses, sur une manche qu'ils jouent, d'un champion de
     * leur pool (joue au moins POOL_MIN_GAMES fois dans le perimetre).
     */
    private List<StatsDto.PlayerBans> playerBans(
            List<MatchGamePlayer> lines, List<MatchGameBan> bans, Map<Long, Profile> profiles) {
        Map<Long, Map<String, Long>> pools = new HashMap<>();
        Map<Long, Player> players = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            players.put(line.getPlayer().getId(), line.getPlayer());
            pools.computeIfAbsent(line.getPlayer().getId(), id -> new HashMap<>())
                    .merge(line.getChampion(), 1L, Long::sum);
        }
        Map<MatchGame, List<MatchGameBan>> bansOfGame = new HashMap<>();
        for (MatchGameBan ban : bans) {
            if (ban.getGame() != null && ban.getTeam() != null) {
                bansOfGame
                        .computeIfAbsent(ban.getGame(), g -> new ArrayList<>())
                        .add(ban);
            }
        }
        Map<Long, Long> games = new HashMap<>();
        Map<Long, Map<String, Long>> targeted = new HashMap<>();
        for (MatchGamePlayer line : lines) {
            if (line.getGame() == null || line.getTeam() == null) {
                continue;
            }
            Long playerId = line.getPlayer().getId();
            games.merge(playerId, 1L, Long::sum);
            Map<String, Long> pool = pools.get(playerId);
            for (MatchGameBan ban : bansOfGame.getOrDefault(line.getGame(), List.of())) {
                if (!StatTally.sameTeam(ban.getTeam(), line.getTeam())
                        && pool.getOrDefault(ban.getChampion(), 0L) >= POOL_MIN_GAMES) {
                    targeted.computeIfAbsent(playerId, id -> new HashMap<>()).merge(ban.getChampion(), 1L, Long::sum);
                }
            }
        }
        return targeted.entrySet().stream()
                .map(e -> {
                    Player p = players.get(e.getKey());
                    Profile profile = profiles.getOrDefault(p.getId(), Profile.of(p));
                    Team team = profile.team();
                    long count = total(e.getValue());
                    long played = games.get(p.getId());
                    return new StatsDto.PlayerBans(
                            p.getId(),
                            p.getPseudo(),
                            team != null ? team.getId() : null,
                            team != null ? team.getCode() : null,
                            team != null && team.getLogo() != null,
                            profile.position(),
                            played,
                            count,
                            (double) count / played,
                            mostFrequent(e.getValue(), TOP_BANS));
                })
                .sorted(Comparator.comparingLong(StatsDto.PlayerBans::targetedBans)
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(StatsDto.PlayerBans::targetedPerGame)
                                .reversed()))
                .limit(TARGETED_PLAYERS)
                .toList();
    }

    private static long total(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    /** Champions les plus frequents ; a egalite, dans l'ordre alphabetique. */
    private static List<StatsDto.ChampionPick> mostFrequent(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .map(c -> new StatsDto.ChampionPick(c.getKey(), c.getValue()))
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
