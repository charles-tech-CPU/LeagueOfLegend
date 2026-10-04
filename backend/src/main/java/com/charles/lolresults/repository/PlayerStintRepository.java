package com.charles.lolresults.repository;

import com.charles.lolresults.domain.PlayerStint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerStintRepository extends JpaRepository<PlayerStint, Long> {

    List<PlayerStint> findByPlayerId(Long playerId);

    Optional<PlayerStint> findByPlayerIdAndEndDateIsNull(Long playerId);
}
