package com.charles.lolresults.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.charles.lolresults.domain.Position;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Les records avec un constructeur secondaire doivent etre lus via leur constructeur canonique. */
class RoleSwapDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void leTransfertLitLePosteFacultatif() throws Exception {
        assertThat(mapper.readValue(
                        "{\"teamId\":1,\"date\":\"2025-07-01\",\"position\":\"ADC\"}", PlayerTransferDto.class))
                .isEqualTo(new PlayerTransferDto(1L, LocalDate.of(2025, 7, 1), Position.ADC));
        assertThat(mapper.readValue("{\"teamId\":null,\"date\":\"2025-07-01\"}", PlayerTransferDto.class))
                .isEqualTo(new PlayerTransferDto(null, LocalDate.of(2025, 7, 1)));
    }

    @Test
    void lePassageLitLePosteFacultatif() throws Exception {
        assertThat(mapper.readValue(
                        "{\"teamId\":1,\"position\":\"MID\",\"startDate\":null,\"endDate\":\"2025-06-30\"}",
                        PlayerStintCreateDto.class))
                .isEqualTo(new PlayerStintCreateDto(1L, Position.MID, null, LocalDate.of(2025, 6, 30)));
    }
}
