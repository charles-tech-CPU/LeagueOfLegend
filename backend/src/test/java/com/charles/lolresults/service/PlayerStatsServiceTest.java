package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.MatchGamePlayer;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.Position;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.PlayerStatsDto;
import com.charles.lolresults.repository.MatchGamePlayerRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerStatsServiceTest {

    private static final Long CAPS_ID = 10L;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private MatchGamePlayerRepository gamePlayerRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private PlayerStatsService playerStatsService;

    private Team g2;
    private Team fnc;
    private Team mad;
    private Player caps;
    private Competition lec2023;
    private Competition lec2024;
    private Competition worlds2024;

    @BeforeEach
    void setUp() {
        g2 = team(1L, "G2");
        fnc = team(2L, "FNC");
        mad = team(3L, "MAD");
        caps = new Player(g2, "Caps", null, Position.MID);
        caps.setId(CAPS_ID);
        lec2023 = competition(100L, "LEC", 2023);
        lec2024 = competition(101L, "LEC", 2024);
        worlds2024 = competition(102L, "WORLDS", 2024);
    }

    @Test
    void cumuleLaCarriereParCompetitionParSaisonEtParChampion() {
        Match old = match(lec2023, g2, mad, LocalDate.of(2023, 3, 1));
        Match spring = match(lec2024, g2, fnc, LocalDate.of(2024, 3, 1));
        Match worlds = match(worlds2024, fnc, g2, LocalDate.of(2024, 10, 1));
        MatchGame g1 = game(old, 1, g2);
        MatchGame g2Game = game(spring, 1, fnc);
        MatchGame g3 = game(worlds, 1, g2);
        g3.setMvp(caps);
        List<MatchGamePlayer> lines = List.of(
                line(g1, g2, "Azir", 5, 1, 5), line(g2Game, g2, "Azir", 1, 4, 2), line(g3, g2, "Orianna", 8, 0, 4));
        // Coequipier dans la manche 3 : G2 a 14 kills.
        teammate(g3, g2, 6);

        when(playerRepository.existsById(CAPS_ID)).thenReturn(true);
        when(gamePlayerRepository.findByPlayer_Id(CAPS_ID)).thenReturn(lines);
        Match mvpSeries = match(worlds2024, fnc, g2, LocalDate.of(2024, 10, 1));
        when(matchRepository.findByMvp_Id(CAPS_ID)).thenReturn(List.of(mvpSeries));

        PlayerStatsDto stats = playerStatsService.compute(CAPS_ID);

        PlayerStatsDto.Tally career = stats.career();
        assertThat(career.games()).isEqualTo(3L);
        assertThat(career.wins()).isEqualTo(2L);
        assertThat(career.losses()).isEqualTo(1L);
        assertThat(career.kills()).isEqualTo(14L);
        assertThat(career.deaths()).isEqualTo(5L);
        assertThat(career.assists()).isEqualTo(11L);
        assertThat(career.ratio()).isEqualTo(5.0);
        assertThat(career.perfectGames()).isEqualTo(1L);
        assertThat(career.gameMvp()).isEqualTo(1L);
        assertThat(career.championCount()).isEqualTo(2L);
        assertThat(stats.seriesMvp()).isEqualTo(1L);

        // Du plus recent au plus ancien.
        assertThat(stats.competitions())
                .extracting(PlayerStatsDto.CompetitionLine::competitionId)
                .containsExactly(102L, 101L, 100L);
        PlayerStatsDto.CompetitionLine worldsLine = stats.competitions().get(0);
        assertThat(worldsLine.teamCode()).isEqualTo("G2");
        assertThat(worldsLine.seriesMvp()).isEqualTo(1L);
        // (8 + 4) sur 14 kills de G2.
        assertThat(worldsLine.stats().killParticipation()).isEqualTo(12.0 / 14);

        assertThat(stats.seasons())
                .extracting(PlayerStatsDto.SeasonLine::season)
                .containsExactly(2024, 2023);
        assertThat(stats.seasons().get(0).stats().games()).isEqualTo(2L);
        assertThat(stats.seasons().get(0).seriesMvp()).isEqualTo(1L);

        assertThat(stats.champions())
                .extracting(PlayerStatsDto.ChampionLine::champion)
                .containsExactly("Azir", "Orianna");
        assertThat(stats.champions().get(0).stats().wins()).isEqualTo(1L);

        assertThat(stats.opponents()).hasSize(2);
        assertThat(stats.opponents().get(0).teamCode()).isEqualTo("FNC");
        assertThat(stats.opponents().get(0).stats().games()).isEqualTo(2L);

        assertThat(stats.bestGames().get(0).champion()).isEqualTo("Orianna");
        assertThat(stats.recentGames()).extracting(p -> p.opponentCode()).containsExactly("FNC", "FNC", "MAD");
    }

    @Test
    void joueurInconnu() {
        when(playerRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> playerStatsService.compute(99L)).isInstanceOf(EntityNotFoundException.class);
    }

    private MatchGamePlayer line(MatchGame game, Team team, String champion, int k, int d, int a) {
        MatchGamePlayer line = new MatchGamePlayer();
        line.setGame(game);
        line.setPlayer(caps);
        line.setTeam(team);
        line.setPosition(Position.MID);
        line.setChampion(champion);
        line.setKills(k);
        line.setDeaths(d);
        line.setAssists(a);
        game.getPlayers().add(line);
        return line;
    }

    private static void teammate(MatchGame game, Team team, int kills) {
        MatchGamePlayer line = new MatchGamePlayer();
        line.setGame(game);
        line.setTeam(team);
        line.setKills(kills);
        game.getPlayers().add(line);
    }

    private static MatchGame game(Match match, int number, Team winner) {
        MatchGame game = new MatchGame(match, number);
        game.setWinner(winner);
        return game;
    }

    private static Match match(Competition competition, Team team1, Team team2, LocalDate date) {
        Match match = new Match();
        match.setCompetition(competition);
        match.setTeam1(team1);
        match.setTeam2(team2);
        match.setDate(date);
        return match;
    }

    private static Competition competition(Long id, String code, int season) {
        Competition competition =
                new Competition(code, code + " " + season, CompetitionType.REGIONAL_LEAGUE, null, season);
        competition.setId(id);
        return competition;
    }

    private static Team team(Long id, String code) {
        Team team = new Team();
        team.setId(id);
        team.setCode(code);
        return team;
    }
}
