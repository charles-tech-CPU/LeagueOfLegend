package com.charles.lolresults.dto;

/**
 * Une case de la matrice tete-a-tete : le bilan de teamA contre teamB
 * (seriesWon-seriesLost du point de vue de teamA). Absent de la liste
 * si les deux equipes ne se sont pas encore affrontees.
 */
public record HeadToHeadCellDto(
        Long teamAId,
        Long teamBId,
        int seriesWon,
        int seriesLost
) {
}
