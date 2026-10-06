package com.charles.lolresults.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Une manche (game) d'une serie, saisie au cas par cas : la plupart des matchs n'en ont
 * aucune. Vainqueur, MVP et stats des joueurs sont tous facultatifs.
 */
@Entity
@Table(name = "match_game")
@Getter
@Setter
@NoArgsConstructor
public class MatchGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    /** Numero de la manche dans la serie (1 a 5). */
    @Column(name = "game_number", nullable = false)
    private Integer gameNumber;

    @ManyToOne
    @JoinColumn(name = "winner_team_id")
    private Team winner;

    /** "Player of the Game". */
    @ManyToOne
    @JoinColumn(name = "mvp_player_id")
    private Player mvp;

    @OneToMany(mappedBy = "game", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<MatchGamePlayer> players = new ArrayList<>();

    public MatchGame(Match match, Integer gameNumber) {
        this.match = match;
        this.gameNumber = gameNumber;
    }
}
