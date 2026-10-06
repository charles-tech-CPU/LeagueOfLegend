package com.charles.lolresults.web;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionSplit;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.repository.CompetitionRepository;
import com.charles.lolresults.repository.MatchRepository;
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

    @MockBean
    private MatchRepository matchRepository;

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
    void uneSaisonEstRangeeParSplitPuisEvenementsAvecSonAvancement() throws Exception {
        Competition summer = competition(1L, "LCS", 2013);
        summer.setSplit(CompetitionSplit.SUMMER);
        Competition spring = competition(2L, "LCS", 2013);
        spring.setName("LCS 2013 Spring");
        spring.setSplit(CompetitionSplit.SPRING);
        Competition worlds = competition(3L, "WORLDS", 2013);
        when(competitionRepository.findBySeason(2013)).thenReturn(List.of(worlds, summer, spring));
        when(matchRepository.countBySeason(2013, MatchStatus.COMPLETED))
                .thenReturn(List.<Object[]>of(new Object[] {2L, 56L, 10L}));

        mockMvc.perform(get("/api/competitions").param("season", "2013"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(2, 1, 3)))
                .andExpect(jsonPath("$[0].matchCount").value(56))
                .andExpect(jsonPath("$[0].playedCount").value(10))
                .andExpect(jsonPath("$[1].matchCount").value(0));
    }

    @Test
    void lesSaisonsSontListeesAvecLeurNombreDeCompetitions() throws Exception {
        when(competitionRepository.countBySeason())
                .thenReturn(List.<Object[]>of(new Object[] {2026, 11L}, new Object[] {2013, 20L}));

        mockMvc.perform(get("/api/competitions/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].season", contains(2026, 2013)))
                .andExpect(jsonPath("$[1].competitionCount").value(20));
    }

    @Test
    void updateModifieLeSplitEtLesDates() throws Exception {
        Competition lec = competition(1L, "LEC", 2026);
        when(competitionRepository.findById(1L)).thenReturn(Optional.of(lec));
        when(competitionRepository.save(any(Competition.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(put("/api/competitions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LEC\",\"name\":\"LEC 2026 Summer\",\"type\":\"REGIONAL_LEAGUE\","
                                + "\"season\":2026,\"split\":\"SUMMER\",\"startDate\":\"2026-07-24\","
                                + "\"endDate\":\"2026-09-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.split").value("SUMMER"))
                .andExpect(jsonPath("$.startDate").value("2026-07-24"));
    }

    @Test
    void desDatesInverseesSontRefusees() throws Exception {
        when(competitionRepository.findById(1L)).thenReturn(Optional.of(competition(1L, "LEC", 2026)));

        mockMvc.perform(put("/api/competitions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"LEC\",\"name\":\"LEC\",\"type\":\"REGIONAL_LEAGUE\",\"season\":2026,"
                                + "\"startDate\":\"2026-09-20\",\"endDate\":\"2026-07-24\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La date de fin doit etre posterieure a la date de debut"));
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
    void leServeurViteViaUneIpDuReseauEstAutoriseParCors() throws Exception {
        mockMvc.perform(options("/api/competitions")
                        .header("Origin", "http://192.168.1.20:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.1.20:5173"));
    }

    @Test
    void lAccesHttpsViaTailscaleEstAutoriseParCors() throws Exception {
        mockMvc.perform(options("/api/competitions")
                        .header("Origin", "https://serveur-foyer.tail0af124.ts.net:8443")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Access-Control-Allow-Origin", "https://serveur-foyer.tail0af124.ts.net:8443"));
    }

    @Test
    void unAutreSiteHttpsEstRefuseParCors() throws Exception {
        mockMvc.perform(options("/api/competitions")
                        .header("Origin", "https://autre-site.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
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
