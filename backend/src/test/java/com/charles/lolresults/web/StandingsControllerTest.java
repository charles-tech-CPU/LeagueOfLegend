package com.charles.lolresults.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.dto.HeadToHeadCellDto;
import com.charles.lolresults.dto.StandingRowDto;
import com.charles.lolresults.service.StandingsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StandingsController.class)
class StandingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StandingsService standingsService;

    @Test
    void classementDUnGroupe() throws Exception {
        when(standingsService.computeStandings(1L, 2L))
                .thenReturn(List.of(new StandingRowDto(7L, "G2", "G2 Esports", false, 3, 1, 7, 3)));

        mockMvc.perform(get("/api/standings").param("competitionId", "1").param("groupId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamCode").value("G2"))
                .andExpect(jsonPath("$[0].seriesWon").value(3));
    }

    @Test
    void teteATeteSansGroupe() throws Exception {
        when(standingsService.computeHeadToHead(1L, null)).thenReturn(List.of(new HeadToHeadCellDto(7L, 8L, 2, 0)));

        mockMvc.perform(get("/api/head-to-head").param("competitionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamBId").value(8));
    }

    @Test
    void competitionObligatoire() throws Exception {
        mockMvc.perform(get("/api/standings")).andExpect(status().isBadRequest());
    }
}
