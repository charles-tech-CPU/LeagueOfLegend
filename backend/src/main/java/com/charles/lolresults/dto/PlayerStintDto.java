package com.charles.lolresults.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Un passage du joueur et ses resultats, deduits des matchs joues (COMPLETED) par
 * l'equipe pendant la periode : on considere que le joueur a joue tous ces matchs.
 */
public record PlayerStintDto(
        Long id,
        Long teamId,
        String teamCode,
        String teamName,
        boolean teamHasLogo,
        LocalDate startDate,
        LocalDate endDate,
        boolean current,
        int wins,
        int losses,
        int gamesWon,
        int gamesLost,
        int titles,
        List<StintCompetitionDto> competitions,
        List<MatchDto> matches) {}
