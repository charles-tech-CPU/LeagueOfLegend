package com.charles.lolresults.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Un nom porte par une equipe sur une periode (historique des renommages). */
@Entity
@Table(name = "team_name_history")
@Getter
@Setter
@NoArgsConstructor
public class TeamName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 100)
    private String name;

    /** Code court de l'epoque (ex: "SKT"), nul si inconnu. */
    @Column(name = "short_name", length = 20)
    private String shortName;

    /** Nul : depuis l'origine connue. */
    @Column(name = "valid_from")
    private LocalDate validFrom;

    /** Nul : nom actuel. */
    @Column(name = "valid_to")
    private LocalDate validTo;
}
