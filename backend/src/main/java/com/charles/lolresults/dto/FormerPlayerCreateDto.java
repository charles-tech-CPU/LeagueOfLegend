package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Ancien joueur d'une equipe, cree avec un passage termine. startDate nul = arrivee inconnue ;
 * endDate obligatoire (pour un joueur actuel, PlayerCreateDto).
 */
public record FormerPlayerCreateDto(
        @NotBlank @Size(max = 50) String pseudo,

        @Pattern(regexp = PlayerCreateDto.NATIONALITY_PATTERN)
        String nationality,

        @NotNull Position position,
        LocalDate startDate,
        @NotNull LocalDate endDate) {}
