package com.charles.lolresults.web;

import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.dto.MatchCreateDto;
import com.charles.lolresults.dto.MatchDetailsDto;
import com.charles.lolresults.dto.MatchDetailsUpdateDto;
import com.charles.lolresults.dto.MatchDto;
import com.charles.lolresults.service.MatchDetailsService;
import com.charles.lolresults.service.MatchService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;
    private final MatchDetailsService matchDetailsService;

    public MatchController(MatchService matchService, MatchDetailsService matchDetailsService) {
        this.matchService = matchService;
        this.matchDetailsService = matchDetailsService;
    }

    /**
     * GET /api/matches?competitionId=1  ou  ?teamId=3  ou  ?status=SCHEDULED (&from=2026-01-01
     * pour ignorer les matchs plus anciens).
     */
    @GetMapping
    public List<MatchDto> find(
            @RequestParam(required = false) Long competitionId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) LocalDate from) {
        if (competitionId != null) {
            return matchService.findByCompetition(competitionId);
        }
        if (teamId != null) {
            return matchService.findByTeam(teamId);
        }
        if (status != null) {
            return matchService.findByStatus(status, from);
        }
        throw new IllegalArgumentException("Precise competitionId, teamId ou status en parametre de requete");
    }

    @GetMapping("/{id}")
    public MatchDto findOne(@PathVariable Long id) {
        return matchService.findOne(id);
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

    /** Details facultatifs de la serie : manches, champions, K/D/A, MVP (vides si non saisis). */
    @GetMapping("/{id}/details")
    public MatchDetailsDto details(@PathVariable Long id) {
        return matchDetailsService.find(id);
    }

    /** Remplace tous les details de la serie (aucune manche et MVP nul = details effaces). */
    @PutMapping("/{id}/details")
    public MatchDetailsDto replaceDetails(@PathVariable Long id, @Valid @RequestBody MatchDetailsUpdateDto dto) {
        return matchDetailsService.replace(id, dto);
    }
}
