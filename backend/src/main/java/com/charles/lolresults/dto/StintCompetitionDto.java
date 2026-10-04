package com.charles.lolresults.dto;

/** Bilan d'un passage dans une competition. champion : l'equipe a gagne la grande finale. */
public record StintCompetitionDto(
        Long competitionId, String code, String name, Integer season, int wins, int losses, boolean champion) {}
