package com.charles.lolresults.web;

import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.dto.MatchCreateDto;
import com.charles.lolresults.dto.MatchDto;
import com.charles.lolresults.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    /** GET /api/matches?competitionId=1  ou  GET /api/matches?teamId=3  ou  GET /api/matches?status=SCHEDULED */
    @GetMapping
    public List<MatchDto> find(@RequestParam(required = false) Long competitionId,
                                @RequestParam(required = false) Long teamId,
                                @RequestParam(required = false) MatchStatus status) {
        if (competitionId != null) {
            return matchService.findByCompetition(competitionId);
        }
        if (teamId != null) {
            return matchService.findByTeam(teamId);
        }
        if (status != null) {
            return matchService.findByStatus(status);
        }
        throw new IllegalArgumentException("Precise competitionId, teamId ou status en parametre de requete");
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MatchDto create(@Valid @RequestBody MatchCreateDto dto) {
        return matchService.create(dto);
    }

    @PutMapping("/{id}")
    public MatchDto update(@PathVariable Long id, @Valid @RequestBody MatchCreateDto dto) {
        return matchService.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        matchService.delete(id);
    }
}
