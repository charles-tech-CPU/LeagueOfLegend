package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Transfert a partir de la date donnee : le passage en cours se termine la veille.
 * teamId nul = le joueur quitte son equipe sans en rejoindre une autre.
 * position nulle = poste inchange ; la meme equipe avec un autre poste = role swap.
 */
public record PlayerTransferDto(Long teamId, @NotNull LocalDate date, Position position) {
    public PlayerTransferDto(Long teamId, LocalDate date) {
        this(teamId, date, null);
    }
}
