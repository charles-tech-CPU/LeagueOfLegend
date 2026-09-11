package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sous-groupe au sein d'une competition, ex: "Legend Group" / "Rise Group" en LCK.
 * Une competition sans sous-groupes n'a simplement aucun CompetitionGroup associe :
 * ses matchs de saison reguliere pointent group=null.
 */
@Entity
@Table(name = "competition_group")
@Getter
@Setter
@NoArgsConstructor
public class CompetitionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "competition_id", nullable = false)
    private Competition competition;

    @Column(nullable = false, length = 100)
    private String name;

    public CompetitionGroup(Competition competition, String name) {
        this.competition = competition;
        this.name = name;
    }
}
