package com.charles.lolresults.dto;

import com.charles.lolresults.domain.CompetitionSplit;
import java.time.LocalDate;

/**
 * Competition jouee par une equipe, avec la date de son premier et de son dernier match de
 * l'equipe : sert a saisir les dates d'un passage en choisissant une competition.
 */
public record TeamCompetitionDto(
        Long competitionId,
        String code,
        String name,
        Integer season,
        CompetitionSplit split,
        LocalDate firstMatchDate,
        LocalDate lastMatchDate) {}
