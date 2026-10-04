package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une phase d'une competition (ex: "Saison reguliere" en aller-retour Bo1, puis
 * "Playoffs" en elimination simple Bo5). Les matchs de la phase y sont rattaches.
 */
@Entity
@Table(name = "competition_stage")
@Getter
@Setter
@NoArgsConstructor
public class CompetitionStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    /** Ordre de la phase dans la competition (1 = premiere jouee). */
    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StageFormat format;

    /** Nul si la phase melange plusieurs formats de serie. */
    @Enumerated(EnumType.STRING)
    @Column(name = "best_of", length = 10)
    private BestOf bestOf;

    @Column(name = "team_count")
    private Integer teamCount;

    @Column(name = "group_count")
    private Integer groupCount;

    /** Equipes qualifiees pour la phase suivante (par groupe le cas echeant). */
    private Integer advancing;

    public CompetitionStage(Competition competition, Integer position, String name, StageFormat format, BestOf bestOf) {
        this.competition = competition;
        this.position = position;
        this.name = name;
        this.format = format;
        this.bestOf = bestOf;
    }
}
