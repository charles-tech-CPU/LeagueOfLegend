package com.charles.lolresults.domain;

/** Format d'une phase de competition. */
public enum StageFormat {
    /** Poule(s) ou chaque equipe affronte une fois chacune des autres. */
    ROUND_ROBIN,
    /** Poule(s) en aller-retour. */
    DOUBLE_ROUND_ROBIN,
    /** Ronde suisse : a chaque ronde, les equipes au meme bilan s'affrontent. */
    SWISS,
    SINGLE_ELIMINATION,
    /** Bracket des vainqueurs + bracket des perdants + grande finale. */
    DOUBLE_ELIMINATION,
    /** Tout format non reconnu (repris tel quel d'un import). */
    OTHER
}
