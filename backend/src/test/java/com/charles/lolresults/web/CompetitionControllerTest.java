package com.charles.lolresults.web;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.repository.CompetitionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CompetitionController.class)
class CompetitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompetitionRepository competitionRepository;

    @Test
    void listeTrieeParSaisonDecroissantePuisCode() throws Exception {
        when(competitionRepository.findAll())
                .thenReturn(List.of(
                        competition(1L, "LEC", 2025), competition(2L, "LCS", 2026), competition(3L, "LCK", 2026)));

        mockMvc.perform(get("/api/competitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("LCK", "LCS", "LEC")));
    }

    @Test
    void findOneRenvoieLaCompetition() throws Exception {
        when(competitionRepository.findById(1L)).thenReturn(Optional.of(competition(1L, "LEC", 2026)));

        mockMvc.perform(get("/api/competitions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("LEC"))
                .andExpect(jsonPath("$.type").value("REGIONAL_LEAGUE"));
    }

    @Test
    void competitionInconnueRenvoie404AvecMessage() throws Exception {
        when(competitionRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/competitions/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Competition introuvable : 99"));
    }

    @Test
    void createEnregistreLaCompetition() throws Exception {
        when(competitionRepository.save(any(Competition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(
                        post("/api/competitions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"code\":\"MSI\",\"name\":\"MSI 2026\",\"type\":\"INTERNATIONAL_EVENT\",\"season\":2026}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("MSI 2026"))
                .andExpect(jsonPath("$.type").value("INTERNATIONAL_EVENT"));
    }

    @Test
    void createRefuseUneCompetitionIncomplete() throws Exception {
        mockMvc.perform(post("/api/competitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSupprimeLaCompetition() throws Exception {
        mockMvc.perform(delete("/api/competitions/1")).andExpect(status().isNoContent());

        verify(competitionRepository).deleteById(1L);
    }

    @Test
    void leServeurViteEstAutoriseParCors() throws Exception {
        mockMvc.perform(options("/api/competitions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void uneAutreOrigineEstRefuseeParCors() throws Exception {
        mockMvc.perform(options("/api/competitions")
                        .header("Origin", "http://autre-site.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    private static Competition competition(Long id, String code, int season) {
        Competition competition =
                new Competition(code, code + " " + season, CompetitionType.REGIONAL_LEAGUE, "EMEA", season);
        competition.setId(id);
        return competition;
    }
}
