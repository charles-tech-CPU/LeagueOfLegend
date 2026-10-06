package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchGame;
import com.charles.lolresults.domain.MatchGamePlayer;
import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.Position;
import java.util.List;

/**
 * Details facultatifs d'une serie : MVP de la serie et manches saisies. games est vide
 * (et mvpPlayerId nul) pour un match dont les details ne sont pas renseignes.
 */
public record MatchDetailsDto(Long matchId, Long mvpPlayerId, String mvpPseudo, List<Game> games) {

    public record Game(Integer gameNumber, Long winnerTeamId, Long mvpPlayerId, String mvpPseudo, List<Line> players) {
        static Game from(MatchGame game) {
            return new Game(
                    game.getGameNumber(),
                    game.getWinner() != null ? game.getWinner().getId() : null,
                    idOf(game.getMvp()),
                    pseudoOf(game.getMvp()),
                    game.getPlayers().stream().map(Line::from).toList());
        }
    }

    public record Line(
            Long playerId,
            String pseudo,
            Long teamId,
            Position position,
            String champion,
            Integer kills,
            Integer deaths,
            Integer assists) {
        static Line from(MatchGamePlayer line) {
            return new Line(
                    line.getPlayer().getId(),
                    line.getPlayer().getPseudo(),
                    line.getTeam().getId(),
                    line.getPosition(),
                    line.getChampion(),
                    line.getKills(),
                    line.getDeaths(),
                    line.getAssists());
        }
    }

    public static MatchDetailsDto from(Match match, List<MatchGame> games) {
        return new MatchDetailsDto(
                match.getId(),
                idOf(match.getMvp()),
                pseudoOf(match.getMvp()),
                games.stream().map(Game::from).toList());
    }

    private static Long idOf(Player player) {
        return player != null ? player.getId() : null;
    }

    private static String pseudoOf(Player player) {
        return player != null ? player.getPseudo() : null;
    }
}
