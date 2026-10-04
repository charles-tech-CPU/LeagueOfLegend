package com.charles.lolresults.web;

import com.charles.lolresults.dto.StageCreateDto;
import com.charles.lolresults.dto.StageDto;
import com.charles.lolresults.service.StageService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Phases d'une competition (format) et generation de leurs matchs. */
@RestController
@RequestMapping("/api")
public class StageController {

    private final StageService stageService;

    public StageController(StageService stageService) {
        this.stageService = stageService;
    }

    @GetMapping("/competitions/{competitionId}/stages")
    public List<StageDto> findByCompetition(@PathVariable Long competitionId) {
        return stageService.findByCompetition(competitionId);
    }

    /** Cree la phase et genere ses matchs (poules, premiere ronde suisse ou bracket complet). */
    @PostMapping("/competitions/{competitionId}/stages")
    @ResponseStatus(HttpStatus.CREATED)
    public StageDto create(@PathVariable Long competitionId, @Valid @RequestBody StageCreateDto dto) {
        return stageService.create(competitionId, dto);
    }

    /** Ronde suisse suivante, datee du jour donne (aujourd'hui par defaut). */
    @PostMapping("/stages/{id}/next-round")
    public StageDto nextSwissRound(@PathVariable Long id, @RequestParam(required = false) LocalDate date) {
        return stageService.nextSwissRound(id, date);
    }

    @DeleteMapping("/stages/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        stageService.delete(id);
    }
}
