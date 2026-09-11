package com.charles.lolresults.domain;

/**
 * Phase d'un match : saison reguliere (compte dans le classement) ou
 * playoffs/play-in (fait avancer les equipes dans un bracket, ne compte
 * jamais dans le classement).
 */
public enum MatchPhase {
    REGULAR_SEASON,
    PLAYOFFS
}
