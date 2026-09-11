package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une competition = une ligue sur une saison donnee (ex: "LEC 2026")
 * ou un evenement international (ex: "MSI 2026").
 */
@Entity
@Table(name = "competition")
@Getter
@Setter
@NoArgsConstructor
public class Competition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Code court, ex: "LEC", "LCS", "MSI". */
    @Column(nullable = false, length = 30)
    private String code;

    /** Nom affichable, ex: "LEC 2026". */
    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CompetitionType type;

    /** Region couverte le cas echeant (EMEA, NA, KR, CN, BR...). Null pour un evenement mondial. */
    @Column(length = 50)
    private String region;

    @Column(nullable = false)
    private Integer season;

    public Competition(String code, String name, CompetitionType type, String region, Integer season) {
        this.code = code;
        this.name = name;
        this.type = type;
        this.region = region;
        this.season = season;
    }
}
