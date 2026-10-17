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

    @Test
    void calculeVictoiresParticipationAuxKillsEquipesEtRecords() {
        // Manche 1 gagnee par G2, manche 2 gagnee par FNC.
        MatchGame game1 = game(g2);
        MatchGame game2 = game(fnc);
        Player jankos = player(11L, "Jankos", g2);
        when(gamePlayerRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(
                        inGame(game1, line(caps, g2, "Azir", 6, 0, 1)),
                        inGame(game1, line(jankos, g2, "Lee Sin", 2, 1, 5)),
                        inGame(game1, line(humanoid, fnc, "Azir", 1, 4, 0)),
                        inGame(game2, line(caps, g2, "Azir", 1, 3, 0)),
                        inGame(game2, line(humanoid, fnc, "Orianna", 9, 1, 3))));
        when(gameBanRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(inGame(game1, ban(fnc, "Zed")), inGame(game2, ban(fnc, "Zed"))));
        when(matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(COMPETITION_ID))
                .thenReturn(List.of());
        when(gameRepository.findByMatch_Competition_Id(COMPETITION_ID)).thenReturn(List.of());

        StatsDto stats = statsService.compute(COMPETITION_ID, null);

        assertThat(stats.summary().games()).isEqualTo(2L);
        assertThat(stats.summary().kills()).isEqualTo(19L);
        assertThat(stats.summary().championsPicked()).isEqualTo(3L);
        assertThat(stats.summary().championsPickedOrBanned()).isEqualTo(4L);

        StatsDto.KdaStat capsStat = stats.kda().stream()
                .filter(k -> k.pseudo().equals("Caps"))
                .findFirst()
                .orElseThrow();
        assertThat(capsStat.wins()).isEqualTo(1L);
        assertThat(capsStat.losses()).isEqualTo(1L);
        assertThat(capsStat.perfectGames()).isEqualTo(1L);
        // (6 + 1) + (1 + 0) sur 8 + 1 kills de G2.
        assertThat(capsStat.killParticipation()).isEqualTo(8.0 / 9);
        assertThat(capsStat.topChampions())
                .extracting(StatsDto.ChampionPick::champion)
                .containsExactly("Azir");

        StatsDto.ChampionStat azir = stats.champions().stream()
                .filter(c -> c.champion().equals("Azir"))
                .findFirst()
                .orElseThrow();
        assertThat(azir.wins()).isEqualTo(1L);
        assertThat(azir.losses()).isEqualTo(2L);

        assertThat(stats.teams()).extracting(StatsDto.TeamStat::teamCode).containsExactly("FNC", "G2");
        StatsDto.TeamStat fncStat = stats.teams().get(0);
        assertThat(fncStat.games()).isEqualTo(2L);
        assertThat(fncStat.wins()).isEqualTo(1L);
        assertThat(fncStat.kills()).isEqualTo(10L);
        assertThat(fncStat.mostBanned()).isEqualTo(new StatsDto.ChampionPick("Zed", 2));

        assertThat(stats.records().mostKills().get(0).pseudo()).isEqualTo("Humanoid");
        assertThat(stats.records().mostAssists().get(0).pseudo()).isEqualTo("Jankos");
        assertThat(stats.records().bestKda().get(0).pseudo()).isEqualTo("Humanoid");
    }

    @Test
    void calculeEcartsDeKillsRecordsDEquipeEtBans() {
        // Manche 1 : G2 8 kills, FNC 1. Manche 2 : G2 1 kill, FNC 9.
        MatchGame game1 = game(g2);
        MatchGame game2 = game(fnc);
        when(gamePlayerRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(
                        inGame(game1, line(caps, g2, "Azir", 8, 0, 1)),
                        inGame(game1, line(humanoid, fnc, "Orianna", 1, 6, 0)),
                        inGame(game2, line(caps, g2, "Azir", 1, 3, 0)),
                        inGame(game2, line(humanoid, fnc, "Orianna", 9, 1, 3))));
        // FNC bannit Azir (pool de Caps) en manche 2, Zed en manche 1 ; G2 bannit Orianna.
        when(gameBanRepository.findByGame_Match_Competition_Id(COMPETITION_ID))
                .thenReturn(List.of(
                        inGame(game1, ban(fnc, "Zed")),
                        inGame(game2, ban(fnc, "Azir")),
                        inGame(game2, ban(g2, "Orianna"))));
        when(matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(COMPETITION_ID))
                .thenReturn(List.of());
        when(gameRepository.findByMatch_Competition_Id(COMPETITION_ID)).thenReturn(List.of());

        StatsDto stats = statsService.compute(COMPETITION_ID, null);

        StatsDto.TeamStat g2Stat = stats.teams().stream()
                .filter(t -> t.teamCode().equals("G2"))
                .findFirst()
                .orElseThrow();
        // (8 - 1) + (1 - 9) sur 2 manches
        assertThat(g2Stat.killDiffPerGame()).isEqualTo(-0.5);
        assertThat(g2Stat.championPool()).isEqualTo(1L);
        assertThat(g2Stat.mostBannedAgainst()).isEqualTo(new StatsDto.ChampionPick("Azir", 1));

        assertThat(stats.records().mostTeamKills().get(0).teamCode()).isEqualTo("FNC");
        assertThat(stats.records().mostTeamKills().get(0).kills()).isEqualTo(9L);
        assertThat(stats.records().biggestKillGap().get(0).teamCode()).isEqualTo("FNC");
        assertThat(stats.records().biggestKillGap().get(0).opponentCode()).isEqualTo("G2");
        assertThat(stats.records().mostDeaths().get(0).pseudo()).isEqualTo("Humanoid");

        StatsDto.TeamBans fncBans = stats.bans().teams().stream()
                .filter(t -> t.teamCode().equals("FNC"))
                .findFirst()
                .orElseThrow();
        assertThat(fncBans.games()).isEqualTo(2L);
        assertThat(fncBans.bansMade()).isEqualTo(2L);
        assertThat(fncBans.bansReceived()).isEqualTo(1L);
        assertThat(fncBans.topReceived()).containsExactly(new StatsDto.ChampionPick("Orianna", 1));

        // Caps est cible par le ban d'Azir ; Zed n'est pas dans son pool. Humanoid par Orianna.
        assertThat(stats.bans().players())
                .extracting(StatsDto.PlayerBans::pseudo, StatsDto.PlayerBans::targetedBans)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Caps", 1L),
                        org.assertj.core.groups.Tuple.tuple("Humanoid", 1L));
    }

    private static MatchGame game(Team winner) {
        MatchGame game = new MatchGame();
        game.setWinner(winner);
        game.setGameNumber(1);
        return game;
    }

    private static MatchGamePlayer inGame(MatchGame game, MatchGamePlayer line) {
        line.setGame(game);
        return line;
    }

    private static MatchGameBan inGame(MatchGame game, MatchGameBan ban) {
        ban.setGame(game);
        return ban;
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
