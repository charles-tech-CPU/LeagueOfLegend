package com.charles.lolresults.domain;

/**
 * Format d'une serie (best of).
 */
public enum BestOf {
    BO1(1),
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
