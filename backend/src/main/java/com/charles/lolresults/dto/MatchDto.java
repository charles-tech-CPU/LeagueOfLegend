package com.charles.lolresults.dto;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchPhase;
import com.charles.lolresults.domain.MatchStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record MatchDto(
        Long id,
        Long competitionId,
        String competitionCode,
        Long groupId,
        String groupName,
        String roundLabel,
        LocalDate date,
        LocalTime time,
        BestOf bestOf,
        Long team1Id,
        String team1Code,
        String team1Name,
        Long team2Id,
        String team2Code,
        String team2Name,
        Integer score1,
        Integer score2,
        MatchStatus status,
        MatchPhase phase,
        String bracketSide,
        Long nextMatchId,
        Integer nextMatchSlot,
        Long loserNextMatchId,
        Integer loserNextMatchSlot) {
    public static MatchDto from(Match m) {
        return new MatchDto(
                m.getId(),
                m.getCompetition().getId(),
                m.getCompetition().getCode(),
                m.getGroup() != null ? m.getGroup().getId() : null,
                m.getGroup() != null ? m.getGroup().getName() : null,
                m.getRoundLabel(),
                m.getDate(),
                m.getTime(),
                m.getBestOf(),
                m.getTeam1() != null ? m.getTeam1().getId() : null,
                m.getTeam1() != null ? m.getTeam1().getCode() : null,
                m.getTeam1() != null ? m.getTeam1().getName() : null,
                m.getTeam2() != null ? m.getTeam2().getId() : null,
                m.getTeam2() != null ? m.getTeam2().getCode() : null,
                m.getTeam2() != null ? m.getTeam2().getName() : null,
                m.getScore1(),
                m.getScore2(),
                m.getStatus(),
                m.getPhase(),
                m.getBracketSide(),
                m.getNextMatch() != null ? m.getNextMatch().getId() : null,
                m.getNextMatchSlot(),
                m.getLoserNextMatch() != null ? m.getLoserNextMatch().getId() : null,
                m.getLoserNextMatchSlot());
    }
}
