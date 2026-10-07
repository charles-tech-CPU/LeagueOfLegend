package com.charles.lolresults.dto;

import java.util.List;

/**
 * Statistiques calculees a la volee a partir des details de match saisis (jamais stockees,
 * meme principe que StandingRowDto/HeadToHeadCellDto). Les trois listes sont vides si aucun
 * detail n'a ete saisi dans le perimetre demande.
 */
public record StatsDto(List<ChampionStat> champions, List<MvpStat> mvps, List<KdaStat> kda) {

    public record ChampionStat(String champion, long picks, long bans, long pickAndBan) {}

    public record MvpStat(Long playerId, String pseudo, String teamCode, long seriesMvp, long gameMvp) {}

    public record KdaStat(
            Long playerId, String pseudo, String teamCode, long kills, long deaths, long assists, double ratio) {}
}
