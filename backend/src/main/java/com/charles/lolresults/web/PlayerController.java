package com.charles.lolresults.web;

import com.charles.lolresults.dto.PlayerCreateDto;
import com.charles.lolresults.dto.PlayerDto;
import com.charles.lolresults.dto.PlayerStintCreateDto;
import com.charles.lolresults.dto.PlayerStintDto;
import com.charles.lolresults.dto.PlayerTransferDto;
import com.charles.lolresults.service.PlayerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Joueurs : liste complete et fiche sous /api/players, effectif d'une equipe sous
 * /api/teams/{teamId}/players, historique des equipes (passages) sous /api/players/{id}/stints.
 */
@RestController
@RequestMapping("/api")
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    /** Tous les joueurs, avec ou sans equipe, tries par pseudo. */
    @GetMapping("/players")
    public List<PlayerDto> findAll() {
        return playerService.findAll();
    }

    @GetMapping("/players/{id}")
    public PlayerDto findOne(@PathVariable Long id) {
        return playerService.findOne(id);
    }

    /** Effectif actuel, trie par poste (TOP -> SUPP) puis par pseudo. */
    @GetMapping("/teams/{teamId}/players")
    public List<PlayerDto> findByTeam(@PathVariable Long teamId) {
        return playerService.findByTeam(teamId);
    }

    @PostMapping("/teams/{teamId}/players")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerDto create(@PathVariable Long teamId, @Valid @RequestBody PlayerCreateDto dto) {
        return playerService.create(teamId, dto);
    }

    @PutMapping("/players/{id}")
    public PlayerDto update(@PathVariable Long id, @Valid @RequestBody PlayerCreateDto dto) {
        return playerService.update(id, dto);
    }

    @DeleteMapping("/players/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        playerService.delete(id);
    }

    /** Change d'equipe (ou la quitte si teamId est nul) a partir d'une date. */
    @PostMapping("/players/{id}/transfer")
    public PlayerDto transfer(@PathVariable Long id, @Valid @RequestBody PlayerTransferDto dto) {
        return playerService.transfer(id, dto);
    }

    /** Historique des equipes, avec les resultats de chaque passage. */
    @GetMapping("/players/{id}/stints")
    public List<PlayerStintDto> findStints(@PathVariable Long id) {
        return playerService.findStints(id);
    }

    @PostMapping("/players/{id}/stints")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerStintDto addStint(@PathVariable Long id, @Valid @RequestBody PlayerStintCreateDto dto) {
        return playerService.addStint(id, dto);
    }

    @PutMapping("/stints/{id}")
    public PlayerStintDto updateStint(@PathVariable Long id, @Valid @RequestBody PlayerStintCreateDto dto) {
        return playerService.updateStint(id, dto);
    }

    @DeleteMapping("/stints/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStint(@PathVariable Long id) {
        playerService.deleteStint(id);
    }
}
