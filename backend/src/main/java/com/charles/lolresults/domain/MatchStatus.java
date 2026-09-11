package com.charles.lolresults.domain;

/**
 * Statut d'un match : programme (pas encore joue, scores nuls)
 * ou joue (scores renseignes).
 */
public enum MatchStatus {
    SCHEDULED,
    COMPLETED
}
