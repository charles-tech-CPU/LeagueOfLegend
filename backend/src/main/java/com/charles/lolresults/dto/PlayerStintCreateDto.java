package com.charles.lolresults.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Ajout ou correction d'un passage. startDate nul = arrivee inconnue ; endDate nul = passage en cours. */
public record PlayerStintCreateDto(@NotNull Long teamId, LocalDate startDate, LocalDate endDate) {}
