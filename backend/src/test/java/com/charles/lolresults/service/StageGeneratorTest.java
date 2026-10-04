package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionStage;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchPhase;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.StageFormat;
import com.charles.lolresults.domain.Team;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StageGeneratorTest {

    private static final LocalDate START = LocalDate.of(2013, 2, 7);

    private final Competition competition =
            new Competition("LCS", "EU LCS 2013 Spring", CompetitionType.REGIONAL_LEAGUE, "EMEA", 2013);

    // ---------- Poules ----------

    @Test
    void unAllerRetourAHuitEquipesFaitJouerChaquePaireDeuxFoisDansLesDeuxSens() {
        List<Team> teams = teams(8);

        StageGenerator.Plan plan = StageGenerator.roundRobin(
                stage(StageFormat.DOUBLE_ROUND_ROBIN, BestOf.BO1), List.of(teams), true, START, 7);

        List<Match> matches = plan.matches();
        assertThat(plan.groups()).isEmpty();
        assertThat(matches).hasSize(56);
        Map<String, Integer> directed = new HashMap<>();
        matches.forEach(
                m -> directed.merge(m.getTeam1().getId() + ">" + m.getTeam2().getId(), 1, Integer::sum));
        assertThat(directed)
                .hasSize(56)
                .allSatisfy((pair, count) -> assertThat(count).isEqualTo(1));
        assertThat(matches).allSatisfy(m -> {
            assertThat(m.getPhase()).isEqualTo(MatchPhase.REGULAR_SEASON);
            assertThat(m.getBestOf()).isEqualTo(BestOf.BO1);
            assertThat(m.getStatus()).isEqualTo(MatchStatus.SCHEDULED);
        });
        assertThat(matches.get(0).getRoundLabel()).isEqualTo("J1");
        assertThat(matches.get(55).getRoundLabel()).isEqualTo("J14");
        assertThat(matches.get(55).getDate()).isEqualTo(START.plusWeeks(13));
    }

    @Test
    void chaqueEquipeJoueAuPlusUneFoisParJourneeMemeAvecUnNombreImpair() {
        List<Team> teams = teams(5);

        List<Match> matches = StageGenerator.roundRobin(
                        stage(StageFormat.ROUND_ROBIN, BestOf.BO1), List.of(teams), false, START, 1)
                .matches();

        assertThat(matches).hasSize(10);
        Map<String, List<Match>> byRound = new HashMap<>();
        matches.forEach(m -> byRound.computeIfAbsent(m.getRoundLabel(), k -> new ArrayList<>())
                .add(m));
        assertThat(byRound).hasSize(5);
        byRound.values().forEach(round -> {
            Set<Long> seen = new HashSet<>();
            round.forEach(m -> {
                assertThat(seen.add(m.getTeam1().getId())).isTrue();
                assertThat(seen.add(m.getTeam2().getId())).isTrue();
            });
        });
    }

    @Test
    void plusieursPoulesCreentUnGroupeParPoule() {
        List<Team> teams = teams(8);

        StageGenerator.Plan plan = StageGenerator.roundRobin(
                stage(StageFormat.ROUND_ROBIN, BestOf.BO1),
                List.of(teams.subList(0, 4), teams.subList(4, 8)),
                false,
                START,
                1);

        assertThat(plan.groups()).extracting(g -> g.getName()).containsExactly("Groupe A", "Groupe B");
        assertThat(plan.matches()).hasSize(12);
        assertThat(plan.matches().stream()
                        .filter(m -> m.getGroup() == plan.groups().get(1)))
                .hasSize(6)
                .allSatisfy(m -> assertThat(m.getTeam1().getId()).isGreaterThan(4L));
    }

    // ---------- Elimination simple ----------

    @Test
    void unBracketAHuitCroiseLesTetesDeSerieEtMeneALaFinale() {
        List<Team> seeds = teams(8);

        List<Match> matches = StageGenerator.singleElimination(
                stage(StageFormat.SINGLE_ELIMINATION, BestOf.BO3), seeds, START, 1, BestOf.BO5);

        assertThat(matches).hasSize(7);
        assertThat(matches.subList(0, 4))
                .extracting(m -> m.getTeam1().getId() + "-" + m.getTeam2().getId())
                .containsExactly("1-8", "4-5", "2-7", "3-6");
        Match finale = matches.get(6);
        assertThat(finale.getRoundLabel()).isEqualTo("Finale");
        assertThat(finale.getBracketSide()).isEqualTo("GRAND_FINAL");
        assertThat(finale.getBestOf()).isEqualTo(BestOf.BO5);
        assertThat(matches.get(4).getRoundLabel()).isEqualTo("Demi-finale 1");
        assertThat(matches.get(4).getBestOf()).isEqualTo(BestOf.BO3);
        assertThat(matches.get(0).getNextMatch()).isSameAs(matches.get(4));
        assertThat(matches.get(1).getNextMatch()).isSameAs(matches.get(4));
        assertThat(matches.get(1).getNextMatchSlot()).isEqualTo(2);
        assertThat(matches.get(5).getNextMatch()).isSameAs(finale);
        assertLinksPointForward(matches);
    }

    @Test
    void lesMeilleuresTetesDeSerieSontExempteesQuandLeNombreNEstPasUnePuissanceDeDeux() {
        List<Team> seeds = teams(6);

        List<Match> matches = StageGenerator.singleElimination(
                stage(StageFormat.SINGLE_ELIMINATION, BestOf.BO3), seeds, START, 1, null);

        // 2 quarts (3-6, 4-5), 2 demies ou attendent les tetes de serie 1 et 2, 1 finale.
        assertThat(matches).hasSize(5);
        assertThat(matches.subList(0, 2))
                .extracting(m -> m.getTeam1().getId() + "-" + m.getTeam2().getId())
                .containsExactlyInAnyOrder("4-5", "3-6");
        List<Match> semis = matches.stream()
                .filter(m -> m.getRoundLabel().startsWith("Demi"))
                .toList();
        assertThat(semis).extracting(m -> m.getTeam1().getId()).containsExactly(1L, 2L);
        assertThat(semis).allSatisfy(m -> assertThat(m.getTeam2()).isNull());
        assertThat(matches.subList(0, 2))
                .extracting(Match::getRoundLabel)
                .containsExactly("Quart de finale 1", "Quart de finale 2");
        assertLinksPointForward(matches);
    }

    // ---------- Double elimination ----------

    @Test
    void uneDoubleEliminationAHuitRelieLesPerdantsAuBracketDesPerdants() {
        List<Team> seeds = teams(8);

        List<Match> matches = StageGenerator.doubleElimination(
                stage(StageFormat.DOUBLE_ELIMINATION, BestOf.BO5), seeds, START, 1, null);

        List<Match> upper = side(matches, "UPPER");
        List<Match> lower = side(matches, "LOWER");
        assertThat(upper).hasSize(7);
        assertThat(lower).hasSize(6);
        assertThat(side(matches, "GRAND_FINAL")).hasSize(1);
        // Chaque match du bracket des vainqueurs envoie son perdant dans le bracket des perdants.
        assertThat(upper)
                .allSatisfy(
                        m -> assertThat(m.getLoserNextMatch().getBracketSide()).isEqualTo("LOWER"));
        // Chaque match du bracket des perdants recoit exactement deux equipes.
        Map<Match, Integer> feeds = new HashMap<>();
        matches.forEach(m -> {
            if (m.getNextMatch() != null) {
                feeds.merge(m.getNextMatch(), 1, Integer::sum);
            }
            if (m.getLoserNextMatch() != null) {
                feeds.merge(m.getLoserNextMatch(), 1, Integer::sum);
            }
        });
        assertThat(lower).allSatisfy(m -> assertThat(feeds.get(m)).isEqualTo(2));
        assertThat(lower.get(lower.size() - 1).getRoundLabel()).isEqualTo("LB Finale");
        assertThat(lower.get(lower.size() - 1).getNextMatch().getBracketSide()).isEqualTo("GRAND_FINAL");
        assertLinksPointForward(matches);
    }

    @Test
    void uneDoubleEliminationAQuatreEquipes() {
        List<Match> matches = StageGenerator.doubleElimination(
                stage(StageFormat.DOUBLE_ELIMINATION, BestOf.BO3), teams(4), START, 2, BestOf.BO5);

        assertThat(matches)
                .extracting(Match::getRoundLabel)
                .containsExactly("UB R1 1", "UB R1 2", "UB Finale", "LB R1", "LB Finale", "Grande finale");
        assertThat(matches.get(5).getBestOf()).isEqualTo(BestOf.BO5);
        assertThat(matches.get(5).getDate()).isAfter(matches.get(4).getDate());
    }

    // ---------- Ronde suisse ----------

    @Test
    void laPremiereRondeSuisseOpposeLaMoitieHauteALaMoitieBasse() {
        List<Team> seeds = teams(8);

        List<Match> round1 =
                StageGenerator.swissRound(stage(StageFormat.SWISS, BestOf.BO1), 1, seeds, List.of(), START);

        assertThat(round1)
                .extracting(m -> m.getTeam1().getId() + "-" + m.getTeam2().getId())
                .containsExactly("1-5", "2-6", "3-7", "4-8");
        assertThat(round1).allSatisfy(m -> {
            assertThat(m.getRoundLabel()).isEqualTo("R1");
            assertThat(m.getBracketSide()).isEqualTo("SWISS_STAGE");
        });
    }

    @Test
    void lesRondesSuivantesApparientABilanEgalSansRevancheJusquALaFin() {
        CompetitionStage swiss = stage(StageFormat.SWISS, BestOf.BO1);
        List<Team> seeds = teams(16);
        List<Match> played = new ArrayList<>();
        int round = 1;
        List<Match> current = StageGenerator.swissRound(swiss, round, seeds, played, START);
        while (!current.isEmpty()) {
            // La meilleure tete de serie gagne toujours.
            current.forEach(m -> win(m, m.getTeam1().getId() < m.getTeam2().getId()));
            played.addAll(current);
            Set<String> pairs = new HashSet<>();
            played.forEach(
                    m -> assertThat(pairs.add(key(m))).as("revanche " + key(m)).isTrue());
            round++;
            current = StageGenerator.swissRound(swiss, round, seeds, played, START);
            current.forEach(m -> assertThat(record(played, m.getTeam1())).isEqualTo(record(played, m.getTeam2())));
        }

        // A 16 equipes : 3 victoires pour passer, 3 defaites pour sortir, 5 rondes au plus.
        assertThat(round - 1).isEqualTo(5);
        assertThat(StageGenerator.swissThreshold(16)).isEqualTo(3);
        long qualified = seeds.stream().filter(t -> record(played, t)[0] == 3).count();
        assertThat(qualified).isEqualTo(8);
    }

    @Test
    void seuilDeLaRondeSuisseSelonLeNombreDEquipes() {
        assertThat(StageGenerator.swissThreshold(8)).isEqualTo(2);
        assertThat(StageGenerator.swissThreshold(32)).isEqualTo(4);
    }

    // ---------- Outils ----------

    private CompetitionStage stage(StageFormat format, BestOf bestOf) {
        return new CompetitionStage(competition, 1, "Phase", format, bestOf);
    }

    private static List<Team> teams(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> {
                    Team t = new Team("T" + i, "Team " + i, null);
                    t.setId((long) i);
                    return t;
                })
                .toList();
    }

    private static List<Match> side(List<Match> matches, String side) {
        return matches.stream().filter(m -> side.equals(m.getBracketSide())).toList();
    }

    /** Un match ne pointe que vers des matchs places apres lui (ordre d'enregistrement inverse). */
    private static void assertLinksPointForward(List<Match> matches) {
        for (int i = 0; i < matches.size(); i++) {
            Match m = matches.get(i);
            if (m.getNextMatch() != null) {
                assertThat(matches.indexOf(m.getNextMatch())).isGreaterThan(i);
            }
            if (m.getLoserNextMatch() != null) {
                assertThat(matches.indexOf(m.getLoserNextMatch())).isGreaterThan(i);
            }
        }
    }

    private static void win(Match m, boolean team1Wins) {
        m.setScore1(team1Wins ? 1 : 0);
        m.setScore2(team1Wins ? 0 : 1);
        m.setStatus(MatchStatus.COMPLETED);
    }

    private static String key(Match m) {
        long a = Math.min(m.getTeam1().getId(), m.getTeam2().getId());
        long b = Math.max(m.getTeam1().getId(), m.getTeam2().getId());
        return a + "-" + b;
    }

    private static int[] record(List<Match> played, Team team) {
        int[] wl = new int[2];
        for (Match m : played) {
            boolean isTeam1 = m.getTeam1() == team;
            if (!isTeam1 && m.getTeam2() != team) {
                continue;
            }
            boolean won = isTeam1 == (m.getScore1() > m.getScore2());
            wl[won ? 0 : 1]++;
        }
        return wl;
    }
}
