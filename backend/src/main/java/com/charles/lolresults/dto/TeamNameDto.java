package com.charles.lolresults.dto;

import com.charles.lolresults.domain.TeamName;
import java.time.LocalDate;

public record TeamNameDto(String name, String shortName, LocalDate validFrom, LocalDate validTo) {
    public static TeamNameDto from(TeamName n) {
        return new TeamNameDto(n.getName(), n.getShortName(), n.getValidFrom(), n.getValidTo());
    }
}
