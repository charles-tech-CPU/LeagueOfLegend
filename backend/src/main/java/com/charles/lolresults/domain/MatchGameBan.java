package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Champion banni par une equipe sur une manche. Nombre libre par equipe, non lie a un
 * joueur (une interdiction de draft ne cible pas forcement le joueur qui l'aurait joue).
 */
@Entity
@Table(name = "match_game_ban")
@Getter
@Setter
@NoArgsConstructor
public class MatchGameBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private MatchGame game;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 30)
    private String champion;
}
