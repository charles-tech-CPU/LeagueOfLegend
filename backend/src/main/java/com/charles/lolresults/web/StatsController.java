package com.charles.lolresults.web;

import com.charles.lolresults.dto.PlayerStatsDto;
import com.charles.lolresults.dto.StatsDto;
import com.charles.lolresults.service.PlayerStatsService;
import com.charles.lolresults.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final StatsService statsService;
    private final PlayerStatsService playerStatsService;

    public StatsController(StatsService statsService, PlayerStatsService playerStatsService) {
        this.statsService = statsService;
        this.playerStatsService = playerStatsService;
    }

    /**
     * GET /api/stats?competitionId=1 (une competition), ?season=2024 (une saison, toutes
     * competitions confondues), ou sans parametre (tout l'historique).
     */
    @GetMapping("/api/stats")
    public StatsDto stats(
            @RequestParam(required = false) Long competitionId, @RequestParam(required = false) Integer season) {
        return statsService.compute(competitionId, season);
    }

    /** Stats de la fiche joueur : carriere, par competition, par saison, par champion... */
    @GetMapping("/api/players/{id}/stats")
    public PlayerStatsDto playerStats(@PathVariable Long id) {
        return playerStatsService.compute(id);
    }
}
