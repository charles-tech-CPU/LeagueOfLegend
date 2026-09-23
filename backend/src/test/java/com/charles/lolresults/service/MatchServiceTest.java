package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchPhase;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.MatchCreateDto;
import com.charles.lolresults.repository.CompetitionGroupRepository;
import com.charles.lolresults.repository.CompetitionRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionGroupRepository groupRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private MatchService matchService;

    private Team g2;
    private Team fnc;
    private Match nextMatch;
    private Match loserNextMatch;

    @BeforeEach
    void setUp() {
        g2 = team(1L, "G2");
        fnc = team(2L, "FNC");
        nextMatch = new Match();
        nextMatch.setId(100L);
        loserNextMatch = new Match();
        loserNextMatch.setId(200L);

        when(competitionRepository.findById(1L)).thenReturn(Optional.of(new Competition()));
        when(teamRepository.findById(1L)).thenReturn(Optional.of(g2));
        when(teamRepository.findById(2L)).thenReturn(Optional.of(fnc));
        when(matchRepository.findById(100L)).thenReturn(Optional.of(nextMatch));
        when(matchRepository.findById(200L)).thenReturn(Optional.of(loserNextMatch));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void unMatchAvecScoreEstCompleteEtFaitAvancerVainqueurEtPerdant() {
        matchService.create(playoffMatch(3, 1));

        assertThat(nextMatch.getTeam1()).isSameAs(g2);
        assertThat(loserNextMatch.getTeam2()).isSameAs(fnc);
    }

    @Test
    void unMatchSansScoreResteAVenirEtLibereLesSlotsSuivants() {
        nextMatch.setTeam1(g2);
        loserNextMatch.setTeam2(fnc);

        matchService.create(playoffMatch(null, null));

        assertThat(nextMatch.getTeam1()).isNull();
        assertThat(loserNextMatch.getTeam2()).isNull();
    }

    @Test
    void unSlotDejaJoueNEstPasLibere() {
        nextMatch.setTeam1(g2);
        nextMatch.setStatus(MatchStatus.COMPLETED);

        matchService.create(playoffMatch(null, null));

        assertThat(nextMatch.getTeam1()).isSameAs(g2);
    }

    @Test
    void miseAJourDUnMatchInconnuLeveUneErreur() {
        when(matchRepository.findById(999L)).thenReturn(Optional.empty());

        MatchCreateDto dto = playoffMatch(2, 0);
        assertThatThrownBy(() -> matchService.update(999L, dto)).isInstanceOf(EntityNotFoundException.class);
    }

    private static MatchCreateDto playoffMatch(Integer score1, Integer score2) {
        return new MatchCreateDto(
                1L,
                null,
                "SF",
                LocalDate.of(2026, 9, 1),
                null,
                BestOf.BO5,
                1L,
                2L,
                score1,
                score2,
                MatchPhase.PLAYOFFS,
                "UPPER",
                100L,
                1,
                200L,
                2);
    }

    private static Team team(Long id, String code) {
        Team team = new Team(code, code, null);
        team.setId(id);
        return team;
    }
}
