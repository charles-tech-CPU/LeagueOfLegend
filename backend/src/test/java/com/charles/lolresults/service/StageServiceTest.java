package com.charles.lolresults.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.charles.lolresults.domain.BestOf;
import com.charles.lolresults.domain.Competition;
import com.charles.lolresults.domain.CompetitionStage;
import com.charles.lolresults.domain.CompetitionType;
import com.charles.lolresults.domain.Match;
import com.charles.lolresults.domain.MatchStatus;
import com.charles.lolresults.domain.StageFormat;
import com.charles.lolresults.domain.Team;
import com.charles.lolresults.dto.StageCreateDto;
import com.charles.lolresults.dto.StageDto;
import com.charles.lolresults.repository.CompetitionGroupRepository;
import com.charles.lolresults.repository.CompetitionRepository;
import com.charles.lolresults.repository.CompetitionStageRepository;
import com.charles.lolresults.repository.MatchRepository;
import com.charles.lolresults.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
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
class StageServiceTest {

    private static final LocalDate START = LocalDate.of(2013, 9, 15);

    @Mock
    private CompetitionStageRepository stageRepository;

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private CompetitionGroupRepository groupRepository;

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private StageService stageService;

    private Competition worlds;
    private final List<Team> teams = new ArrayList<>();
    private final List<Match> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        worlds = new Competition("WORLDS", "Worlds 2013", CompetitionType.INTERNATIONAL_EVENT, null, 2013);
        worlds.setId(1L);
        LongStream.rangeClosed(1, 16).forEach(i -> {
            Team t = new Team("T" + i, "Team " + i, null);
            t.setId(i);
            teams.add(t);
        });
        when(competitionRepository.findById(1L)).thenReturn(Optional.of(worlds));
        when(competitionRepository.findById(99L)).thenReturn(Optional.empty());
        when(teamRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Team> found = new ArrayList<>();
            for (Object id : (Iterable<?>) invocation.getArgument(0)) {
                teams.stream().filter(t -> t.getId().equals(id)).forEach(found::add);
            }
            return found;
        });
        when(stageRepository.findByCompetitionIdOrderByPosition(1L)).thenReturn(List.of());
        when(stageRepository.save(any(CompetitionStage.class))).thenAnswer(invocation -> {
            CompetitionStage stage = invocation.getArgument(0);
            stage.setId(10L);
            return stage;
        });
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match m = invocation.getArgument(0);
            m.setId((long) saved.size() + 1);
            saved.add(m);
            return m;
        });
        when(matchRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Match> matches = invocation.getArgument(0);
            matches.forEach(m -> {
                m.setId((long) saved.size() + 1);
                saved.add(m);
            });
            return matches;
        });
        when(matchRepository.findByStageId(10L)).thenReturn(saved);
    }

    @Test
    void creerDesPoulesGenereLesMatchsEtLesGroupes() {
        StageDto stage = stageService.create(1L, dto(StageFormat.DOUBLE_ROUND_ROBIN, List.of(ids(1, 4), ids(5, 8))));

        assertThat(stage.position()).isEqualTo(1);
        assertThat(stage.teamCount()).isEqualTo(8);
        assertThat(stage.groupCount()).isEqualTo(2);
        assertThat(stage.matchCount()).isEqualTo(24);
        verify(groupRepository).saveAll(anyList());
        assertThat(worlds.getStartDate()).isEqualTo(START);
        assertThat(worlds.getEndDate()).isEqualTo(START.plusDays(5));
    }

    @Test
    void unBracketEstEnregistreDepuisLaFinale() {
        stageService.create(1L, dto(StageFormat.SINGLE_ELIMINATION, List.of(ids(1, 8))));

        // La finale est enregistree en premier : les autres matchs pointent vers des matchs deja sauves.
        assertThat(saved).hasSize(7);
        assertThat(saved.get(0).getRoundLabel()).isEqualTo("Finale");
        for (int i = 0; i < saved.size(); i++) {
            Match next = saved.get(i).getNextMatch();
            if (next != null) {
                assertThat(saved.indexOf(next)).isLessThan(i);
            }
        }
    }

    @Test
    void laRondeSuisseSuivanteAttendQueLaRondeEnCoursSoitJouee() {
        stageService.create(1L, dto(StageFormat.SWISS, List.of(ids(1, 8))));
        CompetitionStage stage = saved.get(0).getStage();
        when(stageRepository.findById(10L)).thenReturn(Optional.of(stage));

        assertThatThrownBy(() -> stageService.nextSwissRound(10L, START.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tous les matchs de la ronde en cours doivent etre joues");

        // Ronde 1 dans l'ordre des tetes de serie (1-5, 2-6...) : la meilleure gagne.
        assertThat(saved).extracting(m -> m.getTeam1().getId()).containsExactly(1L, 2L, 3L, 4L);
        saved.forEach(m -> {
            m.setScore1(1);
            m.setScore2(0);
            m.setStatus(MatchStatus.COMPLETED);
        });
        StageDto afterRound2 = stageService.nextSwissRound(10L, START.plusDays(1));

        assertThat(afterRound2.matchCount()).isEqualTo(8);
        List<Match> round2 = saved.subList(4, 8);
        assertThat(round2).allSatisfy(m -> assertThat(m.getRoundLabel()).isEqualTo("R2"));
        assertThat(round2.get(0).getTeam1().getId()).isEqualTo(1L);
        assertThat(round2.get(0).getTeam2().getId()).isEqualTo(2L);
    }

    @Test
    void uneRondeSuivanteNExisteQuePourUnSwiss() {
        CompetitionStage bracket =
                new CompetitionStage(worlds, 1, "Playoffs", StageFormat.SINGLE_ELIMINATION, BestOf.BO3);
        when(stageRepository.findById(11L)).thenReturn(Optional.of(bracket));

        assertThatThrownBy(() -> stageService.nextSwissRound(11L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La phase Playoffs n'est pas une ronde suisse");
    }

    @Test
    void lesFormatsImpossiblesSontRefuses() {
        StageCreateDto duplicate = dto(StageFormat.ROUND_ROBIN, List.of(List.of(1L, 2L, 1L)));
        StageCreateDto groupedBracket = dto(StageFormat.SINGLE_ELIMINATION, List.of(ids(1, 4), ids(5, 8)));
        StageCreateDto aloneInGroup = dto(StageFormat.ROUND_ROBIN, List.of(ids(1, 4), List.of(5L)));
        StageCreateDto oddSwiss = dto(StageFormat.SWISS, List.of(ids(1, 5)));
        StageCreateDto doubleElimOf6 = dto(StageFormat.DOUBLE_ELIMINATION, List.of(ids(1, 6)));
        StageCreateDto unknownTeam = dto(StageFormat.ROUND_ROBIN, List.of(List.of(1L, 42L)));

        assertThatThrownBy(() -> stageService.create(1L, duplicate))
                .hasMessage("Une equipe ne peut apparaitre qu'une fois dans la phase");
        assertThatThrownBy(() -> stageService.create(1L, groupedBracket))
                .hasMessage("Seules les poules peuvent avoir plusieurs groupes");
        assertThatThrownBy(() -> stageService.create(1L, aloneInGroup))
                .hasMessage("Il faut au moins 2 equipes par groupe");
        assertThatThrownBy(() -> stageService.create(1L, oddSwiss))
                .hasMessage("Une ronde suisse demande un nombre pair d'equipes (au moins 4)");
        assertThatThrownBy(() -> stageService.create(1L, doubleElimOf6))
                .hasMessage("La double elimination demande 4, 8, 16 ou 32 equipes");
        assertThatThrownBy(() -> stageService.create(1L, unknownTeam))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Equipe introuvable : 42");
        assertThatThrownBy(() -> stageService.findByCompetition(99L)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void supprimerUnePhaseSupprimeSesMatchs() {
        stageService.create(1L, dto(StageFormat.SINGLE_ELIMINATION, List.of(ids(1, 4))));
        CompetitionStage stage = saved.get(0).getStage();
        when(stageRepository.findById(10L)).thenReturn(Optional.of(stage));

        stageService.delete(10L);

        assertThat(saved).allSatisfy(m -> assertThat(m.getNextMatch()).isNull());
        verify(matchRepository).deleteAll(saved);
        verify(stageRepository).delete(stage);
    }

    @Test
    void lesPhasesDUneCompetitionSontListeesAvecLeurAvancement() {
        CompetitionStage stage = new CompetitionStage(worlds, 1, "Groupes", StageFormat.ROUND_ROBIN, BestOf.BO1);
        stage.setId(10L);
        when(stageRepository.findByCompetitionIdOrderByPosition(1L)).thenReturn(List.of(stage));
        Match played = new Match();
        played.setStatus(MatchStatus.COMPLETED);
        saved.add(played);
        saved.add(new Match());

        assertThat(stageService.findByCompetition(1L)).singleElement().satisfies(dto -> {
            assertThat(dto.matchCount()).isEqualTo(2);
            assertThat(dto.playedCount()).isEqualTo(1);
        });
    }

    private static StageCreateDto dto(StageFormat format, List<List<Long>> groups) {
        return new StageCreateDto("Phase", format, BestOf.BO1, null, groups, START, 1, null);
    }

    private static List<Long> ids(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }
}
