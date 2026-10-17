package com.charles.lolresults.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.dto.PowerRankingRowDto;
import com.charles.lolresults.service.PowerRankingService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PowerRankingController.class)
class PowerRankingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PowerRankingService powerRankingService;

    @Test
    void powerRankingDUneSaison() throws Exception {
        when(powerRankingService.compute(2026))
                .thenReturn(List.of(new PowerRankingRowDto(
                        1,
                        2,
                        1L,
                        "G2",
                        "G2 Esports",
                        "EMEA",
                        true,
                        "LEC",
                        1500,
                        1620,
                        35,
                        8,
                        2,
                        17,
                        6,
                        List.of("W", "W", "L", "W", "W"),
                        new PowerRankingRowDto.BestWin(9L, 3L, "GEN", 1700, "3-1"),
                        LocalDate.of(2026, 10, 12))));

        mockMvc.perform(get("/api/power-ranking").param("season", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamCode").value("G2"))
                .andExpect(jsonPath("$[0].rating").value(1620))
                .andExpect(jsonPath("$[0].league").value("LEC"))
                .andExpect(jsonPath("$[0].rankChange").value(2))
                .andExpect(jsonPath("$[0].form[2]").value("L"))
                .andExpect(jsonPath("$[0].bestWin.opponentCode").value("GEN"));
    }

    @Test
    void laSaisonEstObligatoire() throws Exception {
        mockMvc.perform(get("/api/power-ranking")).andExpect(status().isBadRequest());
    }
}
