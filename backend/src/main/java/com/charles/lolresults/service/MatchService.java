package com.charles.lolresults.service;

import com.charles.lolresults.domain.*;
import com.charles.lolresults.dto.MatchCreateDto;
import com.charles.lolresults.dto.MatchDto;
import com.charles.lolresults.repository.CompetitionGroupRepository;
import com.charles.lolresults.repository.CompetitionRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class MatchService {

    private final MatchRepository matchRepository;
    private final CompetitionRepository competitionRepository;
    private final CompetitionGroupRepository groupRepository;
    private final TeamRepository teamRepository;

    public MatchService(MatchRepository matchRepository,
                         CompetitionRepository competitionRepository,
                         CompetitionGroupRepository groupRepository,
                         TeamRepository teamRepository) {
        this.matchRepository = matchRepository;
        this.competitionRepository = competitionRepository;
        this.groupRepository = groupRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> findByCompetition(Long competitionId) {
        return matchRepository.findByCompetitionIdOrderByDateAscTimeAsc(competitionId)
                .stream().map(MatchDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MatchDto> findByTeam(Long teamId) {
        return matchRepository.findByTeam1_IdOrTeam2_IdOrderByDateDesc(teamId, teamId)
                .stream().map(MatchDto::from).toList();
    }

    /** Toutes competitions confondues : sert la page "A venir" (saisie de resultats). */
    @Transactional(readOnly = true)
    public List<MatchDto> findByStatus(MatchStatus status) {
        return matchRepository.findByStatusOrderByDateAscTimeAsc(status)
                .stream().map(MatchDto::from).toList();
    }

    public MatchDto create(MatchCreateDto dto) {
        Match match = new Match();
        applyFields(match, dto);
        Match saved = matchRepository.save(match);
        propagateAdvancement(saved);
        return MatchDto.from(saved);
    }

    public MatchDto update(Long id, MatchCreateDto dto) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Match introuvable : " + id));
        applyFields(match, dto);
        Match saved = matchRepository.save(match);
        propagateAdvancement(saved);
        return MatchDto.from(saved);
    }

    public void delete(Long id) {
        matchRepository.deleteById(id);
    }

    private void applyFields(Match match, MatchCreateDto dto) {
        Competition competition = competitionRepository.findById(dto.competitionId())
                .orElseThrow(() -> new EntityNotFoundException("Competition introuvable : " + dto.competitionId()));

        CompetitionGroup group = null;
        if (dto.groupId() != null) {
            group = groupRepository.findById(dto.groupId())
                    .orElseThrow(() -> new EntityNotFoundException("Groupe introuvable : " + dto.groupId()));
        }

        match.setCompetition(competition);
        match.setGroup(group);
        match.setRoundLabel(dto.roundLabel());
        match.setDate(dto.date());
        match.setTime(dto.time());
        match.setBestOf(dto.bestOf());
        match.setTeam1(findTeamOrNull(dto.team1Id()));
        match.setTeam2(findTeamOrNull(dto.team2Id()));
        match.setScore1(dto.score1());
        match.setScore2(dto.score2());
        match.setPhase(dto.phase() != null ? dto.phase() : MatchPhase.REGULAR_SEASON);
        match.setBracketSide(dto.bracketSide());
        match.setNextMatch(findMatchOrNull(dto.nextMatchId()));
        match.setNextMatchSlot(dto.nextMatchSlot());
        match.setLoserNextMatch(findMatchOrNull(dto.loserNextMatchId()));
        match.setLoserNextMatchSlot(dto.loserNextMatchSlot());
        match.setStatus(match.getTeam1() != null && match.getTeam2() != null
                && dto.score1() != null && dto.score2() != null
                ? MatchStatus.COMPLETED
                : MatchStatus.SCHEDULED);
    }

    private Team findTeamOrNull(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Equipe introuvable : " + teamId));
    }

    private Match findMatchOrNull(Long matchId) {
        if (matchId == null) {
            return null;
        }
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Match introuvable : " + matchId));
    }

    /**
     * Fait avancer automatiquement le vainqueur (et le perdant, pour une branche
     * "losers bracket") du match vers les matchs suivants du bracket references
     * par nextMatch/loserNextMatch. Si le score est efface (retour a SCHEDULED),
     * annule cette avance tant que le match suivant n'a pas ete joue lui-meme.
     */
    private void propagateAdvancement(Match match) {
        if (match.getStatus() == MatchStatus.COMPLETED) {
            boolean team1Wins = match.getScore1() > match.getScore2();
            Team winner = team1Wins ? match.getTeam1() : match.getTeam2();
            Team loser = team1Wins ? match.getTeam2() : match.getTeam1();
            insertIntoSlot(match.getNextMatch(), match.getNextMatchSlot(), winner);
            insertIntoSlot(match.getLoserNextMatch(), match.getLoserNextMatchSlot(), loser);
        } else {
            clearSlotIfPending(match.getNextMatch(), match.getNextMatchSlot());
            clearSlotIfPending(match.getLoserNextMatch(), match.getLoserNextMatchSlot());
        }
    }

    private void insertIntoSlot(Match target, Integer slot, Team team) {
        if (target == null || slot == null || team == null) {
            return;
        }
        if (Objects.equals(slot, 1)) {
            target.setTeam1(team);
        } else {
            target.setTeam2(team);
        }
        matchRepository.save(target);
    }

    private void clearSlotIfPending(Match target, Integer slot) {
        if (target == null || slot == null || target.getStatus() == MatchStatus.COMPLETED) {
            return;
        }
        if (Objects.equals(slot, 1)) {
            target.setTeam1(null);
        } else {
            target.setTeam2(null);
        }
        matchRepository.save(target);
    }
}
