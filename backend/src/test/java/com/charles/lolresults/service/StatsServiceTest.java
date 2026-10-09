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
import java.time.LocalDate;
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

    @Test
    void afficheLEquipeDeLaDerniereMancheEtLePostePrincipal() {
        // Caps joue d'abord chez FNC puis chez G2, deux fois MID et une fois ADC.
        MatchGamePlayer old = line(caps, fnc, "Ahri", 1, 1, 1, Position.MID, LocalDate.of(2018, 5, 1), 1);
        MatchGamePlayer recentGame1 = line(caps, g2, "Ezreal", 2, 1, 2, Position.ADC, LocalDate.of(2024, 5, 1), 1);
        MatchGamePlayer recentGame2 = line(caps, g2, "Azir", 3, 0, 4, Position.MID, LocalDate.of(2024, 5, 1), 2);
        when(gamePlayerRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(recentGame2, old, recentGame1));
        Match match = new Match();
        match.setMvp(caps);
        when(matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(COMPETITION_ID))
                .thenReturn(List.of(match));
        when(gameRepository.findByMatch_Competition_Id(COMPETITION_ID)).thenReturn(List.of());
        when(gameBanRepository.findByGame_Match_Competition_Id(COMPETITION_ID)).thenReturn(List.of());

        StatsDto stats = statsService.compute(COMPETITION_ID, null);

        StatsDto.KdaStat kda = stats.kda().get(0);
        assertThat(kda.teamCode()).isEqualTo("G2");
        assertThat(kda.teamId()).isEqualTo(1L);
        assertThat(kda.position()).isEqualTo(Position.MID);
        assertThat(kda.games()).isEqualTo(3L);
        assertThat(stats.mvps().get(0).teamCode()).isEqualTo("G2");
        assertThat(stats.mvps().get(0).position()).isEqualTo(Position.MID);
    }

    @Test
    void cumuleParPosteAvecLesChampionsLesPlusJoues() {
        when(gamePlayerRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(
                        line(caps, g2, "Azir", 4, 1, 6),
                        line(humanoid, fnc, "Azir", 2, 3, 5),
                        line(humanoid, fnc, "Orianna", 1, 1, 1),
                        line(caps, g2, "Gnar", 3, 2, 1, Position.TOP, null, 1)));
        when(matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(COMPETITION_ID))
                .thenReturn(List.of());
        when(gameRepository.findByMatch_Competition_Id(COMPETITION_ID)).thenReturn(List.of());
        when(gameBanRepository.findByGame_Match_Competition_Id(COMPETITION_ID)).thenReturn(List.of());

        StatsDto stats = statsService.compute(COMPETITION_ID, null);

        assertThat(stats.positions())
                .extracting(StatsDto.PositionStat::position)
                .containsExactly(Position.TOP, Position.MID);
        StatsDto.PositionStat mid = stats.positions().get(1);
        assertThat(mid.games()).isEqualTo(3L);
        assertThat(mid.kills()).isEqualTo(7L);
        assertThat(mid.topChampions())
                .extracting(StatsDto.ChampionPick::champion, StatsDto.ChampionPick::picks)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Azir", 2L),
                        org.assertj.core.groups.Tuple.tuple("Orianna", 1L));
        assertThat(stats.champions())
                .filteredOn(c -> c.champion().equals("Azir"))
                .first()
                .extracting(StatsDto.ChampionStat::picksByPosition)
                .isEqualTo(java.util.Map.of(Position.MID, 2L));
    }

    private static MatchGamePlayer line(
            Player player,
            Team team,
            String champion,
            Integer k,
            Integer d,
            Integer a,
            Position position,
            LocalDate date,
            int gameNumber) {
        MatchGamePlayer line = line(player, team, champion, k, d, a);
        line.setPosition(position);
        Match match = new Match();
        match.setDate(date);
        MatchGame game = new MatchGame();
        game.setMatch(match);
        game.setGameNumber(gameNumber);
        line.setGame(game);
        return line;
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
