package com.charles.lolresults.repository;

import com.charles.lolresults.domain.MatchGame;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchGameRepository extends JpaRepository<MatchGame, Long> {
    List<MatchGame> findByMatchIdOrderByGameNumber(Long matchId);
}
