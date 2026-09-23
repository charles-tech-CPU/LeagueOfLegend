package com.charles.lolresults.service;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchPhase;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.HeadToHeadCellDto;
import com.charles.lolresults.dto.StandingRowDto;
import com.charles.lolresults.repository.MatchRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Classement et tete-a-tete ne sont jamais stockes : ils sont toujours
 * recalcules a partir des matchs COMPLETED. C'est le choix de conception
 * central du projet (voir README) : une seule source de verite (Match),
 * zero risque d'incoherence entre "resultats" et "classement" comme cela
 * pouvait arriver dans le fichier Excel maintenu a la main.
 */
@Service
public class StandingsService {

    private final MatchRepository matchRepository;

    public StandingsService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    public List<StandingRowDto> computeStandings(Long competitionId, Long groupId) {
        Map<Long, TeamTally> byTeam = new LinkedHashMap<>();

        for (Match m : playedMatches(competitionId, groupId)) {
            tallyFor(byTeam, m.getTeam1()).addResult(m.getScore1(), m.getScore2());
            tallyFor(byTeam, m.getTeam2()).addResult(m.getScore2(), m.getScore1());
        }

        return byTeam.values().stream()
                .map(TeamTally::toDto)
                .sorted(Comparator.comparingInt(StandingRowDto::seriesWon)
                        .reversed()
                        .thenComparing(StandingRowDto::seriesLost)
                        .thenComparing(Comparator.comparingInt(StandingRowDto::gamesWon)
                                .reversed()))
                .toList();
    }

    public List<HeadToHeadCellDto> computeHeadToHead(Long competitionId, Long groupId) {
        Map<Long, Map<Long, int[]>> tally = new LinkedHashMap<>();

        for (Match m : playedMatches(competitionId, groupId)) {
            addH2H(tally, m.getTeam1().getId(), m.getTeam2().getId(), m.getScore1(), m.getScore2());
            addH2H(tally, m.getTeam2().getId(), m.getTeam1().getId(), m.getScore2(), m.getScore1());
        }

        List<HeadToHeadCellDto> result = new ArrayList<>();
        tally.forEach((teamAId, opponents) ->
                opponents.forEach((teamBId, wl) -> result.add(new HeadToHeadCellDto(teamAId, teamBId, wl[0], wl[1]))));
        return result;
    }

    /**
     * Seuls les matchs de saison reguliere comptent dans le classement et le
     * tete-a-tete : les matchs de playoffs (phase PLAYOFFS) font avancer les
     * equipes dans un bracket (voir MatchService.propagateAdvancement) mais
     * ne doivent jamais influencer un classement de saison reguliere.
     */
    private List<Match> playedMatches(Long competitionId, Long groupId) {
        return matchRepository
                .findByCompetitionIdAndStatusOrderByDateAscTimeAsc(competitionId, MatchStatus.COMPLETED)
                .stream()
                .filter(m -> m.getPhase() == MatchPhase.REGULAR_SEASON)
                .filter(m -> groupId == null
                        || (m.getGroup() != null && groupId.equals(m.getGroup().getId())))
                .toList();
    }

    private TeamTally tallyFor(Map<Long, TeamTally> byTeam, Team team) {
        return byTeam.computeIfAbsent(team.getId(), id -> new TeamTally(team));
    }

    private void addH2H(Map<Long, Map<Long, int[]>> tally, Long teamAId, Long teamBId, Integer scoreA, Integer scoreB) {
        if (scoreA == null || scoreB == null) {
            return;
        }
        int[] wl =
                tally.computeIfAbsent(teamAId, id -> new LinkedHashMap<>()).computeIfAbsent(teamBId, id -> new int[2]);
        if (scoreA > scoreB) {
            wl[0]++;
        } else {
            wl[1]++;
        }
    }

    /** Petit accumulateur mutable, interne au calcul, jamais expose ni persiste. */
    private static final class TeamTally {
        private final Team team;
        private int seriesWon;
        private int seriesLost;
        private int gamesWon;
        private int gamesLost;

        private TeamTally(Team team) {
            this.team = team;
        }

        private void addResult(Integer scoreFor, Integer scoreAgainst) {
            if (scoreFor == null || scoreAgainst == null) {
                return;
            }
            if (scoreFor > scoreAgainst) {
                seriesWon++;
            } else {
                seriesLost++;
            }
            gamesWon += scoreFor;
            gamesLost += scoreAgainst;
        }

        private StandingRowDto toDto() {
            return new StandingRowDto(
                    team.getId(),
                    team.getCode(),
                    team.getName(),
                    team.getLogo() != null,
                    seriesWon,
                    seriesLost,
                    gamesWon,
                    gamesLost);
        }
    }
}
