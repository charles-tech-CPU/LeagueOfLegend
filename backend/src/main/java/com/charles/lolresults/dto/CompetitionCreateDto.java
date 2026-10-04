package com.charles.lolresults.dto;

import com.charles.lolresults.domain.CompetitionSplit;
import com.charles.lolresults.domain.CompetitionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** split nul pour un evenement ponctuel ; dates facultatives (deduites des matchs generes sinon). */
public record CompetitionCreateDto(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull CompetitionType type,
        String region,
        @NotNull Integer season,
        CompetitionSplit split,
        LocalDate startDate,
        LocalDate endDate) {}
