package com.charles.lolresults.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.charles.lolresults.repository.CompetitionRepository;
import com.charles.lolresults.repository.MatchRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** CORS_ALLOWED_ORIGINS avec plusieurs motifs : chacun est autorise, le reste refuse. */
@WebMvcTest(CompetitionController.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://*:8180, https://*:8280")
class CorsAllowedOriginsListTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompetitionRepository competitionRepository;

    @MockBean
    private MatchRepository matchRepository;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://192.168.1.20:8180",
                "http://localhost:8180",
                "https://serveur-foyer.tail0af124.ts.net:8280",
                "https://localhost:8280"
            })
    void chaqueMotifDeLaListeEstAutorise(String origin) throws Exception {
        mockMvc.perform(options("/api/competitions")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", origin));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // port d'un motif mais mauvais protocole
                "https://192.168.1.20:8180",
                "http://serveur-foyer.tail0af124.ts.net:8280",
                // motifs par defaut, remplaces par la liste
                "http://localhost:5173",
                "https://serveur-foyer.tail0af124.ts.net:8443"
            })
    void uneOrigineHorsDeLaListeEstRefusee(String origin) throws Exception {
        mockMvc.perform(options("/api/competitions")
                        .header("Origin", origin)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
