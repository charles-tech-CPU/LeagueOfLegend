package com.charles.lolresults.repository;

import com.charles.lolresults.domain.TeamName;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamNameRepository extends JpaRepository<TeamName, Long> {
    List<TeamName> findByTeamId(Long teamId);
}
