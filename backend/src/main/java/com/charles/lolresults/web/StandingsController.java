package com.charles.lolresults.web;

import com.charles.lolresults.dto.HeadToHeadCellDto;
import com.charles.lolresults.dto.StandingRowDto;
import com.charles.lolresults.service.StandingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StandingsController {

    private final StandingsService standingsService;

    public StandingsController(StandingsService standingsService) {
        this.standingsService = standingsService;
    }

    /** GET /api/standings?competitionId=1&groupId=2 (groupId optionnel) */
    @GetMapping("/api/standings")
    public List<StandingRowDto> standings(@RequestParam Long competitionId,
                                           @RequestParam(required = false) Long groupId) {
        return standingsService.computeStandings(competitionId, groupId);
    }

    /** GET /api/head-to-head?competitionId=1&groupId=2 (groupId optionnel) */
    @GetMapping("/api/head-to-head")
    public List<HeadToHeadCellDto> headToHead(@RequestParam Long competitionId,
                                               @RequestParam(required = false) Long groupId) {
        return standingsService.computeHeadToHead(competitionId, groupId);
    }
}
