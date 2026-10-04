package com.charles.lolresults.service;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionStage;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.StageFormat;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.StageCreateDto;
import com.charles.lolresults.dto.StageDto;
import com.charles.lolresults.repository.CompetitionGroupRepository;
import com.charles.lolresults.repository.CompetitionRepository;
import com.charles.lolresults.repository.CompetitionStageRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phases d'une competition : creation (avec generation des matchs selon le format),
 * ronde suivante d'un swiss, suppression. Les scores se saisissent ensuite match par
 * match ; les brackets avancent tout seuls (voir MatchService.propagateAdvancement).
 */
@Service
@Transactional
public class StageService {

    private static final int DEFAULT_DAYS_BETWEEN_ROUNDS = 7;

    private final CompetitionStageRepository stageRepository;
    private final CompetitionRepository competitionRepository;
    private final CompetitionGroupRepository groupRepository;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    public StageService(
            CompetitionStageRepository stageRepository,
            CompetitionRepository competitionRepository,
            CompetitionGroupRepository groupRepository,
            MatchRepository matchRepository,
            TeamRepository teamRepository) {
        this.stageRepository = stageRepository;
        this.competitionRepository = competitionRepository;
        this.groupRepository = groupRepository;
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<StageDto> findByCompetition(Long competitionId) {
        findCompetition(competitionId);
        return stageRepository.findByCompetitionIdOrderByPosition(competitionId).stream()
                .map(this::toDto)
                .toList();
    }

    public StageDto create(Long competitionId, StageCreateDto dto) {
        Competition competition = findCompetition(competitionId);
        List<List<Team>> groups = resolveTeams(dto.groups());
        List<Team> allTeams = groups.stream().flatMap(List::stream).toList();
        validate(dto, groups, allTeams);

        int position = stageRepository.findByCompetitionIdOrderByPosition(competitionId).stream()
                        .mapToInt(CompetitionStage::getPosition)
                        .max()
                        .orElse(0)
                + 1;
        CompetitionStage stage =
                new CompetitionStage(competition, position, dto.name().trim(), dto.format(), dto.bestOf());
        stage.setTeamCount(allTeams.size());
        stage.setGroupCount(groups.size());
        stage.setAdvancing(dto.advancing());
        stage = stageRepository.save(stage);

        int days = dto.daysBetweenRounds() != null ? dto.daysBetweenRounds() : DEFAULT_DAYS_BETWEEN_ROUNDS;
        LocalDate start = dto.startDate();
        List<Match> matches =
                switch (dto.format()) {
                    case ROUND_ROBIN, DOUBLE_ROUND_ROBIN -> {
                        StageGenerator.Plan plan = StageGenerator.roundRobin(
                                stage, groups, dto.format() == StageFormat.DOUBLE_ROUND_ROBIN, start, days);
                        groupRepository.saveAll(plan.groups());
                        yield plan.matches();
                    }
                    case SWISS -> StageGenerator.swissRound(stage, 1, allTeams, List.of(), start);
                    case SINGLE_ELIMINATION ->
                        StageGenerator.singleElimination(stage, allTeams, start, days, dto.finalBestOf());
                    case DOUBLE_ELIMINATION ->
                        StageGenerator.doubleElimination(stage, allTeams, start, days, dto.finalBestOf());
                    case OTHER -> List.of();
                };
        saveLinked(matches);
        extendCompetitionDates(competition, matches);
        return toDto(stage);
    }

    /**
     * Genere la ronde suivante d'une phase suisse, une fois la ronde en cours entierement jouee.
     * Les tetes de serie sont les equipes de la ronde 1, dans l'ordre (team1 puis team2).
     */
    public StageDto nextSwissRound(Long stageId, LocalDate date) {
        CompetitionStage stage = findStage(stageId);
        if (stage.getFormat() != StageFormat.SWISS) {
            throw new IllegalArgumentException("La phase " + stage.getName() + " n'est pas une ronde suisse");
        }
        List<Match> matches = matchRepository.findByStageId(stageId);
        if (matches.stream().anyMatch(m -> m.getStatus() != MatchStatus.COMPLETED)) {
            throw new IllegalArgumentException("Tous les matchs de la ronde en cours doivent etre joues");
        }
        int lastRound = matches.stream()
                .mapToInt(m -> Integer.parseInt(m.getRoundLabel().substring(1)))
                .max()
                .orElse(0);
        List<Match> next = StageGenerator.swissRound(
                stage, lastRound + 1, swissSeeds(matches), matches, date != null ? date : LocalDate.now());
        if (next.isEmpty()) {
            throw new IllegalArgumentException("La phase suisse est terminee : toutes les equipes sont fixees");
        }
        saveLinked(next);
        extendCompetitionDates(stage.getCompetition(), next);
        return toDto(stage);
    }

    /** Supprime la phase et tous ses matchs. */
    public void delete(Long stageId) {
        CompetitionStage stage = findStage(stageId);
        List<Match> matches = matchRepository.findByStageId(stageId);
        // Les liens de bracket pointent vers des matchs de la meme phase : on les coupe d'abord.
        matches.forEach(m -> {
            m.setNextMatch(null);
            m.setLoserNextMatch(null);
        });
        matchRepository.saveAllAndFlush(matches);
        matchRepository.deleteAll(matches);
        stageRepository.delete(stage);
    }

    private static List<Team> swissSeeds(List<Match> matches) {
        Set<Team> seeds = new LinkedHashSet<>();
        matches.stream()
                .filter(m -> "R1".equals(m.getRoundLabel()))
                .sorted(Comparator.comparing(Match::getId))
                .forEach(m -> seeds.add(m.getTeam1()));
        matches.stream()
                .filter(m -> "R1".equals(m.getRoundLabel()))
                .sorted(Comparator.comparing(Match::getId))
                .forEach(m -> seeds.add(m.getTeam2()));
        return new ArrayList<>(seeds);
    }

    private void validate(StageCreateDto dto, List<List<Team>> groups, List<Team> allTeams) {
        if (new HashSet<>(allTeams).size() != allTeams.size()) {
            throw new IllegalArgumentException("Une equipe ne peut apparaitre qu'une fois dans la phase");
        }
        boolean isGroupStage =
                dto.format() == StageFormat.ROUND_ROBIN || dto.format() == StageFormat.DOUBLE_ROUND_ROBIN;
        if (!isGroupStage && groups.size() > 1) {
            throw new IllegalArgumentException("Seules les poules peuvent avoir plusieurs groupes");
        }
        if (groups.stream().anyMatch(g -> g.size() < 2)) {
            throw new IllegalArgumentException("Il faut au moins 2 equipes par groupe");
        }
        int n = allTeams.size();
        boolean powerOfTwo = Integer.bitCount(n) == 1;
        if (dto.format() == StageFormat.SWISS && (n % 2 == 1 || n < 4)) {
            throw new IllegalArgumentException("Une ronde suisse demande un nombre pair d'equipes (au moins 4)");
        }
        if (dto.format() == StageFormat.DOUBLE_ELIMINATION && (!powerOfTwo || n < 4)) {
            throw new IllegalArgumentException("La double elimination demande 4, 8, 16 ou 32 equipes");
        }
    }

    private List<List<Team>> resolveTeams(List<List<Long>> groups) {
        Set<Long> ids = groups.stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, Team> byId =
                teamRepository.findAllById(ids).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        return groups.stream()
                .map(g -> g.stream()
                        .map(id -> {
                            Team team = byId.get(id);
                            if (team == null) {
                                throw new EntityNotFoundException("Equipe introuvable : " + id);
                            }
                            return team;
                        })
                        .toList())
                .toList();
    }

    /**
     * Un bracket s'enregistre en partant de la fin : un match n'est sauve qu'apres ceux vers
     * lesquels il pointe. Sans liens (poules, swiss), l'ordre naturel conserve celui des tetes de serie.
     */
    private void saveLinked(List<Match> matches) {
        boolean linked = matches.stream().anyMatch(m -> m.getNextMatch() != null || m.getLoserNextMatch() != null);
        if (!linked) {
            matchRepository.saveAll(matches);
            return;
        }
        for (int i = matches.size() - 1; i >= 0; i--) {
            matchRepository.save(matches.get(i));
        }
    }

    private static void extendCompetitionDates(Competition competition, List<Match> matches) {
        for (Match m : matches) {
            if (competition.getStartDate() == null || m.getDate().isBefore(competition.getStartDate())) {
                competition.setStartDate(m.getDate());
            }
            if (competition.getEndDate() == null || m.getDate().isAfter(competition.getEndDate())) {
                competition.setEndDate(m.getDate());
            }
        }
    }

    private StageDto toDto(CompetitionStage stage) {
        List<Match> matches = stage.getId() != null ? matchRepository.findByStageId(stage.getId()) : List.of();
        long played = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.COMPLETED)
                .count();
        return StageDto.from(stage, matches.size(), played);
    }

    private Competition findCompetition(Long id) {
        return competitionRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Competition introuvable : " + id));
    }

    private CompetitionStage findStage(Long id) {
        return stageRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Phase introuvable : " + id));
    }
}
