package com.charles.lolresults.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebConfigTest {

    private static final String[] DEFAULTS = {"http://*:5173", "https://*.ts.net:[*]"};

    @Test
    void unSeulMotif() {
        assertThat(WebConfig.parseOrigins("http://*:8180")).containsExactly("http://*:8180");
    }

    @Test
    void plusieursMotifsSeparesParDesVirgules() {
        assertThat(WebConfig.parseOrigins("http://*:8180,https://*:8280,http://192.168.1.20:5173"))
                .containsExactly("http://*:8180", "https://*:8280", "http://192.168.1.20:5173");
    }

    @Test
    void espacesEtEntreesVidesIgnores() {
        assertThat(WebConfig.parseOrigins(" http://*:8180 , ,https://*:8280, "))
                .containsExactly("http://*:8180", "https://*:8280");
    }

    @Test
    void variableAbsenteOuVideDonneLesMotifsParDefaut() {
        assertThat(WebConfig.parseOrigins(null)).containsExactly(DEFAULTS);
        assertThat(WebConfig.parseOrigins("")).containsExactly(DEFAULTS);
        assertThat(WebConfig.parseOrigins(" , ")).containsExactly(DEFAULTS);
    }
}
