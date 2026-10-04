package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Ajout ou correction d'un passage. startDate nul = arrivee inconnue ; endDate nul = passage en cours ;
 * position nulle = poste actuel du joueur (ajout) ou poste inchange (correction).
 */
public record PlayerStintCreateDto(@NotNull Long teamId, Position position, LocalDate startDate, LocalDate endDate) {
    public PlayerStintCreateDto(Long teamId, LocalDate startDate, LocalDate endDate) {
        this(teamId, null, startDate, endDate);
    }
}
