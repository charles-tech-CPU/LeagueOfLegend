package com.charles.lolresults.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.domain.Position;
import com.charles.lolresults.dto.StatsDto;
import com.charles.lolresults.service.StatsService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatsService statsService;

    @Test
    void statsDUneCompetition() throws Exception {
        when(statsService.compute(1L, null))
                .thenReturn(new StatsDto(
                        List.of(new StatsDto.ChampionStat("Ahri", 3, 1, 4, Map.of(Position.MID, 3L))),
                        List.of(new StatsDto.MvpStat(10L, "Caps", 1L, "G2", true, Position.MID, 2, 1)),
                        List.of(new StatsDto.KdaStat(10L, "Caps", 1L, "G2", true, Position.MID, 4, 20, 5, 30, 10.0)),
                        List.of(new StatsDto.PositionStat(
                                Position.MID, 4, 20, 5, 30, 10.0, List.of(new StatsDto.ChampionPick("Ahri", 3))))));

        mockMvc.perform(get("/api/stats").param("competitionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.champions[0].champion").value("Ahri"))
                .andExpect(jsonPath("$.mvps[0].pseudo").value("Caps"))
                .andExpect(jsonPath("$.mvps[0].teamCode").value("G2"))
                .andExpect(jsonPath("$.kda[0].ratio").value(10.0))
                .andExpect(jsonPath("$.kda[0].position").value("MID"))
                .andExpect(jsonPath("$.champions[0].picksByPosition.MID").value(3))
                .andExpect(jsonPath("$.positions[0].topChampions[0].champion").value("Ahri"));
    }

    @Test
    void statsDUneSaison() throws Exception {
        when(statsService.compute(null, 2024)).thenReturn(new StatsDto(List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/stats").param("season", "2024")).andExpect(status().isOk());
    }

    @Test
    void statsSansParametreCouvreToutLHistorique() throws Exception {
        when(statsService.compute(null, null)).thenReturn(new StatsDto(List.of(), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/stats")).andExpect(status().isOk());
    }
}
