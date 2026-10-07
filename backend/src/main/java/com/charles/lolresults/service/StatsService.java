package com.charles.lolresults.service;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.MatchGameBan;
import com.charles.lolresults.domain.MatchGamePlayer;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.dto.StatsDto;
import com.charles.lolresults.repository.MatchGameBanRepository;
import com.charles.lolresults.repository.MatchGamePlayerRepository;
import com.charles.lolresults.repository.MatchGameRepository;
import com.charles.lolresults.repository.MatchRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Statistiques (picks/bans de champions, MVP, K/D/A cumule) recalculees a la volee a partir
 * des details de match saisis, jamais stockees : meme principe que StandingsService. Le
 * perimetre est une competition, une saison (toutes competitions confondues), ou tout
 * l'historique quand ni l'un ni l'autre n'est precise.
 */
@Service
public class StatsService {

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

        return new StatsDto(championStats(lines, bans), mvpStats(matches, games), kdaStats(lines));
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

    private List<StatsDto.ChampionStat> championStats(List<MatchGamePlayer> lines, List<MatchGameBan> bans) {
        Map<String, long[]> byChampion = new LinkedHashMap<>(); // [picks, bans]
        for (MatchGamePlayer line : lines) {
            byChampion.computeIfAbsent(line.getChampion(), c -> new long[2])[0]++;
        }
        for (MatchGameBan ban : bans) {
            byChampion.computeIfAbsent(ban.getChampion(), c -> new long[2])[1]++;
        }
        return byChampion.entrySet().stream()
                .map(e -> new StatsDto.ChampionStat(
                        e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[0] + e.getValue()[1]))
                .sorted(Comparator.comparingLong(StatsDto.ChampionStat::pickAndBan)
                        .reversed())
                .toList();
    }

    private List<StatsDto.MvpStat> mvpStats(List<Match> matches, List<MatchGame> games) {
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
            result.add(new StatsDto.MvpStat(
                    playerId, p.getPseudo(), p.getTeam() != null ? p.getTeam().getCode() : null, counts[0], counts[1]));
        });
        return result.stream()
                .sorted(Comparator.comparingLong((StatsDto.MvpStat s) -> s.seriesMvp() + s.gameMvp())
                        .reversed())
                .toList();
    }

    private List<StatsDto.KdaStat> kdaStats(List<MatchGamePlayer> lines) {
        Map<Long, long[]> byPlayer = new LinkedHashMap<>(); // [kills, deaths, assists]
        Map<Long, Player> players = new LinkedHashMap<>();
        for (MatchGamePlayer line : lines) {
            if (line.getKills() == null && line.getDeaths() == null && line.getAssists() == null) {
                continue;
            }
            long[] tally = byPlayer.computeIfAbsent(line.getPlayer().getId(), id -> new long[3]);
            tally[0] += orZero(line.getKills());
            tally[1] += orZero(line.getDeaths());
            tally[2] += orZero(line.getAssists());
            players.put(line.getPlayer().getId(), line.getPlayer());
        }
        return byPlayer.entrySet().stream()
                .map(e -> {
                    Player p = players.get(e.getKey());
                    long kills = e.getValue()[0];
                    long deaths = e.getValue()[1];
                    long assists = e.getValue()[2];
                    double ratio = (double) (kills + assists) / Math.max(deaths, 1);
                    return new StatsDto.KdaStat(
                            p.getId(),
                            p.getPseudo(),
                            p.getTeam() != null ? p.getTeam().getCode() : null,
                            kills,
                            deaths,
                            assists,
                            ratio);
                })
                .sorted(Comparator.comparingDouble(StatsDto.KdaStat::ratio).reversed())
                .toList();
    }

    private static long orZero(Integer value) {
        return value != null ? value : 0;
    }
}
