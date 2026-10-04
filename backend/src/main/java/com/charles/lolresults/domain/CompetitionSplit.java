package com.charles.lolresults.domain;

/**
 * Split d'une saison de ligue regionale. Un evenement ponctuel (Worlds, MSI, IEM...)
 * n'a pas de split.
 */
public enum CompetitionSplit {
    WINTER,
    SPRING,
    SUMMER
}
