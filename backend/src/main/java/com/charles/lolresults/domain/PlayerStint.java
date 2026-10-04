package com.charles.lolresults.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un passage d'un joueur dans une equipe, a un poste, sur une periode. Le passage en cours
 * (endDate nul) correspond a l'equipe et au poste actuels du joueur (Player.team, Player.position).
 * Un role swap dans la meme equipe se traduit par deux passages consecutifs.
 */
@Entity
@Table(name = "player_stint")
@Getter
@Setter
@NoArgsConstructor
public class PlayerStint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** Poste occupe pendant le passage. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Position position;

    /** Date d'arrivee, nulle si inconnue. */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** Dernier jour dans l'equipe (inclus), nul tant que le passage est en cours. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Passage au poste actuel du joueur. */
    public PlayerStint(Player player, Team team, LocalDate startDate, LocalDate endDate) {
        this(player, team, player.getPosition(), startDate, endDate);
    }

    public PlayerStint(Player player, Team team, Position position, LocalDate startDate, LocalDate endDate) {
        this.player = player;
        this.team = team;
        this.position = position;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean isCurrent() {
        return endDate == null;
    }

    /** Vrai si la date tombe dans le passage (une borne nulle est ouverte). */
    public boolean covers(LocalDate date) {
        return (startDate == null || !date.isBefore(startDate)) && (endDate == null || !date.isAfter(endDate));
    }

    /** Vrai si les deux periodes ont au moins un jour en commun (une borne nulle est ouverte). */
    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        boolean startsBeforeOtherEnds = startDate == null || otherEnd == null || !startDate.isAfter(otherEnd);
        boolean otherStartsBeforeThisEnds = otherStart == null || endDate == null || !otherStart.isAfter(endDate);
        return startsBeforeOtherEnds && otherStartsBeforeThisEnds;
    }
}
