package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un match (une serie Bo1/Bo3/Bo5) entre deux equipes, dans une competition,
 * eventuellement rattache a un sous-groupe. Peut exister avant d'etre joue
 * (statut SCHEDULED, scores nuls) pour servir de calendrier.
 *
 * Les classements et le tableau tete-a-tete ne sont PAS stockes ici : ils sont
 * recalcules a la volee a partir des matchs COMPLETED (voir StandingsService).
 */
@Entity
@Table(name = "match")
@Getter
@Setter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @ManyToOne
    @JoinColumn(name = "competition_group_id")
    private CompetitionGroup group;

    /**
     * Libelle libre du round/de la phase : "W1".."W13" pour les semaines de saison
     * reguliere, ou "R1", "QF", "SF", "F", "Losers Bracket R1"... pour les playoffs.
     * Choix delibere de ne pas modeliser un arbre de bracket en v1.
     */
    @Column(name = "round_label", nullable = false, length = 50)
    private String roundLabel;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime time;

    @Enumerated(EnumType.STRING)
    @Column(name = "best_of", nullable = false, length = 10)
    private BestOf bestOf;

    /** Peut etre nul : equipe pas encore connue, en attente du vainqueur d'un match precedent du bracket. */
    @ManyToOne
    @JoinColumn(name = "team1_id")
    private Team team1;

    /** Peut etre nul : equipe pas encore connue, en attente du vainqueur d'un match precedent du bracket. */
    @ManyToOne
    @JoinColumn(name = "team2_id")
    private Team team2;

    /** Nombre de manches (games) gagnees par team1 dans la serie. Null tant que non joue. */
    @Column(name = "score1")
    private Integer score1;

    /** Nombre de manches (games) gagnees par team2 dans la serie. Null tant que non joue. */
    @Column(name = "score2")
    private Integer score2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.SCHEDULED;

    /** Saison reguliere (compte dans le classement) ou playoffs (fait avancer dans le bracket). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchPhase phase = MatchPhase.REGULAR_SEASON;

    /** Cote du bracket : "UPPER", "LOWER", "PLAY_IN", "GROUP"... libre, pertinent seulement en PLAYOFFS. */
    @Column(name = "bracket_side", length = 30)
    private String bracketSide;

    /** Match du bracket ou le vainqueur de ce match est insere une fois complete. */
    @ManyToOne
    @JoinColumn(name = "next_match_id")
    private Match nextMatch;

    /** Slot (1 = team1, 2 = team2) ou inserer le vainqueur dans nextMatch. */
    @Column(name = "next_match_slot")
    private Integer nextMatchSlot;

    /** Match du bracket (branche perdants) ou le perdant de ce match est insere une fois complete. */
    @ManyToOne
    @JoinColumn(name = "loser_next_match_id")
    private Match loserNextMatch;

    /** Slot (1 = team1, 2 = team2) ou inserer le perdant dans loserNextMatch. */
    @Column(name = "loser_next_match_slot")
    private Integer loserNextMatchSlot;
}
