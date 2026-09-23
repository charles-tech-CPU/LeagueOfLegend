package com.charles.lolresults.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.dto.MatchCreateDto;
import com.charles.lolresults.dto.MatchDto;
import com.charles.lolresults.service.MatchService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    private static final String MATCH_JSON =
            "{\"competitionId\":1,\"roundLabel\":\"W1\",\"date\":\"2026-09-24\",\"bestOf\":\"BO3\",\"team1Id\":1,\"team2Id\":2}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchService matchService;

    @Test
    void rechercheParCompetition() throws Exception {
        when(matchService.findByCompetition(1L)).thenReturn(List.of(sampleDto()));

        mockMvc.perform(get("/api/matches").param("competitionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roundLabel").value("W1"))
                .andExpect(jsonPath("$[0].competitionCode").value("LEC"));
    }

    @Test
    void rechercheParEquipe() throws Exception {
        when(matchService.findByTeam(3L)).thenReturn(List.of(sampleDto()));

        mockMvc.perform(get("/api/matches").param("teamId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void rechercheParStatut() throws Exception {
        when(matchService.findByStatus(MatchStatus.SCHEDULED)).thenReturn(List.of());

        mockMvc.perform(get("/api/matches").param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rechercheSansCritereRenvoie400() throws Exception {
        mockMvc.perform(get("/api/matches"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.error").value("Precise competitionId, teamId ou status en parametre de requete"));
    }

    @Test
    void createDelegueAuService() throws Exception {
        when(matchService.create(any(MatchCreateDto.class))).thenReturn(sampleDto());

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MATCH_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bestOf").value("BO3"));
    }

    @Test
    void updateDelegueAuService() throws Exception {
        when(matchService.update(eq(5L), any(MatchCreateDto.class))).thenReturn(sampleDto());

        mockMvc.perform(put("/api/matches/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MATCH_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void deleteDelegueAuService() throws Exception {
        mockMvc.perform(delete("/api/matches/5")).andExpect(status().isNoContent());

        verify(matchService).delete(5L);
    }

    private static MatchDto sampleDto() {
        Competition competition = new Competition();
        competition.setId(1L);
        competition.setCode("LEC");
        Match match = new Match();
        match.setId(5L);
        match.setCompetition(competition);
        match.setRoundLabel("W1");
        match.setDate(LocalDate.of(2026, 9, 24));
        match.setBestOf(BestOf.BO3);
        return MatchDto.from(match);
    }
}
