package com.charles.lolresults.dto;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.MatchPhase;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO de creation/mise a jour d'un match. score1/score2 peuvent etre nuls :
 * un match sans score est un match "a venir" (calendrier), qu'on completera
 * plus tard via PUT /api/matches/{id}.
 *
 * team1Id/team2Id peuvent aussi etre nuls pour un match de playoffs pas
 * encore determine (en attente du vainqueur d'un match precedent du
 * bracket) : voir nextMatchId/nextMatchSlot sur le match precedent.
 */
public record MatchCreateDto(
        @NotNull Long competitionId,
        Long groupId,
        @NotNull String roundLabel,
        @NotNull LocalDate date,
        LocalTime time,
        @NotNull BestOf bestOf,
        Long team1Id,
        Long team2Id,
        Integer score1,
        Integer score2,
        MatchPhase phase,
        String bracketSide,
        Long nextMatchId,
        Integer nextMatchSlot,
        Long loserNextMatchId,
        Integer loserNextMatchSlot
) {
}
