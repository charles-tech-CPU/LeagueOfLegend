package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Position;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Saisie des details d'une serie : remplace l'ensemble des manches deja saisies. Tout est
 * facultatif : games vide et mvpPlayerId nul effacent les details du match.
 */
public record MatchDetailsUpdateDto(Long mvpPlayerId, @Valid List<Game> games) {

    public record Game(
            @NotNull @Min(1) @Max(5) Integer gameNumber,
            Long winnerTeamId,
            Long mvpPlayerId,
            @Valid List<Line> players) {}

    /** K/D/A facultatifs : le champion peut etre connu sans le score du joueur. */
    public record Line(
            @NotNull Long playerId,
            @NotNull Long teamId,
            @NotNull Position position,
            @NotBlank @Size(max = 30) String champion,
            @PositiveOrZero Integer kills,
            @PositiveOrZero Integer deaths,
            @PositiveOrZero Integer assists) {}
}
