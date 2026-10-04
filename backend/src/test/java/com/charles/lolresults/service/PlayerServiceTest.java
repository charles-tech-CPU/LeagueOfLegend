package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchPhase;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.PlayerStint;
import com.charles.lolresults.domain.Position;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.FormerPlayerCreateDto;
import com.charles.lolresults.dto.PlayerCreateDto;
import com.charles.lolresults.dto.PlayerDto;
import com.charles.lolresults.dto.PlayerStintCreateDto;
import com.charles.lolresults.dto.PlayerStintDto;
import com.charles.lolresults.dto.PlayerTransferDto;
import com.charles.lolresults.dto.StintCompetitionDto;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.PlayerRepository;
import com.charles.lolresults.repository.PlayerStintRepository;
import com.charles.lolresults.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerStintRepository stintRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private PlayerService playerService;

    private Team g2;
    private Team fnc;
    private Player caps;
    private List<PlayerStint> stints;

    @BeforeEach
    void setUp() {
        g2 = team(1L, "G2");
        fnc = team(2L, "FNC");
        caps = new Player(g2, "Caps", "DK", Position.MID);
        caps.setId(5L);
        stints = new ArrayList<>();

        when(teamRepository.findById(1L)).thenReturn(Optional.of(g2));
        when(teamRepository.findById(2L)).thenReturn(Optional.of(fnc));
        when(teamRepository.findById(99L)).thenReturn(Optional.empty());
        when(playerRepository.findById(5L)).thenReturn(Optional.of(caps));
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stintRepository.save(any(PlayerStint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stintRepository.saveAndFlush(any(PlayerStint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stintRepository.findByPlayerId(5L)).thenReturn(stints);
        when(stintRepository.findByPlayerIdAndEndDateIsNull(5L))
                .thenAnswer(invocation ->
                        stints.stream().filter(PlayerStint::isCurrent).findFirst());
        when(matchRepository.findByTeam1_IdOrTeam2_IdOrderByDateDesc(any(), any()))
                .thenReturn(List.of());
    }

    // ---------- Joueurs ----------

    @Test
    void findAllTrieParPseudoEtGardeLesJoueursSansEquipe() {
        when(playerRepository.findAll()).thenReturn(List.of(caps, new Player(null, "agent", "FR", Position.TOP)));

        List<PlayerDto> players = playerService.findAll();

        assertThat(players).extracting(PlayerDto::pseudo).containsExactly("agent", "Caps");
        assertThat(players.get(0).teamId()).isNull();
        assertThat(players.get(1).teamCode()).isEqualTo("G2");
        assertThat(playerService.findOne(5L).pseudo()).isEqualTo("Caps");
    }

    @Test
    void findByTeamTrieParPostePuisParPseudo() {
        when(playerRepository.findByTeamId(1L))
                .thenReturn(List.of(
                        new Player(g2, "Mikyx", "SI", Position.SUPP),
                        caps,
                        new Player(g2, "BrokenBlade", "DE", Position.TOP),
                        new Player(g2, "aaa", null, Position.MID)));

        assertThat(playerService.findByTeam(1L))
                .extracting(PlayerDto::pseudo)
                .containsExactly("BrokenBlade", "aaa", "Caps", "Mikyx");
    }

    @Test
    void createOuvreUnPassageEnCoursDansLEquipe() {
        PlayerDto created = playerService.create(1L, new PlayerCreateDto("  Hans Sama ", "fr", Position.ADC));

        assertThat(created.pseudo()).isEqualTo("Hans Sama");
        assertThat(created.nationality()).isEqualTo("FR");
        assertThat(created.teamId()).isEqualTo(1L);
        ArgumentCaptor<PlayerStint> stint = ArgumentCaptor.forClass(PlayerStint.class);
        verify(stintRepository).save(stint.capture());
        assertThat(stint.getValue().getTeam()).isSameAs(g2);
        assertThat(stint.getValue().isCurrent()).isTrue();
        assertThat(stint.getValue().getStartDate()).isNull();
    }

    @Test
    void createWithoutTeamCreeUnJoueurSansPassage() {
        PlayerDto created = playerService.createWithoutTeam(new PlayerCreateDto(" xPeke ", "es", Position.MID));

        assertThat(created.pseudo()).isEqualTo("xPeke");
        assertThat(created.nationality()).isEqualTo("ES");
        assertThat(created.teamId()).isNull();
        verify(stintRepository, never()).save(any());
    }

    @Test
    void createFormerCreeUnJoueurSansEquipeAvecUnPassageTermine() {
        LocalDate start = LocalDate.of(2011, 3, 1);
        LocalDate end = LocalDate.of(2016, 11, 30);

        PlayerDto created =
                playerService.createFormer(2L, new FormerPlayerCreateDto("xPeke", "ES", Position.MID, start, end));

        assertThat(created.teamId()).isNull();
        ArgumentCaptor<PlayerStint> stint = ArgumentCaptor.forClass(PlayerStint.class);
        verify(stintRepository).save(stint.capture());
        assertThat(stint.getValue().getTeam()).isSameAs(fnc);
        assertThat(stint.getValue().getStartDate()).isEqualTo(start);
        assertThat(stint.getValue().getEndDate()).isEqualTo(end);
    }

    @Test
    void createFormerRefuseDesDatesIncoherentesOuUneEquipeInconnue() {
        FormerPlayerCreateDto inverted = new FormerPlayerCreateDto(
                "xPeke", null, Position.MID, LocalDate.of(2016, 1, 1), LocalDate.of(2011, 1, 1));
        FormerPlayerCreateDto valid =
                new FormerPlayerCreateDto("xPeke", null, Position.MID, null, LocalDate.of(2011, 1, 1));

        assertThatThrownBy(() -> playerService.createFormer(2L, inverted)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> playerService.createFormer(99L, valid)).isInstanceOf(EntityNotFoundException.class);
        verify(playerRepository, never()).save(any());
    }

    @Test
    void lEffectifDUneSaisonRegroupeLesJoueursPassesParLEquipePendantLAnnee() {
        Player xpeke = new Player(null, "xPeke", "ES", Position.MID);
        Player cyanide = new Player(null, "Cyanide", "FI", Position.JGL);
        Player rekkles = new Player(fnc, "Rekkles", "SE", Position.ADC);
        Player hylissang = new Player(fnc, "Hylissang", "BG", Position.SUPP);
        when(stintRepository.findByTeamId(2L))
                .thenReturn(List.of(
                        new PlayerStint(xpeke, fnc, LocalDate.of(2011, 3, 1), LocalDate.of(2016, 11, 30)),
                        new PlayerStint(cyanide, fnc, null, LocalDate.of(2013, 5, 1)),
                        // Deux passages du meme joueur dans la saison : une seule ligne.
                        new PlayerStint(rekkles, fnc, LocalDate.of(2014, 1, 1), LocalDate.of(2014, 6, 30)),
                        new PlayerStint(rekkles, fnc, LocalDate.of(2014, 9, 1), null),
                        // Arrivee inconnue d'un joueur actuel : seulement l'annee en cours.
                        new PlayerStint(hylissang, fnc, null, null)));

        assertThat(playerService.findByTeamAndSeason(2L, 2011))
                .extracting(PlayerDto::pseudo)
                .containsExactly("xPeke");
        assertThat(playerService.findByTeamAndSeason(2L, 2013))
                .extracting(PlayerDto::pseudo)
                .containsExactly("Cyanide", "xPeke");
        assertThat(playerService.findByTeamAndSeason(2L, 2014))
                .extracting(PlayerDto::pseudo)
                .containsExactly("xPeke", "Rekkles");
        assertThat(playerService.findByTeamAndSeason(2L, LocalDate.now().getYear()))
                .extracting(PlayerDto::pseudo)
                .containsExactly("Rekkles", "Hylissang");
        assertThatThrownBy(() -> playerService.findByTeamAndSeason(99L, 2011))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void lesSaisonsDEffectifCombinentMatchsEtPassagesDuPlusRecentAuPlusAncien() {
        Competition lec = competition(1L, "LEC", 2019);
        when(matchRepository.findByTeam1_IdOrTeam2_IdOrderByDateDesc(2L, 2L))
                .thenReturn(List.of(match(lec, LocalDate.of(2019, 6, 1), fnc, g2, 2, 1, null)));
        when(stintRepository.findByTeamId(2L))
                .thenReturn(List.of(new PlayerStint(caps, fnc, LocalDate.of(2011, 6, 1), LocalDate.of(2013, 1, 1))));

        assertThat(playerService.findRosterSeasons(2L)).containsExactly(2019, 2013, 2012, 2011);
    }

    @Test
    void updateModifieLIdentiteSansToucherALEquipe() {
        playerService.update(5L, new PlayerCreateDto("Caps", " ", Position.ADC));

        assertThat(caps.getNationality()).isNull();
        assertThat(caps.getPosition()).isEqualTo(Position.ADC);
        assertThat(caps.getTeam()).isSameAs(g2);
    }

    @Test
    void deleteSupprimeLeJoueur() {
        playerService.delete(5L);

        verify(playerRepository).deleteById(5L);
    }

    @Test
    void unJoueurUneEquipeOuUnPassageInconnuLeveUneErreur() {
        when(stintRepository.findById(99L)).thenReturn(Optional.empty());
        PlayerStintCreateDto stintDto = new PlayerStintCreateDto(1L, null, LocalDate.of(2024, 1, 1));

        assertThatThrownBy(() -> playerService.findOne(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Joueur introuvable : 99");
        assertThatThrownBy(() -> playerService.findByTeam(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Equipe introuvable : 99");
        assertThatThrownBy(() -> playerService.findStints(99L)).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> playerService.updateStint(99L, stintDto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Passage introuvable : 99");
    }

    // ---------- Transferts ----------

    @Test
    void transfertTermineLePassageEnCoursLaVeilleEtOuvreLeSuivant() {
        PlayerStint atG2 = stint(10L, g2, LocalDate.of(2024, 1, 1), null);

        PlayerDto moved = playerService.transfer(5L, new PlayerTransferDto(2L, LocalDate.of(2025, 12, 1)));

        assertThat(atG2.getEndDate()).isEqualTo(LocalDate.of(2025, 11, 30));
        verify(stintRepository).saveAndFlush(atG2);
        ArgumentCaptor<PlayerStint> created = ArgumentCaptor.forClass(PlayerStint.class);
        verify(stintRepository).save(created.capture());
        assertThat(created.getValue().getTeam()).isSameAs(fnc);
        assertThat(created.getValue().getStartDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(created.getValue().isCurrent()).isTrue();
        assertThat(moved.teamCode()).isEqualTo("FNC");
    }

    @Test
    void transfertSansEquipeLaisseLeJoueurSansEquipe() {
        PlayerStint atG2 = stint(10L, g2, null, null);

        PlayerDto left = playerService.transfer(5L, new PlayerTransferDto(null, LocalDate.of(2025, 12, 1)));

        assertThat(atG2.getEndDate()).isEqualTo(LocalDate.of(2025, 11, 30));
        assertThat(left.teamId()).isNull();
        verify(stintRepository, never()).save(any(PlayerStint.class));
    }

    @Test
    void unJoueurSansEquipePeutEnRejoindreUne() {
        caps.setTeam(null);
        stint(10L, g2, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 11, 30));

        PlayerDto signed = playerService.transfer(5L, new PlayerTransferDto(2L, LocalDate.of(2026, 1, 5)));

        assertThat(signed.teamCode()).isEqualTo("FNC");
        verify(stintRepository, never()).saveAndFlush(any(PlayerStint.class));
    }

    @Test
    void transfertsIncoherentsRefuses() {
        stint(10L, g2, LocalDate.of(2025, 6, 1), null);
        PlayerTransferDto sameTeam = new PlayerTransferDto(1L, LocalDate.of(2026, 1, 1));
        PlayerTransferDto beforeArrival = new PlayerTransferDto(2L, LocalDate.of(2025, 6, 1));

        assertThatThrownBy(() -> playerService.transfer(5L, sameTeam))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le joueur fait deja partie de G2 au poste MID");
        assertThatThrownBy(() -> playerService.transfer(5L, beforeArrival))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("La date du transfert doit etre posterieure a l'arrivee chez G2");
    }

    @Test
    void unRoleSwapOuvreUnNouveauPassageDansLaMemeEquipe() {
        PlayerStint midAtG2 = stint(10L, g2, LocalDate.of(2025, 1, 1), null);

        PlayerDto swapped =
                playerService.transfer(5L, new PlayerTransferDto(1L, LocalDate.of(2025, 7, 1), Position.ADC));

        assertThat(midAtG2.getEndDate()).isEqualTo(LocalDate.of(2025, 6, 30));
        assertThat(midAtG2.getPosition()).isEqualTo(Position.MID);
        ArgumentCaptor<PlayerStint> created = ArgumentCaptor.forClass(PlayerStint.class);
        verify(stintRepository).save(created.capture());
        assertThat(created.getValue().getTeam()).isSameAs(g2);
        assertThat(created.getValue().getPosition()).isEqualTo(Position.ADC);
        assertThat(created.getValue().getStartDate()).isEqualTo(LocalDate.of(2025, 7, 1));
        assertThat(swapped.position()).isEqualTo(Position.ADC);
        assertThat(swapped.teamCode()).isEqualTo("G2");
    }

    @Test
    void lEffectifDUneSaisonAfficheLeJoueurAChaquePosteOccupe() {
        when(stintRepository.findByTeamId(1L))
                .thenReturn(List.of(
                        new PlayerStint(caps, g2, Position.MID, LocalDate.of(2015, 1, 1), LocalDate.of(2015, 6, 30)),
                        new PlayerStint(caps, g2, Position.ADC, LocalDate.of(2015, 7, 1), null)));

        assertThat(playerService.findByTeamAndSeason(1L, 2015))
                .extracting(PlayerDto::position)
                .containsExactly(Position.MID, Position.ADC);
        assertThat(playerService.findByTeamAndSeason(1L, 2016))
                .extracting(PlayerDto::position)
                .containsExactly(Position.ADC);
    }

    @Test
    void modifierLePosteDuJoueurCorrigeLePassageEnCours() {
        PlayerStint current = stint(10L, g2, null, null);

        playerService.update(5L, new PlayerCreateDto("Caps", "DK", Position.ADC));

        assertThat(current.getPosition()).isEqualTo(Position.ADC);
    }

    @Test
    void corrigerLePosteDuPassageEnCoursMetAJourLeJoueur() {
        stint(10L, g2, null, null);
        when(stintRepository.findById(10L)).thenReturn(Optional.of(stints.get(0)));

        PlayerStintDto updated =
                playerService.updateStint(10L, new PlayerStintCreateDto(1L, Position.SUPP, null, null));

        assertThat(updated.position()).isEqualTo(Position.SUPP);
        assertThat(caps.getPosition()).isEqualTo(Position.SUPP);
    }

    @Test
    void unJoueurDejaSansEquipeNePeutPasLaQuitter() {
        caps.setTeam(null);
        PlayerTransferDto leave = new PlayerTransferDto(null, LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> playerService.transfer(5L, leave))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Le joueur est deja sans equipe");
    }

    @Test
    void unTransfertNePeutPasChevaucherUnPassageTermine() {
        caps.setTeam(null);
        stint(10L, g2, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 11, 30));
        PlayerTransferDto tooEarly = new PlayerTransferDto(2L, LocalDate.of(2025, 6, 1));

        assertThatThrownBy(() -> playerService.transfer(5L, tooEarly))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cette periode chevauche le passage chez G2 (01/01/2024 -> 30/11/2025)");
    }

    // ---------- Historique ----------

    @Test
    void lesResultatsDUnPassageSontDeduitsDesMatchsDeLEquipePendantLaPeriode() {
        stint(10L, g2, LocalDate.of(2026, 1, 1), null);
        Competition lec = competition(7L, "LEC", 2026);
        Competition msi = competition(8L, "MSI", 2026);
        when(matchRepository.findByTeam1_IdOrTeam2_IdOrderByDateDesc(1L, 1L))
                .thenReturn(List.of(
                        match(lec, LocalDate.of(2026, 9, 1), g2, fnc, 3, 1, "GRAND_FINAL"),
                        match(lec, LocalDate.of(2026, 3, 1), fnc, g2, 2, 0, null),
                        match(msi, LocalDate.of(2026, 5, 1), fnc, g2, 1, 2, "UPPER"),
                        scheduled(lec, LocalDate.of(2026, 10, 1)),
                        match(lec, LocalDate.of(2025, 9, 1), g2, fnc, 3, 0, "GRAND_FINAL")));

        PlayerStintDto result = playerService.findStints(5L).get(0);

        assertThat(result.current()).isTrue();
        assertThat(result.wins()).isEqualTo(2);
        assertThat(result.losses()).isEqualTo(1);
        assertThat(result.gamesWon()).isEqualTo(5);
        assertThat(result.gamesLost()).isEqualTo(4);
        assertThat(result.titles()).isEqualTo(1);
        assertThat(result.matches()).hasSize(3);
        assertThat(result.competitions())
                .extracting(StintCompetitionDto::code, StintCompetitionDto::wins, StintCompetitionDto::losses)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("LEC", 1, 1),
                        org.assertj.core.groups.Tuple.tuple("MSI", 1, 0));
        assertThat(result.competitions().get(0).champion()).isTrue();
        assertThat(result.competitions().get(1).champion()).isFalse();
    }

    @Test
    void lHistoriqueCommenceParLePassageEnCoursPuisDuPlusRecentAuPlusAncien() {
        stint(10L, g2, LocalDate.of(2022, 1, 1), LocalDate.of(2023, 11, 30));
        stint(11L, fnc, LocalDate.of(2026, 1, 1), null);
        stint(12L, g2, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 11, 30));

        assertThat(playerService.findStints(5L)).extracting(PlayerStintDto::id).containsExactly(11L, 12L, 10L);
    }

    @Test
    void ajouterUnPassageTermineDansLHistorique() {
        stint(10L, fnc, LocalDate.of(2026, 1, 1), null);

        PlayerStintDto added = playerService.addStint(
                5L, new PlayerStintCreateDto(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 11, 30)));

        assertThat(added.teamCode()).isEqualTo("G2");
        assertThat(added.current()).isFalse();
    }

    @Test
    void unPassageAjouteDoitEtreTermineCoherentEtNePasChevaucher() {
        stint(10L, fnc, null, null);
        PlayerStintCreateDto open = new PlayerStintCreateDto(1L, LocalDate.of(2024, 1, 1), null);
        PlayerStintCreateDto reversed =
                new PlayerStintCreateDto(1L, LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1));
        PlayerStintCreateDto overlapping =
                new PlayerStintCreateDto(1L, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        assertThatThrownBy(() -> playerService.addStint(5L, open))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Un passage ajoute doit avoir une date de fin");
        assertThatThrownBy(() -> playerService.addStint(5L, reversed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La date de fin doit etre posterieure a la date d'arrivee");
        // Le passage en cours a une arrivee inconnue : il couvre tout le passe.
        assertThatThrownBy(() -> playerService.addStint(5L, overlapping))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cette periode chevauche le passage chez FNC (arrivee inconnue -> aujourd'hui)"
                        + " : renseigne d'abord sa date d'arrivee");
    }

    @Test
    void corrigerLaDateDArriveeDuPassageEnCours() {
        PlayerStint current = stint(10L, g2, null, null);
        when(stintRepository.findById(10L)).thenReturn(Optional.of(current));

        PlayerStintDto updated =
                playerService.updateStint(10L, new PlayerStintCreateDto(1L, LocalDate.of(2025, 12, 1), null));

        assertThat(updated.startDate()).isEqualTo(LocalDate.of(2025, 12, 1));
        assertThat(current.getStartDate()).isEqualTo(LocalDate.of(2025, 12, 1));
    }

    @Test
    void corrigerUnPassageTermine() {
        PlayerStint past = stint(10L, g2, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));
        when(stintRepository.findById(10L)).thenReturn(Optional.of(past));

        playerService.updateStint(
                10L, new PlayerStintCreateDto(2L, LocalDate.of(2022, 1, 1), LocalDate.of(2023, 12, 31)));

        assertThat(past.getTeam()).isSameAs(fnc);
        assertThat(past.getStartDate()).isEqualTo(LocalDate.of(2022, 1, 1));
    }

    @Test
    void lePassageEnCoursSeTermineOuChangeDEquipeUniquementParTransfert() {
        PlayerStint current = stint(10L, g2, null, null);
        PlayerStint past = stint(11L, fnc, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));
        when(stintRepository.findById(10L)).thenReturn(Optional.of(current));
        when(stintRepository.findById(11L)).thenReturn(Optional.of(past));
        PlayerStintCreateDto closing = new PlayerStintCreateDto(1L, null, LocalDate.of(2025, 1, 1));
        PlayerStintCreateDto otherTeam = new PlayerStintCreateDto(2L, null, null);
        PlayerStintCreateDto reopening = new PlayerStintCreateDto(2L, LocalDate.of(2023, 1, 1), null);

        assertThatThrownBy(() -> playerService.updateStint(10L, closing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pour terminer le passage en cours, utilise le transfert");
        assertThatThrownBy(() -> playerService.updateStint(10L, otherTeam))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Pour changer l'equipe actuelle, utilise le transfert");
        assertThatThrownBy(() -> playerService.updateStint(11L, reopening))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Un passage termine doit avoir une date de fin");
    }

    @Test
    void supprimerLePassageEnCoursLaisseLeJoueurSansEquipe() {
        PlayerStint current = stint(10L, g2, null, null);
        when(stintRepository.findById(10L)).thenReturn(Optional.of(current));

        playerService.deleteStint(10L);

        assertThat(caps.getTeam()).isNull();
        verify(stintRepository).delete(current);
    }

    @Test
    void supprimerUnPassageTermineNeChangePasLEquipeActuelle() {
        PlayerStint past = stint(10L, fnc, LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));
        when(stintRepository.findById(10L)).thenReturn(Optional.of(past));

        playerService.deleteStint(10L);

        assertThat(caps.getTeam()).isSameAs(g2);
        verify(stintRepository).delete(past);
    }

    // ---------- Jeux de donnees ----------

    private PlayerStint stint(Long id, Team team, LocalDate start, LocalDate end) {
        PlayerStint stint = new PlayerStint(caps, team, start, end);
        stint.setId(id);
        stints.add(stint);
        return stint;
    }

    private static Team team(Long id, String code) {
        Team team = new Team(code, code + " Esports", "EMEA");
        team.setId(id);
        return team;
    }

    private static Competition competition(Long id, String code, int season) {
        Competition competition =
                new Competition(code, code + " " + season, CompetitionType.REGIONAL_LEAGUE, null, season);
        competition.setId(id);
        return competition;
    }

    private static Match match(
            Competition competition, LocalDate date, Team team1, Team team2, int score1, int score2, String side) {
        Match match = scheduled(competition, date);
        match.setTeam1(team1);
        match.setTeam2(team2);
        match.setScore1(score1);
        match.setScore2(score2);
        match.setStatus(MatchStatus.COMPLETED);
        match.setBracketSide(side);
        match.setPhase(side != null ? MatchPhase.PLAYOFFS : MatchPhase.REGULAR_SEASON);
        return match;
    }

    private static Match scheduled(Competition competition, LocalDate date) {
        Match match = new Match();
        match.setCompetition(competition);
        match.setDate(date);
        match.setRoundLabel("W1");
        match.setBestOf(BestOf.BO3);
        return match;
    }
}
