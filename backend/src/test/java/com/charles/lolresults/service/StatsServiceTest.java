package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final Long COMPETITION_ID = 1L;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchGameRepository gameRepository;

    @Mock
    private MatchGamePlayerRepository gamePlayerRepository;

    @Mock
    private MatchGameBanRepository gameBanRepository;

    @InjectMocks
    private StatsService statsService;

    private Team g2;
    private Team fnc;
    private Player caps;
    private Player humanoid;

    @BeforeEach
    void setUp() {
        g2 = team(1L, "G2");
        fnc = team(2L, "FNC");
        caps = player(10L, "Caps", g2);
        humanoid = player(20L, "Humanoid", fnc);
    }

    @Test
    void cumuleLesPicksEtBansParChampion() {
        when(gamePlayerRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(line(caps, g2, "Azir", 4, 1, 6), line(humanoid, fnc, "Orianna", 2, 3, 5)));
        when(gameBanRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(ban(g2, "Zed"), ban(fnc, "Azir")));
        when(matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(COMPETITION_ID))
                .thenReturn(List.of());
        when(gameRepository.findByMatch_Competition_Id(COMPETITION_ID)).thenReturn(List.of());

        StatsDto stats = statsService.compute(COMPETITION_ID, null);

        assertThat(stats.champions())
                .filteredOn(c -> c.champion().equals("Azir"))
                .extracting(
                        StatsDto.ChampionStat::picks, StatsDto.ChampionStat::bans, StatsDto.ChampionStat::pickAndBan)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 1L, 2L));
    }

    @Test
    void unePortaDeSaisonInterrogeLesRepositoriesParSaison() {
        Integer season = 2024;
        when(gamePlayerRepository.findByGame_Match_Competition_Season(season))
                .thenReturn(List.of(line(caps, g2, "Azir", 4, 1, 6)));
        when(gameBanRepository.findByGame_Match_Competition_Season(season)).thenReturn(List.of(ban(g2, "Zed")));
        when(matchRepository.findByCompetition_Season(season)).thenReturn(List.of());
        when(gameRepository.findByMatch_Competition_Season(season)).thenReturn(List.of());

        StatsDto stats = statsService.compute(null, season);

        assertThat(stats.champions())
                .extracting(StatsDto.ChampionStat::champion)
                .containsExactlyInAnyOrder("Azir", "Zed");
    }

    @Test
    void sansPerimetrePreciseCouvreToutLHistorique() {
        when(gamePlayerRepository.findAll()).thenReturn(List.of(line(caps, g2, "Azir", 4, 1, 6)));
        when(gameBanRepository.findAll()).thenReturn(List.of());
        when(matchRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findAll()).thenReturn(List.of());

        StatsDto stats = statsService.compute(null, null);

        assertThat(stats.champions())
                .extracting(StatsDto.ChampionStat::champion)
                .containsExactly("Azir");
    }

    @Test
    void compteLeMvpDeSerieEtLeMvpDeManche() {
        Match match = new Match();
        match.setMvp(caps);
        MatchGame game = new MatchGame();
        game.setMvp(caps);

        when(matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(COMPETITION_ID))
                .thenReturn(List.of(match));
        when(gameRepository.findByMatch_Competition_Id(COMPETITION_ID)).thenReturn(List.of(game));
        when(gamePlayerRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of());
        when(gameBanRepository.findByGame_Match_Competition_Id(COMPETITION_ID)).thenReturn(List.of());

        StatsDto stats = statsService.compute(COMPETITION_ID, null);

        assertThat(stats.mvps()).hasSize(1);
        StatsDto.MvpStat capsStat = stats.mvps().get(0);
        assertThat(capsStat.pseudo()).isEqualTo("Caps");
        assertThat(capsStat.seriesMvp()).isEqualTo(1L);
        assertThat(capsStat.gameMvp()).isEqualTo(1L);
    }

    @Test
    void cumuleLeKdaParJoueurEtCalculeLeRatio() {
        when(gamePlayerRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(line(caps, g2, "Azir", 4, 2, 6), line(caps, g2, "Orianna", 6, 0, 4)));
        when(matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(COMPETITION_ID))
                .thenReturn(List.of());
        when(gameRepository.findByMatch_Competition_Id(COMPETITION_ID)).thenReturn(List.of());
        when(gameBanRepository.findByGame_Match_Competition_Id(COMPETITION_ID)).thenReturn(List.of());

        StatsDto stats = statsService.compute(COMPETITION_ID, null);

        assertThat(stats.kda()).hasSize(1);
        StatsDto.KdaStat capsStat = stats.kda().get(0);
        assertThat(capsStat.kills()).isEqualTo(10L);
        assertThat(capsStat.deaths()).isEqualTo(2L);
        assertThat(capsStat.assists()).isEqualTo(10L);
        assertThat(capsStat.ratio()).isEqualTo(10.0);
    }

    private static MatchGamePlayer line(Player player, Team team, String champion, Integer k, Integer d, Integer a) {
        MatchGamePlayer line = new MatchGamePlayer();
        line.setPlayer(player);
        line.setTeam(team);
        line.setPosition(Position.MID);
        line.setChampion(champion);
        line.setKills(k);
        line.setDeaths(d);
        line.setAssists(a);
        return line;
    }

    private static MatchGameBan ban(Team team, String champion) {
        MatchGameBan ban = new MatchGameBan();
        ban.setTeam(team);
        ban.setChampion(champion);
        return ban;
    }

    private static Team team(Long id, String code) {
        Team team = new Team();
        team.setId(id);
        team.setCode(code);
        return team;
    }

    private static Player player(Long id, String pseudo, Team team) {
        Player player = new Player(team, pseudo, null, Position.MID);
        player.setId(id);
        return player;
    }
}
