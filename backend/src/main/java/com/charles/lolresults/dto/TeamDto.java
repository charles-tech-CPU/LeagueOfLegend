package com.charles.lolresults.dto;

import com.charles.lolresults.domain.Team;

public record TeamDto(Long id, String code, String name, String region, boolean hasLogo) {
    public static TeamDto from(Team team) {
        return new TeamDto(team.getId(), team.getCode(), team.getName(), team.getRegion(), team.getLogo() != null);
    }
}
