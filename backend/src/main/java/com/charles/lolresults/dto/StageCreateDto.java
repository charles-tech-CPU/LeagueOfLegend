package com.charles.lolresults.dto;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.StageFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * Creation d'une phase et generation de ses matchs.
 *
 * groups : pour des poules, une liste d'equipes par poule ; pour un swiss ou un bracket,
 * une seule liste, dans l'ordre des tetes de serie (la premiere est la meilleure).
 * finalBestOf : format de la finale s'il differe du reste (ex: Bo3 puis finale en Bo5).
 */
public record StageCreateDto(
        @NotBlank String name,
        @NotNull StageFormat format,
        @NotNull BestOf bestOf,
        BestOf finalBestOf,
        @NotEmpty List<@NotEmpty List<Long>> groups,
        @NotNull LocalDate startDate,
        @Min(0) Integer daysBetweenRounds,
        Integer advancing) {}
