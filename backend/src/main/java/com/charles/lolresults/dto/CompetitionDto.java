package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionType;

public record CompetitionDto(Long id, String code, String name, CompetitionType type, String region, Integer season) {
    public static CompetitionDto from(Competition c) {
        return new CompetitionDto(c.getId(), c.getCode(), c.getName(), c.getType(), c.getRegion(), c.getSeason());
    }
}
