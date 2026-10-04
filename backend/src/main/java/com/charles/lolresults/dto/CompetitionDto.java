package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionSplit;
import com.charles.lolresults.domain.CompetitionType;
import java.time.LocalDate;

/** matchCount / playedCount : avancement de la competition (nombre de matchs, dont joues). */
public record CompetitionDto(
        Long id,
        String code,
        String name,
        CompetitionType type,
        String region,
        Integer season,
        CompetitionSplit split,
        LocalDate startDate,
        LocalDate endDate,
        long matchCount,
        long playedCount) {
    public static CompetitionDto from(Competition c) {
        return from(c, 0, 0);
    }

    public static CompetitionDto from(Competition c, long matchCount, long playedCount) {
        return new CompetitionDto(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getType(),
                c.getRegion(),
                c.getSeason(),
                c.getSplit(),
                c.getStartDate(),
                c.getEndDate(),
                matchCount,
                playedCount);
    }
}
