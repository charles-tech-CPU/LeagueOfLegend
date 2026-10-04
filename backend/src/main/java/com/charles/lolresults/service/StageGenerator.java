package com.charles.lolresults.service;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.CompetitionGroup;
import com.charles.lolresults.domain.CompetitionStage;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchPhase;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Team;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Genere les matchs d'une phase a partir de son format, sans rien persister.
 *
 * Les listes renvoyees sont ordonnees de sorte qu'un match apparaisse toujours
 * AVANT les matchs vers lesquels il fait avancer ses equipes (nextMatch /
 * loserNextMatch) : il suffit de les enregistrer en partant de la fin.
 */
public final class StageGenerator {

    public static final String SWISS_SIDE = "SWISS_STAGE";
    private static final String GRAND_FINAL = "GRAND_FINAL";

    private StageGenerator() {}

    /** Matchs generes et sous-groupes a enregistrer avant eux (vide s'il n'y a qu'une poule). */
    public record Plan(List<CompetitionGroup> groups, List<Match> matches) {}

    // ---------- Poules ----------

    /**
     * Poule(s) en toutes rondes (methode du cercle) : chaque journee, chaque equipe joue
     * au plus une fois. En aller-retour, la phase retour inverse les equipes.
     */
    public static Plan roundRobin(
            CompetitionStage stage, List<List<Team>> groups, boolean doubleRound, LocalDate start, int daysBetween) {
        List<CompetitionGroup> createdGroups = new ArrayList<>();
        List<Match> matches = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            CompetitionGroup group = null;
            if (groups.size() > 1) {
                group = new CompetitionGroup(stage.getCompetition(), "Groupe " + (char) ('A' + g));
                createdGroups.add(group);
            }
            List<List<Team[]>> rounds = circleRounds(groups.get(g));
            int legRounds = rounds.size();
            for (int leg = 0; leg < (doubleRound ? 2 : 1); leg++) {
                for (int r = 0; r < legRounds; r++) {
                    int day = leg * legRounds + r;
                    for (Team[] pair : rounds.get(r)) {
                        Team home = leg == 0 ? pair[0] : pair[1];
                        Team away = leg == 0 ? pair[1] : pair[0];
                        Match m = newMatch(
                                stage,
                                "J" + (day + 1),
                                start.plusDays((long) day * daysBetween),
                                stage.getBestOf(),
                                MatchPhase.REGULAR_SEASON,
                                null,
                                home,
                                away);
                        m.setGroup(group);
                        matches.add(m);
                    }
                }
            }
        }
        return new Plan(createdGroups, matches);
    }

    private static List<List<Team[]>> circleRounds(List<Team> group) {
        List<Team> teams = new ArrayList<>(group);
        if (teams.size() % 2 == 1) {
            teams.add(null); // exempt de la journee
        }
        int n = teams.size();
        List<List<Team[]>> rounds = new ArrayList<>();
        for (int r = 0; r < n - 1; r++) {
            List<Team[]> pairs = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                Team a = teams.get(i);
                Team b = teams.get(n - 1 - i);
                if (a != null && b != null) {
                    // Alterne le cote de l'equipe fixe pour ne pas la laisser toujours en team1.
                    pairs.add(i == 0 && r % 2 == 1 ? new Team[] {b, a} : new Team[] {a, b});
                }
            }
            rounds.add(pairs);
            teams.add(1, teams.remove(n - 1));
        }
        return rounds;
    }

    // ---------- Ronde suisse ----------

    /** Victoires pour se qualifier (= defaites pour etre elimine) : 2 a 8 equipes, 3 a 16, 4 a 32. */
    public static int swissThreshold(int teamCount) {
        int rounds = 32 - Integer.numberOfLeadingZeros(Math.max(teamCount - 1, 1));
        return Math.max(rounds - 1, 1);
    }

    /**
     * Ronde suivante d'une phase suisse. Ronde 1 : la tete de serie i affronte la i + n/2.
     * Ensuite, les equipes encore en lice sont appariees a bilan egal, sans revanche si possible.
     * Renvoie une liste vide quand toutes les equipes sont qualifiees ou eliminees.
     */
    public static List<Match> swissRound(
            CompetitionStage stage, int round, List<Team> seeds, List<Match> previous, LocalDate date) {
        int threshold = swissThreshold(seeds.size());
        Map<Long, int[]> records = new HashMap<>();
        Set<String> played = new HashSet<>();
        for (Team t : seeds) {
            records.put(t.getId(), new int[2]);
        }
        for (Match m : previous) {
            if (m.getStatus() != MatchStatus.COMPLETED) {
                continue;
            }
            boolean team1Won = m.getScore1() > m.getScore2();
            records.get((team1Won ? m.getTeam1() : m.getTeam2()).getId())[0]++;
            records.get((team1Won ? m.getTeam2() : m.getTeam1()).getId())[1]++;
            played.add(pairKey(m.getTeam1(), m.getTeam2()));
        }

        List<Team> alive = new ArrayList<>(seeds.stream()
                .filter(t -> records.get(t.getId())[0] < threshold && records.get(t.getId())[1] < threshold)
                .toList());
        List<Team[]> pairs;
        if (round == 1) {
            int half = alive.size() / 2;
            pairs = new ArrayList<>();
            for (int i = 0; i < half; i++) {
                pairs.add(new Team[] {alive.get(i), alive.get(i + half)});
            }
        } else {
            alive.sort(Comparator.comparingInt((Team t) -> -records.get(t.getId())[0])
                    .thenComparingInt(t -> records.get(t.getId())[1])
                    .thenComparingInt(seeds::indexOf));
            pairs = pairWithoutRematch(alive, played, records);
            if (pairs == null) {
                pairs = pairInOrder(alive);
            }
        }

        List<Match> matches = new ArrayList<>();
        for (Team[] pair : pairs) {
            matches.add(newMatch(
                    stage, "R" + round, date, stage.getBestOf(), MatchPhase.PLAYOFFS, SWISS_SIDE, pair[0], pair[1]));
        }
        return matches;
    }

    /** Appariement a bilan egal autant que possible, sans revanche (retour arriere si besoin). */
    private static List<Team[]> pairWithoutRematch(List<Team> teams, Set<String> played, Map<Long, int[]> records) {
        if (teams.size() < 2) {
            return new ArrayList<>();
        }
        Team first = teams.get(0);
        List<Team> candidates = new ArrayList<>(teams.subList(1, teams.size()));
        int[] firstRecord = records.get(first.getId());
        // Les adversaires au meme bilan d'abord (tri stable : l'ordre de classement est conserve).
        candidates.sort(Comparator.comparingInt(t -> sameRecord(records.get(t.getId()), firstRecord) ? 0 : 1));
        for (Team opponent : candidates) {
            if (played.contains(pairKey(first, opponent))) {
                continue;
            }
            List<Team> rest = new ArrayList<>(teams);
            rest.remove(first);
            rest.remove(opponent);
            List<Team[]> others = pairWithoutRematch(rest, played, records);
            if (others != null) {
                others.add(0, new Team[] {first, opponent});
                return others;
            }
        }
        return null;
    }

    private static List<Team[]> pairInOrder(List<Team> teams) {
        List<Team[]> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < teams.size(); i += 2) {
            pairs.add(new Team[] {teams.get(i), teams.get(i + 1)});
        }
        return pairs;
    }

    private static boolean sameRecord(int[] a, int[] b) {
        return a[0] == b[0] && a[1] == b[1];
    }

    private static String pairKey(Team a, Team b) {
        long x = Math.min(a.getId(), b.getId());
        long y = Math.max(a.getId(), b.getId());
        return x + "-" + y;
    }

    // ---------- Elimination simple ----------

    /**
     * Bracket a elimination directe. Les tetes de serie sont dans l'ordre (1 = meilleure) ;
     * si le nombre d'equipes n'est pas une puissance de 2, les meilleures sont exemptees
     * du premier tour et placees directement au tour suivant.
     */
    public static List<Match> singleElimination(
            CompetitionStage stage, List<Team> seeds, LocalDate start, int daysBetween, BestOf finalBestOf) {
        int size = Integer.highestOneBit(seeds.size() - 1) << 1;
        int roundCount = Integer.numberOfTrailingZeros(size);
        List<Match[]> rounds = new ArrayList<>();
        for (int r = 0; r < roundCount; r++) {
            int count = size >> (r + 1);
            Match[] matches = new Match[count];
            boolean isFinal = count == 1;
            for (int k = 0; k < count; k++) {
                String label = eliminationLabel(size >> r) + (count > 1 ? " " + (k + 1) : "");
                matches[k] = newMatch(
                        stage,
                        label,
                        start.plusDays((long) r * daysBetween),
                        isFinal && finalBestOf != null ? finalBestOf : stage.getBestOf(),
                        MatchPhase.PLAYOFFS,
                        isFinal ? GRAND_FINAL : "BRACKET",
                        null,
                        null);
            }
            rounds.add(matches);
        }
        for (int r = 0; r + 1 < roundCount; r++) {
            Match[] current = rounds.get(r);
            for (int k = 0; k < current.length; k++) {
                current[k].setNextMatch(rounds.get(r + 1)[k / 2]);
                current[k].setNextMatchSlot(k % 2 + 1);
            }
        }

        int[] order = bracketOrder(size);
        Match[] firstRound = rounds.get(0);
        for (int k = 0; k < firstRound.length; k++) {
            Team a = seedOrNull(seeds, order[2 * k]);
            Team b = seedOrNull(seeds, order[2 * k + 1]);
            if (a != null && b != null) {
                firstRound[k].setTeam1(a);
                firstRound[k].setTeam2(b);
            } else {
                // Exemption : l'equipe passe directement au tour suivant, ce match n'existe pas.
                place(firstRound[k].getNextMatch(), firstRound[k].getNextMatchSlot(), a != null ? a : b);
                firstRound[k] = null;
            }
        }
        // Les matchs du premier tour restants sont renumerotes (1, 2...) une fois les exemptions retirees.
        List<Match> played = Arrays.stream(firstRound).filter(Objects::nonNull).toList();
        if (played.size() < firstRound.length) {
            String label = eliminationLabel(size);
            for (int k = 0; k < played.size(); k++) {
                played.get(k).setRoundLabel(label + (played.size() > 1 ? " " + (k + 1) : ""));
            }
        }

        List<Match> all = new ArrayList<>();
        for (Match[] round : rounds) {
            for (Match m : round) {
                if (m != null) {
                    all.add(m);
                }
            }
        }
        return all;
    }

    private static String eliminationLabel(int remainingTeams) {
        return switch (remainingTeams) {
            case 2 -> "Finale";
            case 4 -> "Demi-finale";
            case 8 -> "Quart de finale";
            case 16 -> "Huitieme de finale";
            default -> "Tour de " + remainingTeams;
        };
    }

    // ---------- Double elimination ----------

    /**
     * Bracket des vainqueurs + bracket des perdants + grande finale, pour 4, 8, 16 ou 32
     * equipes. Les perdants du premier tour s'affrontent, puis chaque tour du bracket des
     * perdants accueille les perdants du tour correspondant du bracket des vainqueurs.
     */
    public static List<Match> doubleElimination(
            CompetitionStage stage, List<Team> seeds, LocalDate start, int daysBetween, BestOf finalBestOf) {
        int size = seeds.size();
        int ubRounds = Integer.numberOfTrailingZeros(size);
        BestOf bo = stage.getBestOf();

        List<Match[]> upper = new ArrayList<>();
        for (int r = 0; r < ubRounds; r++) {
            int count = size >> (r + 1);
            Match[] matches = new Match[count];
            for (int k = 0; k < count; k++) {
                String label = count == 1 ? "UB Finale" : "UB R" + (r + 1) + " " + (k + 1);
                matches[k] = newMatch(
                        stage,
                        label,
                        start.plusDays((long) r * daysBetween),
                        bo,
                        MatchPhase.PLAYOFFS,
                        "UPPER",
                        null,
                        null);
            }
            upper.add(matches);
        }
        int[] order = bracketOrder(size);
        for (int k = 0; k < upper.get(0).length; k++) {
            upper.get(0)[k].setTeam1(seeds.get(order[2 * k] - 1));
            upper.get(0)[k].setTeam2(seeds.get(order[2 * k + 1] - 1));
        }
        for (int r = 0; r + 1 < ubRounds; r++) {
            for (int k = 0; k < upper.get(r).length; k++) {
                upper.get(r)[k].setNextMatch(upper.get(r + 1)[k / 2]);
                upper.get(r)[k].setNextMatchSlot(k % 2 + 1);
            }
        }

        // Bracket des perdants, tour par tour.
        List<Match[]> lower = new ArrayList<>();
        Match[] current = new Match[size / 4];
        for (int k = 0; k < current.length; k++) {
            current[k] = lowerMatch(stage, lower.size(), start, daysBetween);
            linkLoser(upper.get(0)[2 * k], current[k], 1);
            linkLoser(upper.get(0)[2 * k + 1], current[k], 2);
        }
        lower.add(current);
        for (int j = 1; j < ubRounds; j++) {
            Match[] ubRound = upper.get(j);
            Match[] dropIn = new Match[current.length];
            for (int i = 0; i < dropIn.length; i++) {
                dropIn[i] = lowerMatch(stage, lower.size(), start, daysBetween);
                linkWinner(current[i], dropIn[i], 1);
                // Ordre inverse : evite de retrouver tout de suite l'adversaire du bracket des vainqueurs.
                linkLoser(ubRound[ubRound.length - 1 - i], dropIn[i], 2);
            }
            lower.add(dropIn);
            current = dropIn;
            if (current.length > 1) {
                Match[] merged = new Match[current.length / 2];
                for (int i = 0; i < merged.length; i++) {
                    merged[i] = lowerMatch(stage, lower.size(), start, daysBetween);
                    linkWinner(current[2 * i], merged[i], 1);
                    linkWinner(current[2 * i + 1], merged[i], 2);
                }
                lower.add(merged);
                current = merged;
            }
        }
        for (int t = 0; t < lower.size(); t++) {
            Match[] round = lower.get(t);
            for (int i = 0; i < round.length; i++) {
                round[i].setRoundLabel(
                        t == lower.size() - 1
                                ? "LB Finale"
                                : "LB R" + (t + 1) + (round.length > 1 ? " " + (i + 1) : ""));
            }
        }

        Match grandFinal = newMatch(
                stage,
                "Grande finale",
                start.plusDays((long) (lower.size() + 1) * daysBetween),
                finalBestOf != null ? finalBestOf : bo,
                MatchPhase.PLAYOFFS,
                GRAND_FINAL,
                null,
                null);
        linkWinner(upper.get(ubRounds - 1)[0], grandFinal, 1);
        linkWinner(current[0], grandFinal, 2);

        List<Match> all = new ArrayList<>();
        upper.forEach(round -> Collections.addAll(all, round));
        lower.forEach(round -> Collections.addAll(all, round));
        all.add(grandFinal);
        return all;
    }

    private static Match lowerMatch(CompetitionStage stage, int lowerRound, LocalDate start, int daysBetween) {
        return newMatch(
                stage,
                "LB",
                start.plusDays((long) (lowerRound + 1) * daysBetween),
                stage.getBestOf(),
                MatchPhase.PLAYOFFS,
                "LOWER",
                null,
                null);
    }

    private static void linkWinner(Match from, Match to, int slot) {
        from.setNextMatch(to);
        from.setNextMatchSlot(slot);
    }

    private static void linkLoser(Match from, Match to, int slot) {
        from.setLoserNextMatch(to);
        from.setLoserNextMatchSlot(slot);
    }

    // ---------- Outils communs ----------

    /** Ordre des tetes de serie dans un bracket de taille n : 1 et 2 ne se croisent qu'en finale. */
    static int[] bracketOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1));
        while (order.size() < size) {
            int n = order.size() * 2;
            List<Integer> next = new ArrayList<>();
            for (int seed : order) {
                next.add(seed);
                next.add(n + 1 - seed);
            }
            order = next;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Team seedOrNull(List<Team> seeds, int seed) {
        return seed <= seeds.size() ? seeds.get(seed - 1) : null;
    }

    private static void place(Match target, int slot, Team team) {
        if (slot == 1) {
            target.setTeam1(team);
        } else {
            target.setTeam2(team);
        }
    }

    private static Match newMatch(
            CompetitionStage stage,
            String roundLabel,
            LocalDate date,
            BestOf bestOf,
            MatchPhase phase,
            String bracketSide,
            Team team1,
            Team team2) {
        Match m = new Match();
        m.setCompetition(stage.getCompetition());
        m.setStage(stage);
        m.setRoundLabel(roundLabel);
        m.setDate(date);
        m.setBestOf(bestOf);
        m.setPhase(phase);
        m.setBracketSide(bracketSide);
        m.setTeam1(team1);
        m.setTeam2(team2);
        m.setStatus(MatchStatus.SCHEDULED);
        return m;
    }
}
