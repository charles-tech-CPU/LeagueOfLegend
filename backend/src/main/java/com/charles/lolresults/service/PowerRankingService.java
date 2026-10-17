package com.charles.lolresults.service;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.PowerRankingRowDto;
import com.charles.lolresults.repository.MatchRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Power ranking d'une saison : un classement Elo recalcule a la volee a partir des series
 * jouees de la saison, toutes competitions confondues, dans l'ordre chronologique (jamais
 * stocke : un resultat saisi est pris en compte des la requete suivante).
 *
 * <ul>
 *   <li>Coefficient de ligue : chaque equipe part de la note de sa ligue ({@link #LEAGUE_RATINGS},
 *       1600 pour la LCK et la LPL, 1350 pour les ligues mineures). Dans une ligue, les equipes
 *       s'echangent des points entre elles : la moyenne de la ligue reste sa note de depart, que
 *       seuls les evenements majeurs corrigent. Une equipe sans ligue de la saison (deuxieme
 *       division vue seulement aux EMEA Masters, par exemple) part de {@value #SECOND_TIER_RATING}.
 *   <li>Une victoire rapporte d'autant plus que l'adversaire etait bien note au moment du match
 *       (qualite des adversaires battus), une defaite contre plus faible coute cher.
 *   <li>Le gain est module par le format (un BO5 pese plus qu'un BO1), l'ecart de manches (un
 *       3-0 plus qu'un 3-2) et les evenements majeurs (Worlds, MSI, First Stand), seuls ponts
 *       entre les regions.
 *   <li>Les derniers resultats comptent naturellement plus : chaque serie deplace la note a
 *       partir de la note courante (forme recente).
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PowerRankingService {

    /** Note de depart d'une equipe d'une ligue absente de LEAGUE_RATINGS. */
    static final int DEFAULT_LEAGUE_RATING = 1400;

    static final int SECOND_TIER_RATING = 1300;

    /**
     * Coefficient de chaque ligue (code de la competition), sous forme de note de depart. Les
     * ancetres d'une ligue (Champions et OGN pour la LCK, EU LCS pour la LEC...) ont sa note.
     */
    static final Map<String, Integer> LEAGUE_RATINGS = leagueRatings();

    /** Evenements internationaux de premier plan : les seuls qui comparent les meilleures regions. */
    private static final Set<String> MAJOR_EVENTS = Set.of("WORLDS", "MSI", "FST");

    /** Phases de qualification regionales : ne disent pas a elles seules la ligue d'une equipe. */
    private static final Set<String> NOT_A_LEAGUE = Set.of("RF");

    private static final double BASE_K = 24;
    private static final double INTERNATIONAL_WEIGHT = 1.5;
    private static final int FORM_LENGTH = 5;
    private static final int TREND_DAYS = 7;

    private final MatchRepository matchRepository;

    public PowerRankingService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    private static Map<String, Integer> leagueRatings() {
        Map<String, Integer> ratings = new HashMap<>();
        for (String code : List.of("LCK", "Champions", "OGN", "LPL")) {
            ratings.put(code, 1600);
        }
        for (String code : List.of("LEC", "EU LCS")) {
            ratings.put(code, 1500);
        }
        for (String code : List.of("LCS", "NA LCS", "LTA", "LTA N", "LCP", "LMS", "PCS")) {
            ratings.put(code, 1450);
        }
        for (String code : List.of("CBLOL", "LTA S", "VCS")) {
            ratings.put(code, 1400);
        }
        for (String code :
                List.of("LJL", "TCL", "LCL", "OPL", "LCO", "LLA", "CLS", "LLN", "CLA", "CLN", "GPL", "LST", "SLTV")) {
            ratings.put(code, 1350);
        }
        return Map.copyOf(ratings);
    }

    public List<PowerRankingRowDto> compute(int season) {
        List<Match> played = matchRepository.findByCompetition_Season(season).stream()
                .filter(PowerRankingService::isRated)
                .sorted(CHRONOLOGICAL)
                .toList();
        if (played.isEmpty()) {
            return List.of();
        }
        LocalDate lastDay = played.get(played.size() - 1).getDate();
        LocalDate trendCutoff = lastDay.minusDays(TREND_DAYS);
        Map<Long, String> leagues = leagues(played);
        Map<Long, Integer> previousRanks = ranks(rate(
                played.stream()
                        .filter(m ->
                                m.getDate().isBefore(trendCutoff) || m.getDate().isEqual(trendCutoff))
                        .toList(),
                leagues));

        Map<Long, TeamRating> ratings = rate(played, leagues);
        List<TeamRating> ordered = ordered(ratings);
        List<PowerRankingRowDto> rows = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            TeamRating r = ordered.get(i);
            int rank = i + 1;
            Integer previous = previousRanks.get(r.team.getId());
            rows.add(new PowerRankingRowDto(
                    rank,
                    previous != null ? previous - rank : null,
                    r.team.getId(),
                    r.team.getCode(),
                    r.team.getName(),
                    r.team.getRegion(),
                    r.team.getLogo() != null,
                    leagues.get(r.team.getId()),
                    (int) Math.round(r.initial),
                    (int) Math.round(r.rating),
                    (int) Math.round(r.rating - r.ratingBefore(trendCutoff)),
                    r.seriesWon,
                    r.seriesLost,
                    r.gamesWon,
                    r.gamesLost,
                    r.form.subList(Math.max(0, r.form.size() - FORM_LENGTH), r.form.size()),
                    r.bestWin,
                    r.lastPlayed));
        }
        return rows;
    }

    /** Serie prise en compte : jouee, deux equipes connues et un score saisi. */
    private static boolean isRated(Match m) {
        return m.getStatus() == MatchStatus.COMPLETED
                && m.getTeam1() != null
                && m.getTeam2() != null
                && m.getScore1() != null
                && m.getScore2() != null
                && m.getDate() != null;
    }

    static final Comparator<Match> CHRONOLOGICAL = Comparator.comparing(Match::getDate)
            .thenComparing(Match::getTime, Comparator.nullsFirst(Comparator.<LocalTime>naturalOrder()))
            .thenComparing(Match::getId, Comparator.nullsLast(Comparator.<Long>naturalOrder()));

    /**
     * Ligue de chaque equipe dans la saison : la ligue regionale ou elle a joue le plus de
     * series. Absente pour une equipe qui n'a joue dans aucune ligue.
     */
    private static Map<Long, String> leagues(List<Match> matches) {
        Map<Long, Map<String, Integer>> counts = new HashMap<>();
        for (Match m : matches) {
            Competition c = m.getCompetition();
            if (c == null || c.getType() != CompetitionType.REGIONAL_LEAGUE || NOT_A_LEAGUE.contains(c.getCode())) {
                continue;
            }
            for (Team t : List.of(m.getTeam1(), m.getTeam2())) {
                counts.computeIfAbsent(t.getId(), id -> new HashMap<>()).merge(c.getCode(), 1, Integer::sum);
            }
        }
        Map<Long, String> result = new HashMap<>();
        counts.forEach((teamId, byLeague) -> result.put(
                teamId,
                byLeague.entrySet().stream()
                        .max(Map.Entry.<String, Integer>comparingByValue()
                                .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                        .orElseThrow()
                        .getKey()));
        return result;
    }

    /** Note de depart : celle de la ligue de l'equipe, ou de la deuxieme division sans ligue. */
    static int startRating(String league) {
        if (league == null) {
            return SECOND_TIER_RATING;
        }
        return LEAGUE_RATINGS.getOrDefault(league, DEFAULT_LEAGUE_RATING);
    }

    private static Map<Long, TeamRating> rate(List<Match> matches, Map<Long, String> leagues) {
        Map<Long, TeamRating> ratings = new LinkedHashMap<>();
        for (Match m : matches) {
            TeamRating a = ratings.computeIfAbsent(
                    m.getTeam1().getId(), id -> new TeamRating(m.getTeam1(), startRating(leagues.get(id))));
            TeamRating b = ratings.computeIfAbsent(
                    m.getTeam2().getId(), id -> new TeamRating(m.getTeam2(), startRating(leagues.get(id))));
            int s1 = m.getScore1();
            int s2 = m.getScore2();
            double expectedA = 1 / (1 + Math.pow(10, (b.rating - a.rating) / 400));
            double resultA = seriesResult(s1, s2);
            double delta = k(m, Math.abs(s1 - s2)) * (resultA - expectedA);
            int ratingA = (int) Math.round(a.rating);
            int ratingB = (int) Math.round(b.rating);
            a.addSeries(m, s1, s2, delta, b.team, ratingB);
            b.addSeries(m, s2, s1, -delta, a.team, ratingA);
        }
        return ratings;
    }

    /** Resultat Elo d'une serie pour l'equipe 1 : 1 victoire, 0 defaite, 0,5 nul (BO2). */
    private static double seriesResult(int score1, int score2) {
        if (score1 == score2) {
            return 0.5;
        }
        return score1 > score2 ? 1 : 0;
    }

    /** Poids d'une serie : format, ecart de manches, evenement majeur. */
    static double k(Match m, int gameDiff) {
        double format =
                switch (m.getBestOf() != null ? m.getBestOf() : BestOf.BO1) {
                    case BO1, BO2 -> 1.0;
                    case BO3 -> 1.25;
                    case BO5 -> 1.5;
                };
        double margin = 1 + 0.25 * Math.max(0, gameDiff - 1);
        boolean major = m.getCompetition() != null
                && MAJOR_EVENTS.contains(m.getCompetition().getCode());
        return BASE_K * format * margin * (major ? INTERNATIONAL_WEIGHT : 1);
    }

    /** Meilleure note d'abord ; a egalite, le plus de series gagnees puis le code. */
    private static List<TeamRating> ordered(Map<Long, TeamRating> ratings) {
        return ratings.values().stream()
                .sorted(Comparator.comparingDouble((TeamRating r) -> r.rating)
                        .reversed()
                        .thenComparing(Comparator.comparingInt((TeamRating r) -> r.seriesWon)
                                .reversed())
                        .thenComparing(r -> r.team.getCode(), Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static Map<Long, Integer> ranks(Map<Long, TeamRating> ratings) {
        Map<Long, Integer> result = new HashMap<>();
        List<TeamRating> ordered = ordered(ratings);
        for (int i = 0; i < ordered.size(); i++) {
            result.put(ordered.get(i).team.getId(), i + 1);
        }
        return result;
    }

    /** Note courante d'une equipe et ce qu'on en affiche. */
    private static final class TeamRating {
        final Team team;
        final double initial;
        double rating;
        int seriesWon;
        int seriesLost;
        int gamesWon;
        int gamesLost;
        final List<String> form = new ArrayList<>();
        PowerRankingRowDto.BestWin bestWin;
        LocalDate lastPlayed;
        /** Note apres chaque journee jouee, pour l'evolution sur les derniers jours. */
        final List<Map.Entry<LocalDate, Double>> history = new ArrayList<>();

        TeamRating(Team team, int initial) {
            this.team = team;
            this.initial = initial;
            this.rating = initial;
        }

        void addSeries(Match m, int own, int other, double delta, Team opponent, int opponentRating) {
            rating += delta;
            gamesWon += own;
            gamesLost += other;
            if (own > other) {
                seriesWon++;
                form.add("W");
                if (bestWin == null || opponentRating > bestWin.opponentRating()) {
                    bestWin = new PowerRankingRowDto.BestWin(
                            m.getId(), opponent.getId(), opponent.getCode(), opponentRating, own + "-" + other);
                }
            } else if (own < other) {
                seriesLost++;
                form.add("L");
            } else {
                form.add("D");
            }
            lastPlayed = m.getDate();
            history.add(Map.entry(m.getDate(), rating));
        }

        /** Note a la fin du jour donne (note de depart si l'equipe n'avait pas encore joue). */
        double ratingBefore(LocalDate day) {
            double result = initial;
            for (Map.Entry<LocalDate, Double> e : history) {
                if (e.getKey().isAfter(day)) {
                    break;
                }
                result = e.getValue();
            }
            return result;
        }
    }
}
