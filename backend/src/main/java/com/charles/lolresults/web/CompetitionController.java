package com.charles.lolresults.web;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.dto.CompetitionCreateDto;
import com.charles.lolresults.dto.CompetitionDto;
import com.charles.lolresults.dto.SeasonDto;
import com.charles.lolresults.dto.TeamCompetitionDto;
import com.charles.lolresults.repository.CompetitionRepository;
import com.charles.lolresults.repository.MatchRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

    /** Saison la plus recente d'abord, puis split dans l'ordre de l'annee (evenements ponctuels a la fin), puis code. */
    private static final Comparator<Competition> DISPLAY_ORDER = Comparator.comparing(Competition::getSeason)
            .reversed()
            .thenComparing(Competition::getSplit, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Competition::getCode);

    private final CompetitionRepository competitionRepository;
    private final MatchRepository matchRepository;

    public CompetitionController(CompetitionRepository competitionRepository, MatchRepository matchRepository) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
    }

    /**
     * GET /api/competitions (toutes) ou GET /api/competitions?season=2013 : avec season,
     * chaque competition porte aussi son avancement (nombre de matchs et de matchs joues).
     */
    @GetMapping
    public List<CompetitionDto> findAll(@RequestParam(required = false) Integer season) {
        if (season == null) {
            return competitionRepository.findAll().stream()
                    .sorted(DISPLAY_ORDER)
                    .map(CompetitionDto::from)
                    .toList();
        }
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : matchRepository.countBySeason(season, MatchStatus.COMPLETED)) {
            counts.put((Long) row[0], new long[] {((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return competitionRepository.findBySeason(season).stream()
                .sorted(DISPLAY_ORDER)
                .map(c -> {
                    long[] count = counts.getOrDefault(c.getId(), new long[2]);
                    return CompetitionDto.from(c, count[0], count[1]);
                })
                .toList();
    }

    /** Saisons disponibles, de la plus recente a la plus ancienne. */
    @GetMapping("/seasons")
    public List<SeasonDto> seasons() {
        return competitionRepository.countBySeason().stream()
                .map(row -> new SeasonDto((Integer) row[0], ((Number) row[1]).longValue()))
                .toList();
    }

    /**
     * Competitions jouees par une equipe, de la plus recente a la plus ancienne, avec les dates
     * de son premier et de son dernier match : pour saisir un passage sans chercher les dates.
     */
    @GetMapping("/played-by/{teamId}")
    public List<TeamCompetitionDto> playedBy(@PathVariable Long teamId) {
        Map<Long, LocalDate[]> spans = new HashMap<>();
        for (Object[] row : matchRepository.competitionSpansByTeam(teamId)) {
            spans.put((Long) row[0], new LocalDate[] {(LocalDate) row[1], (LocalDate) row[2]});
        }
        return competitionRepository.findAllById(spans.keySet()).stream()
                .map(c -> {
                    LocalDate[] span = spans.get(c.getId());
                    return new TeamCompetitionDto(
                            c.getId(), c.getCode(), c.getName(), c.getSeason(), c.getSplit(), span[0], span[1]);
                })
                .sorted(Comparator.comparing(TeamCompetitionDto::firstMatchDate, Comparator.reverseOrder())
                        .thenComparing(TeamCompetitionDto::code))
                .toList();
    }

    @GetMapping("/{id}")
    public CompetitionDto findOne(@PathVariable Long id) {
        return CompetitionDto.from(findCompetition(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompetitionDto create(@Valid @RequestBody CompetitionCreateDto dto) {
        Competition competition = new Competition(dto.code(), dto.name(), dto.type(), dto.region(), dto.season());
        applyCalendar(competition, dto);
        return CompetitionDto.from(competitionRepository.save(competition));
    }

    @PutMapping("/{id}")
    public CompetitionDto update(@PathVariable Long id, @Valid @RequestBody CompetitionCreateDto dto) {
        Competition competition = findCompetition(id);
        competition.setCode(dto.code());
        competition.setName(dto.name());
        competition.setType(dto.type());
        competition.setRegion(dto.region());
        competition.setSeason(dto.season());
        applyCalendar(competition, dto);
        return CompetitionDto.from(competitionRepository.save(competition));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        competitionRepository.deleteById(id);
    }

    private static void applyCalendar(Competition competition, CompetitionCreateDto dto) {
        competition.setSplit(dto.split());
        if (dto.startDate() != null && dto.endDate() != null && dto.endDate().isBefore(dto.startDate())) {
            throw new IllegalArgumentException("La date de fin doit etre posterieure a la date de debut");
        }
        competition.setStartDate(dto.startDate());
        competition.setEndDate(dto.endDate());
    }

    private Competition findCompetition(Long id) {
        return competitionRepository
                .findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Competition introuvable : " + id));
    }
}
