package com.charles.lolresults.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.domain.Position;
import com.charles.lolresults.dto.PlayerStatsDto;
import com.charles.lolresults.dto.StatsDto;
import com.charles.lolresults.service.PlayerStatsService;
import com.charles.lolresults.service.StatsService;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StatsController.class)
class StatsControllerTest {

    private static final StatsDto EMPTY = new StatsDto(
            new StatsDto.Summary(0, 0, 0, 0, 0),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new StatsDto.Records(List.of(), List.of(), List.of()));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatsService statsService;

    @MockBean
    private PlayerStatsService playerStatsService;

    @Test
    void statsDUneCompetition() throws Exception {
        StatsDto.ChampionPick ahri = new StatsDto.ChampionPick("Ahri", 3);
        when(statsService.compute(1L, null))
                .thenReturn(new StatsDto(
                        new StatsDto.Summary(4, 50, 12.5, 10, 14),
                        List.of(new StatsDto.ChampionStat(
                                "Ahri", 3, 1, 4, Map.of(Position.MID, 3L), 2, 1, 15, 3, 20, 11.67)),
                        List.of(new StatsDto.MvpStat(10L, "Caps", 1L, "G2", true, Position.MID, 2, 1)),
                        List.of(new StatsDto.KdaStat(
                                10L,
                                "Caps",
                                1L,
                                "G2",
                                true,
                                Position.MID,
                                4,
                                20,
                                5,
                                30,
                                10.0,
                                3,
                                1,
                                0.75,
                                1,
                                2,
                                List.of(ahri))),
                        List.of(new StatsDto.PositionStat(Position.MID, 4, 20, 5, 30, 10.0, List.of(ahri))),
                        List.of(new StatsDto.TeamStat(1L, "G2", "G2 Esports", true, 4, 3, 1, 50, 20, 12.5, ahri, null)),
                        new StatsDto.Records(List.of(), List.of(), List.of())));

        mockMvc.perform(get("/api/stats").param("competitionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.games").value(4))
                .andExpect(jsonPath("$.champions[0].champion").value("Ahri"))
                .andExpect(jsonPath("$.champions[0].wins").value(2))
                .andExpect(jsonPath("$.mvps[0].pseudo").value("Caps"))
                .andExpect(jsonPath("$.mvps[0].teamCode").value("G2"))
                .andExpect(jsonPath("$.kda[0].ratio").value(10.0))
                .andExpect(jsonPath("$.kda[0].position").value("MID"))
                .andExpect(jsonPath("$.kda[0].killParticipation").value(0.75))
                .andExpect(jsonPath("$.kda[0].topChampions[0].champion").value("Ahri"))
                .andExpect(jsonPath("$.champions[0].picksByPosition.MID").value(3))
                .andExpect(jsonPath("$.positions[0].topChampions[0].champion").value("Ahri"))
                .andExpect(jsonPath("$.teams[0].mostPicked.champion").value("Ahri"));
    }

    @Test
    void statsDUneSaison() throws Exception {
        when(statsService.compute(null, 2024)).thenReturn(EMPTY);

        mockMvc.perform(get("/api/stats").param("season", "2024")).andExpect(status().isOk());
    }

    @Test
    void statsSansParametreCouvreToutLHistorique() throws Exception {
        when(statsService.compute(null, null)).thenReturn(EMPTY);

        mockMvc.perform(get("/api/stats")).andExpect(status().isOk());
    }

    @Test
    void statsDUnJoueur() throws Exception {
        PlayerStatsDto.Tally tally = new PlayerStatsDto.Tally(5, 3, 2, 5, 20, 5, 30, 10.0, 0.7, 1, 2, 3);
        when(playerStatsService.compute(10L))
                .thenReturn(new PlayerStatsDto(
                        tally,
                        1,
                        List.of(new PlayerStatsDto.CompetitionLine(
                                1L, "LEC", "LEC 2024", 2024, 1L, "G2", true, tally, 1, List.of())),
                        List.of(new PlayerStatsDto.SeasonLine(2024, tally, 1, List.of())),
                        List.of(new PlayerStatsDto.ChampionLine("Azir", tally)),
                        List.of(new PlayerStatsDto.PositionLine(Position.MID, tally)),
                        List.of(),
                        List.of(),
                        List.of()));

        mockMvc.perform(get("/api/players/10/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.career.ratio").value(10.0))
                .andExpect(jsonPath("$.competitions[0].code").value("LEC"))
                .andExpect(jsonPath("$.competitions[0].stats.kills").value(20))
                .andExpect(jsonPath("$.seasons[0].season").value(2024))
                .andExpect(jsonPath("$.champions[0].champion").value("Azir"));
    }

    @Test
    void statsDUnJoueurInconnu() throws Exception {
        when(playerStatsService.compute(99L)).thenThrow(new EntityNotFoundException("Joueur introuvable : 99"));

        mockMvc.perform(get("/api/players/99/stats")).andExpect(status().isNotFound());
    }
}
