package com.charles.lolresults.service;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.MatchGamePlayer;
import com.charles.lolresults.domain.Position;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.PlayerStatsDto;
import com.charles.lolresults.repository.MatchGamePlayerRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statistiques de la fiche joueur, recalculees a la volee a partir de ses manches saisies :
 * jamais stockees, meme principe que StatsService.
 */
@Service
@Transactional(readOnly = true)
public class PlayerStatsService {

    private static final int TOP_CHAMPIONS = 3;
    private static final int BEST_GAMES = 5;
    private static final int RECENT_GAMES = 10;

    private static final Comparator<PlayerStatsDto.Tally> MOST_PLAYED = Comparator.comparingLong(
                    PlayerStatsDto.Tally::games)
            .reversed()
            .thenComparing(
                    Comparator.comparingDouble(PlayerStatsDto.Tally::ratio).reversed());

    private final PlayerRepository playerRepository;
    private final MatchGamePlayerRepository gamePlayerRepository;
    private final MatchRepository matchRepository;

    public PlayerStatsService(
            PlayerRepository playerRepository,
            MatchGamePlayerRepository gamePlayerRepository,
            MatchRepository matchRepository) {
        this.playerRepository = playerRepository;
        this.gamePlayerRepository = gamePlayerRepository;
        this.matchRepository = matchRepository;
    }

    public PlayerStatsDto compute(Long playerId) {
        if (!playerRepository.existsById(playerId)) {
            throw new EntityNotFoundException("Joueur introuvable : " + playerId);
        }
        // Du plus recent au plus ancien : competitions et adversaires sont listes dans cet ordre.
        List<MatchGamePlayer> lines = new ArrayList<>(gamePlayerRepository.findByPlayer_Id(playerId));
        lines.sort(StatsService.CHRONOLOGICAL.reversed());
        List<Match> mvpSeries = matchRepository.findByMvp_Id(playerId);

        StatTally career = new StatTally();
        Map<Long, StatTally> byCompetition = new LinkedHashMap<>();
        Map<Long, Competition> competitions = new HashMap<>();
        Map<Long, Team> competitionTeams = new HashMap<>();
        Map<Integer, StatTally> bySeason = new TreeMap<>(Comparator.reverseOrder());
        Map<String, StatTally> byChampion = new HashMap<>();
        Map<Position, StatTally> byPosition = new EnumMap<>(Position.class);
        Map<Long, StatTally> byOpponent = new LinkedHashMap<>();
        Map<Long, Team> opponents = new HashMap<>();

        for (MatchGamePlayer line : lines) {
            long teamKills = teamKills(line);
            career.add(line, teamKills);
            byChampion.computeIfAbsent(line.getChampion(), c -> new StatTally()).add(line, teamKills);
            byPosition.computeIfAbsent(line.getPosition(), p -> new StatTally()).add(line, teamKills);

            Match match = line.getGame() != null ? line.getGame().getMatch() : null;
            Competition competition = match != null ? match.getCompetition() : null;
            if (competition != null) {
                byCompetition
                        .computeIfAbsent(competition.getId(), id -> new StatTally())
                        .add(line, teamKills);
                competitions.putIfAbsent(competition.getId(), competition);
                competitionTeams.putIfAbsent(competition.getId(), line.getTeam());
                bySeason.computeIfAbsent(competition.getSeason(), s -> new StatTally())
                        .add(line, teamKills);
            }
            Team opponent = opponent(match, line.getTeam());
            if (opponent != null) {
                byOpponent
                        .computeIfAbsent(opponent.getId(), id -> new StatTally())
                        .add(line, teamKills);
                opponents.putIfAbsent(opponent.getId(), opponent);
            }
        }

        Map<Long, Long> mvpByCompetition = new HashMap<>();
        Map<Integer, Long> mvpBySeason = new HashMap<>();
        for (Match m : mvpSeries) {
            if (m.getCompetition() != null) {
                mvpByCompetition.merge(m.getCompetition().getId(), 1L, Long::sum);
                mvpBySeason.merge(m.getCompetition().getSeason(), 1L, Long::sum);
            }
        }

        return new PlayerStatsDto(
                career.toDto(),
                mvpSeries.size(),
                byCompetition.entrySet().stream()
                        .map(e -> {
                            Competition c = competitions.get(e.getKey());
                            Team team = competitionTeams.get(e.getKey());
                            return new PlayerStatsDto.CompetitionLine(
                                    c.getId(),
                                    c.getCode(),
                                    c.getName(),
                                    c.getSeason(),
                                    team != null ? team.getId() : null,
                                    team != null ? team.getCode() : null,
                                    team != null && team.getLogo() != null,
                                    e.getValue().toDto(),
                                    mvpByCompetition.getOrDefault(c.getId(), 0L),
                                    e.getValue().topChampions(TOP_CHAMPIONS));
                        })
                        .toList(),
                bySeason.entrySet().stream()
                        .map(e -> new PlayerStatsDto.SeasonLine(
                                e.getKey(),
                                e.getValue().toDto(),
                                mvpBySeason.getOrDefault(e.getKey(), 0L),
                                e.getValue().topChampions(TOP_CHAMPIONS)))
                        .toList(),
                byChampion.entrySet().stream()
                        .map(e -> new PlayerStatsDto.ChampionLine(
                                e.getKey(), e.getValue().toDto()))
                        .sorted(Comparator.comparing(PlayerStatsDto.ChampionLine::stats, MOST_PLAYED)
                                .thenComparing(PlayerStatsDto.ChampionLine::champion))
                        .toList(),
                byPosition.entrySet().stream()
                        .map(e -> new PlayerStatsDto.PositionLine(
                                e.getKey(), e.getValue().toDto()))
                        .toList(),
                byOpponent.entrySet().stream()
                        .map(e -> {
                            Team t = opponents.get(e.getKey());
                            return new PlayerStatsDto.OpponentLine(
                                    t.getId(),
                                    t.getCode(),
                                    t.getName(),
                                    t.getLogo() != null,
                                    e.getValue().toDto());
                        })
                        .sorted(Comparator.comparing(PlayerStatsDto.OpponentLine::stats, MOST_PLAYED))
                        .toList(),
                StatTally.top(lines, StatTally.BEST_KDA, BEST_GAMES),
                lines.stream().limit(RECENT_GAMES).map(StatTally::performance).toList());
    }

    /** Kills de l'equipe du joueur sur la manche, a partir de toutes les lignes saisies de la manche. */
    private static long teamKills(MatchGamePlayer line) {
        MatchGame game = line.getGame();
        if (game == null || line.getTeam() == null) {
            return 0;
        }
        return game.getPlayers().stream()
                .filter(other -> StatTally.sameTeam(other.getTeam(), line.getTeam()))
                .map(MatchGamePlayer::getKills)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();
    }

    private static Team opponent(Match match, Team team) {
        if (match == null || team == null) {
            return null;
        }
        return StatTally.sameTeam(match.getTeam1(), team) ? match.getTeam2() : match.getTeam1();
    }
}
