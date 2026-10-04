package com.charles.lolresults.dto;

public record StandingRowDto(
        Long teamId,
        String teamCode,
        String teamName,
        boolean teamHasLogo,
        int seriesWon,
        /** Series nulles (BO2 a 1-1), toujours 0 hors BO2. */
        int seriesDrawn,
        int seriesLost,
        int gamesWon,
        int gamesLost) {}
