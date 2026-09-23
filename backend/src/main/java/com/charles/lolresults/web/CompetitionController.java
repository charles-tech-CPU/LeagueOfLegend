package com.charles.lolresults.web;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.dto.CompetitionCreateDto;
import com.charles.lolresults.dto.CompetitionDto;
import com.charles.lolresults.repository.CompetitionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/competitions")
public class CompetitionController {

    private final CompetitionRepository competitionRepository;

    public CompetitionController(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }

    @GetMapping
    public List<CompetitionDto> findAll() {
        return competitionRepository.findAll().stream()
                .sorted(Comparator.comparing(Competition::getSeason).reversed().thenComparing(Competition::getCode))
                .map(CompetitionDto::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CompetitionDto findOne(@PathVariable Long id) {
        return competitionRepository
                .findById(id)
                .map(CompetitionDto::from)
                .orElseThrow(() -> new EntityNotFoundException("Competition introuvable : " + id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompetitionDto create(@Valid @RequestBody CompetitionCreateDto dto) {
        Competition competition = new Competition(dto.code(), dto.name(), dto.type(), dto.region(), dto.season());
        return CompetitionDto.from(competitionRepository.save(competition));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        competitionRepository.deleteById(id);
    }
}
