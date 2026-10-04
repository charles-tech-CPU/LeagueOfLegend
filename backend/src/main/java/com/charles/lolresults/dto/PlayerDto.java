package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Player;
import com.charles.lolresults.domain.Position;
import com.charles.lolresults.domain.Team;

/** teamId, teamCode et teamName sont nuls pour un joueur sans equipe. */
public record PlayerDto(
        Long id, Long teamId, String teamCode, String teamName, String pseudo, String nationality, Position position) {
    public static PlayerDto from(Player player) {
        Team team = player.getTeam();
        return new PlayerDto(
                player.getId(),
                team != null ? team.getId() : null,
                team != null ? team.getCode() : null,
                team != null ? team.getName() : null,
                player.getPseudo(),
                player.getNationality(),
                player.getPosition());
    }
}
