package com.charles.lolresults.dto;

public record StandingRowDto(
        Long teamId,
        String teamCode,
        String teamName,
        boolean teamHasLogo,
        int seriesWon,
        int seriesLost,
        int gamesWon,
        int gamesLost
) {
}
