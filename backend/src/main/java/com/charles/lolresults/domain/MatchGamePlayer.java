package com.charles.lolresults.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ligne de stats d'un joueur sur une manche : champion joue et K/D/A (facultatifs). L'equipe
 * et le poste sont ceux de la manche, le joueur ayant pu changer d'equipe ou de poste depuis.
 */
@Entity
@Table(name = "match_game_player")
@Getter
@Setter
@NoArgsConstructor
public class MatchGamePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private MatchGame game;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Position position;

    @Column(nullable = false, length = 30)
    private String champion;

    private Integer kills;

    private Integer deaths;

    private Integer assists;
}
