package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un joueur, rattache a l'effectif actuel d'une equipe ou sans equipe (agent libre).
 * Pas d'historique de transferts : seule l'equipe actuelle est connue.
 */
@Entity
@Table(name = "player")
@Getter
@Setter
@NoArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nul si le joueur est sans equipe. */
    @ManyToOne
    @JoinColumn(name = "team_id")
    private Team team;

    /** Nom affiche du joueur, ex: "Caps", "Faker". */
    @Column(nullable = false, length = 50)
    private String pseudo;

    /** Nationalite (facultatif), texte libre, ex: "FR", "KR". */
    @Column(length = 50)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Position position;

    public Player(Team team, String pseudo, String nationality, Position position) {
        this.team = team;
        this.pseudo = pseudo;
        this.nationality = nationality;
        this.position = position;
    }
}
