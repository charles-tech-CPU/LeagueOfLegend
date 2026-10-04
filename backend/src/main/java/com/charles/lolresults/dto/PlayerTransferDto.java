package com.charles.lolresults.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Transfert a partir de la date donnee : le passage en cours se termine la veille.
 * teamId nul = le joueur quitte son equipe sans en rejoindre une autre.
 */
public record PlayerTransferDto(Long teamId, @NotNull LocalDate date) {}
