package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.CompetitionGroup;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchPhase;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.HeadToHeadCellDto;
import com.charles.lolresults.dto.StandingRowDto;
import com.charles.lolresults.repository.MatchRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StandingsServiceTest {

    private static final Long COMPETITION_ID = 1L;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private StandingsService standingsService;

    private Team g2;
    private Team fnc;
    private Team kc;

    @BeforeEach
    void setUp() {
        g2 = team(1L, "G2");
        fnc = team(2L, "FNC");
        kc = team(3L, "KC");
    }

    @Test
    void classementTrieParSeriesGagneesPuisPerduesPuisGamesGagnees() {
        givenPlayedMatches(
                match(g2, fnc, 2, 0, MatchPhase.REGULAR_SEASON),
                match(kc, fnc, 2, 1, MatchPhase.REGULAR_SEASON),
                match(g2, kc, 1, 2, MatchPhase.REGULAR_SEASON));

        List<StandingRowDto> standings = standingsService.computeStandings(COMPETITION_ID, null);

        assertThat(standings).extracting(StandingRowDto::teamCode).containsExactly("KC", "G2", "FNC");
        StandingRowDto kcRow = standings.get(0);
        assertThat(kcRow.seriesWon()).isEqualTo(2);
        assertThat(kcRow.seriesLost()).isZero();
        assertThat(kcRow.gamesWon()).isEqualTo(4);
        assertThat(kcRow.gamesLost()).isEqualTo(2);
    }

    @Test
    void lesMatchsDePlayoffsNeComptentPasDansLeClassement() {
        givenPlayedMatches(match(g2, fnc, 2, 0, MatchPhase.REGULAR_SEASON), match(fnc, g2, 3, 0, MatchPhase.PLAYOFFS));

        List<StandingRowDto> standings = standingsService.computeStandings(COMPETITION_ID, null);

        assertThat(standings).extracting(StandingRowDto::seriesWon).containsExactly(1, 0);
    }

    @Test
    void filtreParGroupe() {
        CompetitionGroup groupA = group(10L);
        CompetitionGroup groupB = group(20L);
        Match inA = match(g2, fnc, 2, 0, MatchPhase.REGULAR_SEASON);
        inA.setGroup(groupA);
        Match inB = match(kc, fnc, 2, 0, MatchPhase.REGULAR_SEASON);
        inB.setGroup(groupB);
        givenPlayedMatches(inA, inB);

        List<StandingRowDto> standings = standingsService.computeStandings(COMPETITION_ID, 10L);

        assertThat(standings).extracting(StandingRowDto::teamCode).containsExactlyInAnyOrder("G2", "FNC");
    }

    @Test
    void teteATeteCumuleLesSeriesDansLesDeuxSens() {
        givenPlayedMatches(
                match(g2, fnc, 2, 0, MatchPhase.REGULAR_SEASON), match(fnc, g2, 2, 1, MatchPhase.REGULAR_SEASON));

        List<HeadToHeadCellDto> cells = standingsService.computeHeadToHead(COMPETITION_ID, null);

        assertThat(cells)
                .containsExactlyInAnyOrder(new HeadToHeadCellDto(1L, 2L, 1, 1), new HeadToHeadCellDto(2L, 1L, 1, 1));
    }

    private void givenPlayedMatches(Match... matches) {
        when(matchRepository.findByCompetitionIdAndStatusOrderByDateAscTimeAsc(COMPETITION_ID, MatchStatus.COMPLETED))
                .thenReturn(List.of(matches));
    }

    private static Team team(Long id, String code) {
        Team team = new Team(code, code, null);
        team.setId(id);
        return team;
    }

    private static CompetitionGroup group(Long id) {
        CompetitionGroup group = new CompetitionGroup();
        group.setId(id);
        return group;
    }

    private static Match match(Team team1, Team team2, int score1, int score2, MatchPhase phase) {
        Match match = new Match();
        match.setTeam1(team1);
        match.setTeam2(team2);
        match.setScore1(score1);
        match.setScore2(score2);
        match.setPhase(phase);
        match.setStatus(MatchStatus.COMPLETED);
        return match;
    }
}
