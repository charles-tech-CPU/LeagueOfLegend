package com.charles.lolresults.web;

import com.charles.lolresults.dto.PowerRankingRowDto;
import com.charles.lolresults.service.PowerRankingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PowerRankingController {

    private final PowerRankingService powerRankingService;

    public PowerRankingController(PowerRankingService powerRankingService) {
        this.powerRankingService = powerRankingService;
    }

    /** GET /api/power-ranking?season=2026 : classement Elo de la saison, recalcule a chaque appel. */
    @GetMapping("/api/power-ranking")
    public List<PowerRankingRowDto> powerRanking(@RequestParam int season) {
        return powerRankingService.compute(season);
    }
}
