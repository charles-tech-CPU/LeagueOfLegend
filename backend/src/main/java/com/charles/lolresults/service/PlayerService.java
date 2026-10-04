package com.charles.lolresults.service;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.PlayerStint;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.FormerPlayerCreateDto;
import com.charles.lolresults.dto.MatchDto;
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
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joueurs et historique de leurs equipes (passages). Invariant : Player.team est
 * toujours l'equipe du passage en cours, ou nul s'il n'y en a pas.
 *
 * Les resultats d'un passage ne sont pas stockes : ils sont deduits des matchs
 * COMPLETED de l'equipe pendant la periode, en considerant que le joueur les a tous joues.
 */
@Service
@Transactional
public class PlayerService {

    private static final String PLAYER_NOT_FOUND = "Joueur introuvable : ";
    private static final String GRAND_FINAL = "GRAND_FINAL";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Tri de l'effectif : par poste (TOP -> SUPP) puis par pseudo. */
    private static final Comparator<Player> BY_POSITION_THEN_PSEUDO =
            Comparator.comparing(Player::getPosition).thenComparing(Player::getPseudo, String.CASE_INSENSITIVE_ORDER);

    /** Passage en cours d'abord, puis du plus recent au plus ancien. */
    private static final Comparator<PlayerStint> MOST_RECENT_FIRST = Comparator.comparing(
                    PlayerStint::getEndDate, Comparator.nullsFirst(Comparator.<LocalDate>reverseOrder()))
            .thenComparing(PlayerStint::getStartDate, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()));

    private final PlayerRepository playerRepository;
    private final PlayerStintRepository stintRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;

    public PlayerService(
            PlayerRepository playerRepository,
            PlayerStintRepository stintRepository,
            TeamRepository teamRepository,
            MatchRepository matchRepository) {
        this.playerRepository = playerRepository;
        this.stintRepository = stintRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
    }

    // ---------- Joueurs ----------

    @Transactional(readOnly = true)
    public List<PlayerDto> findAll() {
        return playerRepository.findAll().stream()
                .sorted(Comparator.comparing(Player::getPseudo, String.CASE_INSENSITIVE_ORDER))
                .map(PlayerDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlayerDto findOne(Long id) {
        return PlayerDto.from(findPlayer(id));
    }

    @Transactional(readOnly = true)
    public List<PlayerDto> findByTeam(Long teamId) {
        findTeam(teamId);
        return playerRepository.findByTeamId(teamId).stream()
                .sorted(BY_POSITION_THEN_PSEUDO)
                .map(PlayerDto::from)
                .toList();
    }

    /**
     * Effectif d'une saison : joueurs ayant un passage dans l'equipe pendant l'annee. Une
     * arrivee inconnue ne couvre que l'annee de depart (ou l'annee en cours pour un passage
     * en cours), pour ne pas faire remonter l'effectif actuel dans les anciennes saisons.
     */
    @Transactional(readOnly = true)
    public List<PlayerDto> findByTeamAndSeason(Long teamId, int season) {
        findTeam(teamId);
        return stintRepository.findByTeamId(teamId).stream()
                .filter(stint -> seasons(stint).anyMatch(year -> year == season))
                .map(PlayerStint::getPlayer)
                .distinct()
                .sorted(BY_POSITION_THEN_PSEUDO)
                .map(PlayerDto::from)
                .toList();
    }

    /** Saisons ou l'equipe a joue un match ou compte un joueur, de la plus recente a la plus ancienne. */
    @Transactional(readOnly = true)
    public List<Integer> findRosterSeasons(Long teamId) {
        findTeam(teamId);
        TreeSet<Integer> seasons = new TreeSet<>(Comparator.reverseOrder());
        matchRepository.findByTeam1_IdOrTeam2_IdOrderByDateDesc(teamId, teamId).stream()
                .map(Match::getDate)
                .filter(Objects::nonNull)
                .forEach(date -> seasons.add(date.getYear()));
        stintRepository.findByTeamId(teamId).forEach(stint -> seasons(stint).forEach(seasons::add));
        return List.copyOf(seasons);
    }

    private static IntStream seasons(PlayerStint stint) {
        int last = stint.getEndDate() != null
                ? stint.getEndDate().getYear()
                : LocalDate.now().getYear();
        int first = stint.getStartDate() != null ? stint.getStartDate().getYear() : last;
        return IntStream.rangeClosed(first, last);
    }

    /** Cree un joueur sans equipe actuelle (retraite, ou ajoute pour son historique). */
    public PlayerDto createWithoutTeam(PlayerCreateDto dto) {
        Player player = playerRepository.save(
                new Player(null, dto.pseudo().trim(), countryCode(dto.nationality()), dto.position()));
        return PlayerDto.from(player);
    }

    /** Cree un ancien joueur de l'equipe : sans equipe actuelle, avec un passage termine dans celle-ci. */
    public PlayerDto createFormer(Long teamId, FormerPlayerCreateDto dto) {
        Team team = findTeam(teamId);
        checkDates(dto.startDate(), dto.endDate());
        Player player = playerRepository.save(
                new Player(null, dto.pseudo().trim(), countryCode(dto.nationality()), dto.position()));
        stintRepository.save(new PlayerStint(player, team, dto.startDate(), dto.endDate()));
        return PlayerDto.from(player);
    }

    /** Cree le joueur dans l'equipe, avec un passage en cours (date d'arrivee inconnue). */
    public PlayerDto create(Long teamId, PlayerCreateDto dto) {
        Team team = findTeam(teamId);
        Player player = playerRepository.save(
                new Player(team, dto.pseudo().trim(), countryCode(dto.nationality()), dto.position()));
        stintRepository.save(new PlayerStint(player, team, null, null));
        return PlayerDto.from(player);
    }

    /** Modifie l'identite du joueur ; l'equipe se change par un transfert. */
    public PlayerDto update(Long id, PlayerCreateDto dto) {
        Player player = findPlayer(id);
        player.setPseudo(dto.pseudo().trim());
        player.setNationality(countryCode(dto.nationality()));
        player.setPosition(dto.position());
        return PlayerDto.from(playerRepository.save(player));
    }

    public void delete(Long id) {
        playerRepository.deleteById(id);
    }

    /**
     * Le joueur rejoint une autre equipe (ou aucune si teamId est nul) a partir de la
     * date donnee : le passage en cours se termine la veille et un nouveau commence.
     */
    public PlayerDto transfer(Long id, PlayerTransferDto dto) {
        Player player = findPlayer(id);
        Team target = dto.teamId() != null ? findTeam(dto.teamId()) : null;
        PlayerStint current = stintRepository.findByPlayerIdAndEndDateIsNull(id).orElse(null);

        if (current == null && target == null) {
            throw new IllegalArgumentException("Le joueur est deja sans equipe");
        }
        if (current != null && target != null && current.getTeam().getId().equals(target.getId())) {
            throw new IllegalArgumentException("Le joueur fait deja partie de " + target.getCode());
        }

        if (current != null) {
            LocalDate lastDay = dto.date().minusDays(1);
            if (current.getStartDate() != null && lastDay.isBefore(current.getStartDate())) {
                throw new IllegalArgumentException("La date du transfert doit etre posterieure a l'arrivee chez "
                        + current.getTeam().getCode() + " (" + DAY.format(current.getStartDate()) + ")");
            }
            current.setEndDate(lastDay);
            // Flush immediat : un seul passage en cours par joueur (index unique), et Hibernate
            // executerait sinon l'insertion du nouveau passage avant cette mise a jour.
            stintRepository.saveAndFlush(current);
        }
        if (target != null) {
            checkNoOverlap(id, dto.date(), null, null);
            stintRepository.save(new PlayerStint(player, target, dto.date(), null));
        }

        player.setTeam(target);
        return PlayerDto.from(playerRepository.save(player));
    }

    // ---------- Historique (passages) ----------

    @Transactional(readOnly = true)
    public List<PlayerStintDto> findStints(Long playerId) {
        findPlayer(playerId);
        Map<Long, List<Match>> matchesByTeam = new HashMap<>();
        return stintRepository.findByPlayerId(playerId).stream()
                .sorted(MOST_RECENT_FIRST)
                .map(stint -> toStintDto(
                        stint, matchesByTeam.computeIfAbsent(stint.getTeam().getId(), this::completedMatches)))
                .toList();
    }

    /** Ajoute un passage termine (historique anterieur) ; l'equipe actuelle se change par un transfert. */
    public PlayerStintDto addStint(Long playerId, PlayerStintCreateDto dto) {
        Player player = findPlayer(playerId);
        Team team = findTeam(dto.teamId());
        if (dto.endDate() == null) {
            throw new IllegalArgumentException(
                    "Un passage ajoute doit avoir une date de fin : pour l'equipe actuelle, utilise le transfert");
        }
        checkDates(dto.startDate(), dto.endDate());
        checkNoOverlap(playerId, dto.startDate(), dto.endDate(), null);
        PlayerStint stint = stintRepository.save(new PlayerStint(player, team, dto.startDate(), dto.endDate()));
        return toStintDto(stint, completedMatches(team.getId()));
    }

    /** Corrige les dates (et l'equipe, pour un passage termine) d'un passage. */
    public PlayerStintDto updateStint(Long stintId, PlayerStintCreateDto dto) {
        PlayerStint stint = findStint(stintId);
        Team team = findTeam(dto.teamId());
        if (stint.isCurrent()) {
            if (dto.endDate() != null) {
                throw new IllegalArgumentException("Pour terminer le passage en cours, utilise le transfert");
            }
            if (!team.getId().equals(stint.getTeam().getId())) {
                throw new IllegalArgumentException("Pour changer l'equipe actuelle, utilise le transfert");
            }
        } else if (dto.endDate() == null) {
            throw new IllegalArgumentException("Un passage termine doit avoir une date de fin");
        }
        checkDates(dto.startDate(), dto.endDate());
        checkNoOverlap(stint.getPlayer().getId(), dto.startDate(), dto.endDate(), stint.getId());

        stint.setTeam(team);
        stint.setStartDate(dto.startDate());
        stint.setEndDate(dto.endDate());
        PlayerStint saved = stintRepository.save(stint);
        return toStintDto(saved, completedMatches(team.getId()));
    }

    /** Supprimer le passage en cours laisse le joueur sans equipe. */
    public void deleteStint(Long stintId) {
        PlayerStint stint = findStint(stintId);
        if (stint.isCurrent()) {
            Player player = stint.getPlayer();
            player.setTeam(null);
            playerRepository.save(player);
        }
        stintRepository.delete(stint);
    }

    // ---------- Resultats d'un passage ----------

    private List<Match> completedMatches(Long teamId) {
        return matchRepository.findByTeam1_IdOrTeam2_IdOrderByDateDesc(teamId, teamId).stream()
                .filter(m -> m.getStatus() == MatchStatus.COMPLETED)
                .toList();
    }

    private PlayerStintDto toStintDto(PlayerStint stint, List<Match> teamMatches) {
        Team team = stint.getTeam();
        List<Match> matches =
                teamMatches.stream().filter(m -> stint.covers(m.getDate())).toList();

        Map<Long, CompetitionRecord> byCompetition = new LinkedHashMap<>();
        int wins = 0;
        int gamesWon = 0;
        int gamesLost = 0;
        for (Match m : matches) {
            boolean isTeam1 = m.getTeam1() != null && m.getTeam1().getId().equals(team.getId());
            int ours = isTeam1 ? m.getScore1() : m.getScore2();
            int theirs = isTeam1 ? m.getScore2() : m.getScore1();
            boolean won = ours > theirs;
            wins += won ? 1 : 0;
            gamesWon += ours;
            gamesLost += theirs;
            byCompetition
                    .computeIfAbsent(m.getCompetition().getId(), k -> new CompetitionRecord(m.getCompetition()))
                    .add(won, won && GRAND_FINAL.equals(m.getBracketSide()));
        }

        List<StintCompetitionDto> competitions = byCompetition.values().stream()
                .sorted(Comparator.comparing((CompetitionRecord r) -> r.competition.getSeason())
                        .reversed()
                        .thenComparing(r -> r.competition.getName()))
                .map(CompetitionRecord::toDto)
                .toList();

        return new PlayerStintDto(
                stint.getId(),
                team.getId(),
                team.getCode(),
                team.getName(),
                team.getLogo() != null,
                stint.getStartDate(),
                stint.getEndDate(),
                stint.isCurrent(),
                wins,
                matches.size() - wins,
                gamesWon,
                gamesLost,
                (int) competitions.stream()
                        .filter(StintCompetitionDto::champion)
                        .count(),
                competitions,
                matches.stream().map(MatchDto::from).toList());
    }

    /** Bilan cumule d'une equipe dans une competition, pendant un passage. */
    private static final class CompetitionRecord {
        private final Competition competition;
        private int wins;
        private int losses;
        private boolean champion;

        private CompetitionRecord(Competition competition) {
            this.competition = competition;
        }

        private void add(boolean won, boolean wonGrandFinal) {
            if (won) {
                wins++;
            } else {
                losses++;
            }
            champion |= wonGrandFinal;
        }

        private StintCompetitionDto toDto() {
            return new StintCompetitionDto(
                    competition.getId(),
                    competition.getCode(),
                    competition.getName(),
                    competition.getSeason(),
                    wins,
                    losses,
                    champion);
        }
    }

    // ---------- Validations et recherches ----------

    private static void checkDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("La date de fin doit etre posterieure a la date d'arrivee");
        }
    }

    /** Un joueur ne peut pas etre dans deux equipes a la fois. */
    private void checkNoOverlap(Long playerId, LocalDate start, LocalDate end, Long excludedStintId) {
        for (PlayerStint other : stintRepository.findByPlayerId(playerId)) {
            if (excludedStintId != null && Objects.equals(other.getId(), excludedStintId)) {
                continue;
            }
            if (other.overlaps(start, end)) {
                // Une arrivee inconnue couvre tout le passe : il faut la renseigner pour ajouter un historique.
                String hint = other.getStartDate() == null ? " : renseigne d'abord sa date d'arrivee" : "";
                throw new IllegalArgumentException("Cette periode chevauche le passage chez "
                        + other.getTeam().getCode() + " (" + describe(other) + ")" + hint);
            }
        }
    }

    private static String describe(PlayerStint stint) {
        String from = stint.getStartDate() != null ? DAY.format(stint.getStartDate()) : "arrivee inconnue";
        String to = stint.getEndDate() != null ? DAY.format(stint.getEndDate()) : "aujourd'hui";
        return from + " -> " + to;
    }

    private static String countryCode(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private Player findPlayer(Long id) {
        return playerRepository.findById(id).orElseThrow(() -> new EntityNotFoundException(PLAYER_NOT_FOUND + id));
    }

    private PlayerStint findStint(Long id) {
        return stintRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Passage introuvable : " + id));
    }

    private Team findTeam(Long teamId) {
        return teamRepository
                .findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Equipe introuvable : " + teamId));
    }
}
