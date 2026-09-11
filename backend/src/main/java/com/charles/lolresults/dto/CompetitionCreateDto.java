package com.charles.lolresults.dto;

import com.charles.lolresults.domain.CompetitionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CompetitionCreateDto(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull CompetitionType type,
        String region,
        @NotNull Integer season
) {
}
