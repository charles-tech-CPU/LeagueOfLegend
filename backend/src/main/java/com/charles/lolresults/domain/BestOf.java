package com.charles.lolresults.domain;

/**
 * Format d'une serie (best of). Un BO2 (saisons regulieres des annees 2010) peut
 * finir sur un nul 1-1.
 */
public enum BestOf {
    BO1(1),
    BO2(2),
    BO3(2),
    BO5(3);

    private final int gamesToWin;

    BestOf(int gamesToWin) {
        this.gamesToWin = gamesToWin;
    }

    public int getGamesToWin() {
        return gamesToWin;
    }
}
