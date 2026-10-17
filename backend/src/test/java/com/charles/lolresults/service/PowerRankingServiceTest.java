package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.PowerRankingRowDto;
import com.charles.lolresults.repository.MatchRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PowerRankingServiceTest {

    private static final int SEASON = 2026;
    private static final LocalDate DAY = LocalDate.of(2026, 6, 1);

    @Mock
    private MatchRepository matchRepository;

    @InjectMocks
    private PowerRankingService powerRankingService;

    private Team g2;
    private Team fnc;
    private Team kc;
    private Team vit;
    private Competition league;
    private Competition worlds;
    private long nextId = 1;

    @BeforeEach
    void setUp() {
        g2 = team(1L, "G2");
        fnc = team(2L, "FNC");
        kc = team(3L, "KC");
        vit = team(4L, "VIT");
        league = competition(CompetitionType.REGIONAL_LEAGUE, "LEC");
        worlds = competition(CompetitionType.INTERNATIONAL_EVENT, "WORLDS");
    }

    @Test
    void sansMatchJoueLeClassementEstVide() {
        when(matchRepository.findByCompetition_Season(SEASON)).thenReturn(List.of());

        assertThat(powerRankingService.compute(SEASON)).isEmpty();
    }

    @Test
    void ignoreLesMatchsNonJouesOuSansEquipes() {
        Match scheduled = match(g2, fnc, null, null, DAY, BestOf.BO1, league);
        scheduled.setStatus(MatchStatus.SCHEDULED);
        Match tbd = match(null, fnc, 1, 0, DAY, BestOf.BO1, league);
        givenMatches(scheduled, tbd, match(g2, kc, 1, 0, DAY, BestOf.BO1, league));

        assertThat(powerRankingService.compute(SEASON))
                .extracting(PowerRankingRowDto::teamCode)
                .containsExactly("G2", "KC");
    }

    @Test
    void battreUneEquipeForteRapportePlusQueBattreUneEquipeFaible() {
        // G2 enchaine les victoires (forte), VIT les defaites (faible).
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            matches.add(match(g2, vit, 1, 0, DAY.plusDays(i), BestOf.BO1, league));
        }
        // Puis FNC bat G2 et KC bat VIT, le meme jour, au meme format.
        matches.add(match(fnc, g2, 1, 0, DAY.plusDays(10), BestOf.BO1, league));
        matches.add(match(kc, vit, 1, 0, DAY.plusDays(10), BestOf.BO1, league));
        givenMatches(matches.toArray(Match[]::new));

        List<PowerRankingRowDto> ranking = powerRankingService.compute(SEASON);

        PowerRankingRowDto fncRow = row(ranking, "FNC");
        PowerRankingRowDto kcRow = row(ranking, "KC");
        assertThat(fncRow.rating()).isGreaterThan(kcRow.rating());
        assertThat(fncRow.rank()).isLessThan(kcRow.rank());
        assertThat(fncRow.bestWin().opponentCode()).isEqualTo("G2");
    }

    @Test
    void unBo5NetEtInternationalPeseDavantageQuUnBo1() {
        double bo1 = PowerRankingService.k(match(g2, fnc, 1, 0, DAY, BestOf.BO1, league), 1);
        double bo5Close = PowerRankingService.k(match(g2, fnc, 3, 2, DAY, BestOf.BO5, league), 1);
        double bo5Sweep = PowerRankingService.k(match(g2, fnc, 3, 0, DAY, BestOf.BO5, league), 3);
        double bo5Worlds = PowerRankingService.k(match(g2, fnc, 3, 0, DAY, BestOf.BO5, worlds), 3);

        assertThat(bo1).isLessThan(bo5Close);
        assertThat(bo5Close).isLessThan(bo5Sweep);
        assertThat(bo5Sweep).isLessThan(bo5Worlds);
    }

    @Test
    void bilanFormeEtEvolutionSurLesSeptDerniersJours() {
        givenMatches(
                match(g2, fnc, 2, 1, DAY, BestOf.BO3, league),
                match(kc, g2, 2, 0, DAY.plusDays(1), BestOf.BO3, league),
                // Plus de 7 jours apres : seule cette serie compte dans l'evolution recente.
                match(fnc, kc, 2, 0, DAY.plusDays(20), BestOf.BO3, league));

        List<PowerRankingRowDto> ranking = powerRankingService.compute(SEASON);

        PowerRankingRowDto g2Row = row(ranking, "G2");
        assertThat(g2Row.seriesWon()).isEqualTo(1);
        assertThat(g2Row.seriesLost()).isEqualTo(1);
        assertThat(g2Row.gamesWon()).isEqualTo(2);
        assertThat(g2Row.gamesLost()).isEqualTo(3);
        assertThat(g2Row.form()).containsExactly("W", "L");
        assertThat(g2Row.ratingChange()).isZero();

        PowerRankingRowDto fncRow = row(ranking, "FNC");
        assertThat(fncRow.ratingChange()).isPositive();
        // FNC etait derniere il y a 7 jours, elle a gagne des places depuis.
        assertThat(fncRow.rankChange()).isPositive();
        assertThat(fncRow.lastPlayed()).isEqualTo(DAY.plusDays(20));
    }

    @Test
    void laFormeSeLimiteAuxCinqDernieresSeries() {
        List<Match> matches = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            matches.add(
                    i < 2
                            ? match(fnc, g2, 1, 0, DAY.plusDays(i), BestOf.BO1, league)
                            : match(g2, fnc, 1, 0, DAY.plusDays(i), BestOf.BO1, league));
        }
        givenMatches(matches.toArray(Match[]::new));

        assertThat(row(powerRankingService.compute(SEASON), "G2").form()).containsExactly("W", "W", "W", "W", "W");
    }

    @Test
    void unEvenementInternationalMineurNePesePasPlusQuUneLigue() {
        Competition emeaMasters = competition(CompetitionType.INTERNATIONAL_EVENT, "EM");

        assertThat(PowerRankingService.k(match(g2, fnc, 3, 0, DAY, BestOf.BO5, emeaMasters), 3))
                .isEqualTo(PowerRankingService.k(match(g2, fnc, 3, 0, DAY, BestOf.BO5, league), 3));
    }

    @Test
    void uneEquipeDeDeuxiemeDivisionPartPlusBas() {
        // VIT ne joue qu'aux EMEA Masters : invaincue, elle reste derriere G2 qui a gagne en ligue.
        Competition emeaMasters = competition(CompetitionType.INTERNATIONAL_EVENT, "EM");
        givenMatches(
                match(g2, fnc, 1, 0, DAY, BestOf.BO1, league),
                match(vit, kc, 1, 0, DAY.plusDays(1), BestOf.BO1, emeaMasters));

        List<PowerRankingRowDto> ranking = powerRankingService.compute(SEASON);

        assertThat(row(ranking, "G2").startRating()).isEqualTo(1500);
        assertThat(row(ranking, "VIT").startRating()).isEqualTo(PowerRankingService.SECOND_TIER_RATING);
        assertThat(row(ranking, "VIT").league()).isNull();
        assertThat(row(ranking, "G2").rank()).isEqualTo(1);
    }

    @Test
    void coefficientDeLigueLaCoreePartPlusHautQueLeJapon() {
        Competition lck = competition(CompetitionType.REGIONAL_LEAGUE, "LCK");
        Competition ljl = competition(CompetitionType.REGIONAL_LEAGUE, "LJL");
        // Meme parcours (une victoire en ligue) : seule la ligue les departage.
        givenMatches(match(g2, fnc, 1, 0, DAY, BestOf.BO1, lck), match(kc, vit, 1, 0, DAY, BestOf.BO1, ljl));

        List<PowerRankingRowDto> ranking = powerRankingService.compute(SEASON);

        assertThat(row(ranking, "G2").league()).isEqualTo("LCK");
        assertThat(row(ranking, "G2").startRating()).isEqualTo(1600);
        assertThat(row(ranking, "KC").startRating()).isEqualTo(1350);
        assertThat(row(ranking, "G2").rating()).isGreaterThan(row(ranking, "KC").rating());
        // La derniere equipe coreenne reste devant le vainqueur japonais.
        assertThat(row(ranking, "FNC").rank()).isLessThan(row(ranking, "KC").rank());
    }

    @Test
    void laLigueEstCelleDeLaPlupartDesSeriesHorsRegionalFinals() {
        Competition lck = competition(CompetitionType.REGIONAL_LEAGUE, "LCK");
        Competition regionalFinals = competition(CompetitionType.REGIONAL_LEAGUE, "RF");
        givenMatches(
                match(g2, fnc, 1, 0, DAY, BestOf.BO1, lck),
                match(g2, kc, 1, 0, DAY.plusDays(1), BestOf.BO1, regionalFinals),
                match(g2, kc, 1, 0, DAY.plusDays(2), BestOf.BO1, regionalFinals));

        List<PowerRankingRowDto> ranking = powerRankingService.compute(SEASON);

        assertThat(row(ranking, "G2").league()).isEqualTo("LCK");
        // KC n'a joue que les Regional Finals : pas de ligue connue.
        assertThat(row(ranking, "KC").league()).isNull();
    }

    @Test
    void uneLigueInconnueALaNoteParDefaut() {
        assertThat(PowerRankingService.startRating("XYZ")).isEqualTo(PowerRankingService.DEFAULT_LEAGUE_RATING);
        assertThat(PowerRankingService.startRating("LPL")).isEqualTo(1600);
        assertThat(PowerRankingService.startRating("EU LCS")).isEqualTo(1500);
    }

    private void givenMatches(Match... matches) {
        when(matchRepository.findByCompetition_Season(SEASON)).thenReturn(List.of(matches));
    }

    private static PowerRankingRowDto row(List<PowerRankingRowDto> ranking, String code) {
        return ranking.stream()
                .filter(r -> r.teamCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private Match match(Team t1, Team t2, Integer s1, Integer s2, LocalDate date, BestOf bestOf, Competition c) {
        Match m = new Match();
        m.setId(nextId++);
        m.setTeam1(t1);
        m.setTeam2(t2);
        m.setScore1(s1);
        m.setScore2(s2);
        m.setDate(date);
        m.setBestOf(bestOf);
        m.setCompetition(c);
        m.setStatus(s1 != null ? MatchStatus.COMPLETED : MatchStatus.SCHEDULED);
        return m;
    }

    private static Competition competition(CompetitionType type, String code) {
        Competition c = new Competition();
        c.setType(type);
        c.setCode(code);
        c.setSeason(SEASON);
        return c;
    }

    private static Team team(Long id, String code) {
        Team t = new Team();
        t.setId(id);
        t.setCode(code);
        t.setName(code);
        return t;
    }
}
