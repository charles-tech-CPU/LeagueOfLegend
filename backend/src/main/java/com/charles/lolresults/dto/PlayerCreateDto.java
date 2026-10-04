package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** nationality : code pays ISO 3166-1 alpha-2 (ex: "FR", "KR"), facultatif. */
public record PlayerCreateDto(
        @NotBlank @Size(max = 50) String pseudo,
        @Pattern(regexp = NATIONALITY_PATTERN) String nationality,
        @NotNull Position position) {
    /** Deux lettres, ou vide/nul si non renseignee. */
    public static final String NATIONALITY_PATTERN = "^([A-Za-z]{2})?$";
}
