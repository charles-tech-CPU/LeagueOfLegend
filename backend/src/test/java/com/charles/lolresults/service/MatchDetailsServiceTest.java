package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.Position;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.MatchDetailsDto;
import com.charles.lolresults.dto.MatchDetailsUpdateDto;
import com.charles.lolresults.dto.MatchDetailsUpdateDto.Ban;
import com.charles.lolresults.dto.MatchDetailsUpdateDto.Game;
import com.charles.lolresults.dto.MatchDetailsUpdateDto.Line;
import com.charles.lolresults.repository.MatchGameRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.PlayerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchDetailsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private MatchGameRepository gameRepository;

    @Mock
    private PlayerRepository playerRepository;

    @InjectMocks
    private MatchDetailsService service;

    private Team g2;
    private Team fnc;
    private Match match;

    @BeforeEach
    void setUp() {
        g2 = team(1L, "G2");
        fnc = team(2L, "FNC");
        match = new Match();
        match.setId(5L);
        match.setBestOf(BestOf.BO3);
        match.setTeam1(g2);
        match.setTeam2(fnc);

        when(matchRepository.findById(5L)).thenReturn(Optional.of(match));
        when(gameRepository.findByMatchIdOrderByGameNumber(5L)).thenReturn(List.of());
        when(gameRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(playerRepository.findById(any())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.of(player(id, "P" + id));
        });
    }

    @Test
    void unMatchSansDetailsRenvoieDesManchesVides() {
        MatchDetailsDto dto = service.find(5L);

        assertThat(dto.games()).isEmpty();
        assertThat(dto.mvpPlayerId()).isNull();
    }

    @Test
    void enregistreChampionsKdaEtMvp() {
        Game game1 = new Game(
                1,
                1L,
                10L,
                List.of(
                        new Line(10L, 1L, Position.MID, " Ahri ", 4, 1, 6),
                        new Line(20L, 2L, Position.MID, "Orianna", null, null, null)),
                List.of(new Ban(1L, " Zed "), new Ban(2L, "Yone")));

        MatchDetailsDto dto = service.replace(5L, new MatchDetailsUpdateDto(10L, List.of(game1)));

        assertThat(dto.mvpPlayerId()).isEqualTo(10L);
        assertThat(match.getMvp().getId()).isEqualTo(10L);
        assertThat(dto.games()).hasSize(1);
        MatchDetailsDto.Game saved = dto.games().get(0);
        assertThat(saved.winnerTeamId()).isEqualTo(1L);
        assertThat(saved.mvpPseudo()).isEqualTo("P10");
        assertThat(saved.players()).extracting(MatchDetailsDto.Line::champion).containsExactly("Ahri", "Orianna");
        assertThat(saved.players().get(0).kills()).isEqualTo(4);
        assertThat(saved.players().get(1).kills()).isNull();
        assertThat(saved.bans()).extracting(MatchDetailsDto.Ban::champion).containsExactly("Zed", "Yone");
        assertThat(saved.bans().get(0).teamId()).isEqualTo(1L);
    }

    @Test
    void refuseUnBanChoisiDeuxFoisParLaMemeEquipe() {
        Game game = new Game(1, null, null, List.of(), List.of(new Ban(1L, "Zed"), new Ban(1L, "zed")));

        assertThatThrownBy(() -> service.replace(5L, new MatchDetailsUpdateDto(null, List.of(game))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("banni deux fois");
    }

    @Test
    void uneMancheAvecSeulementDesBansEstConsidereeRemplie() {
        Game game = new Game(1, null, null, List.of(), List.of(new Ban(1L, "Zed")));

        MatchDetailsDto dto = service.replace(5L, new MatchDetailsUpdateDto(null, List.of(game)));

        assertThat(dto.games()).hasSize(1);
    }

    @Test
    void lesManchesVidesSontIgnoreesEtLesAnciennesRemplacees() {
        MatchGame old = new MatchGame(match, 1);
        when(gameRepository.findByMatchIdOrderByGameNumber(5L)).thenReturn(List.of(old));

        MatchDetailsDto dto = service.replace(
                5L, new MatchDetailsUpdateDto(null, List.of(new Game(2, null, null, List.of(), List.of()))));

        assertThat(dto.games()).isEmpty();
        verify(gameRepository).deleteAll(List.of(old));
    }

    @Test
    void refuseUneMancheImpossibleDansLeFormat() {
        Game game4 = new Game(4, 1L, null, null, null);

        assertThatThrownBy(() -> service.replace(5L, new MatchDetailsUpdateDto(null, List.of(game4))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BO3");
    }

    @Test
    void refuseUnChampionChoisiDeuxFois() {
        Game game = new Game(
                1,
                null,
                null,
                List.of(
                        new Line(10L, 1L, Position.MID, "Ahri", null, null, null),
                        new Line(20L, 2L, Position.MID, "ahri", null, null, null)),
                List.of());

        assertThatThrownBy(() -> service.replace(5L, new MatchDetailsUpdateDto(null, List.of(game))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choisi deux fois");
        verify(gameRepository, never()).saveAll(anyList());
    }

    @Test
    void refuseDeuxJoueursAuMemePosteDansUneEquipe() {
        Game game = new Game(
                1,
                null,
                null,
                List.of(
                        new Line(10L, 1L, Position.MID, "Ahri", null, null, null),
                        new Line(11L, 1L, Position.MID, "Lux", null, null, null)),
                List.of());

        assertThatThrownBy(() -> service.replace(5L, new MatchDetailsUpdateDto(null, List.of(game))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au poste MID");
    }

    @Test
    void refuseUneEquipeQuiNeJouePasLeMatch() {
        Game game = new Game(1, 3L, null, null, null);

        assertThatThrownBy(() -> service.replace(5L, new MatchDetailsUpdateDto(null, List.of(game))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ne joue pas ce match");
    }

    @Test
    void leMvpDoitAvoirJoueLaManche() {
        Game game =
                new Game(1, null, 99L, List.of(new Line(10L, 1L, Position.MID, "Ahri", null, null, null)), List.of());

        assertThatThrownBy(() -> service.replace(5L, new MatchDetailsUpdateDto(null, List.of(game))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MVP");
    }

    @Test
    void refuseDesDetailsTantQueLesEquipesSontInconnues() {
        match.setTeam2(null);

        assertThatThrownBy(() -> service.replace(5L, new MatchDetailsUpdateDto(10L, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("equipes");
    }

    @Test
    void unBo2SeJoueEnDeuxManchesEtUnBo5EnCinq() {
        assertThat(BestOf.BO1.getMaxGames()).isEqualTo(1);
        assertThat(BestOf.BO2.getMaxGames()).isEqualTo(2);
        assertThat(BestOf.BO3.getMaxGames()).isEqualTo(3);
        assertThat(BestOf.BO5.getMaxGames()).isEqualTo(5);
    }

    private static Team team(Long id, String code) {
        Team team = new Team();
        team.setId(id);
        team.setCode(code);
        return team;
    }

    private static Player player(Long id, String pseudo) {
        Player player = new Player(null, pseudo, null, Position.MID);
        player.setId(id);
        return player;
    }
}
