package com.charles.lolresults.dto;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.CompetitionStage;
import com.charles.lolresults.domain.StageFormat;

public record StageDto(
        Long id,
        Integer position,
        String name,
        StageFormat format,
        BestOf bestOf,
        Integer teamCount,
        Integer groupCount,
        Integer advancing,
        long matchCount,
        long playedCount) {
    public static StageDto from(CompetitionStage s, long matchCount, long playedCount) {
        return new StageDto(
                s.getId(),
                s.getPosition(),
                s.getName(),
                s.getFormat(),
                s.getBestOf(),
                s.getTeamCount(),
                s.getGroupCount(),
                s.getAdvancing(),
                matchCount,
                playedCount);
    }
}
