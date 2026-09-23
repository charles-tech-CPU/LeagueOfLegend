// Outils partages par les tableaux de matchs editables (calendrier d'une
// competition et page "A venir").

export function formatTime(t) {
  return t ? t.slice(0, 5) : ''
}

export function kickoff(match) {
  return match.date + (match.time ?? '')
}

/** Copie modifiable des champs d'un match, liee aux cellules de MatchEditCells. */
export function toEditForm(match) {
  return {
    team1Id: match.team1Id,
    team2Id: match.team2Id,
    roundLabel: match.roundLabel,
    date: match.date,
    time: formatTime(match.time),
    bestOf: match.bestOf,
    phase: match.phase,
    score1: match.score1,
    score2: match.score2
  }
}

export function canSave(edit) {
  return Boolean(edit.team1Id && edit.team2Id)
}

/**
 * Corps de requete POST/PUT /api/matches : les champs saisis viennent de
 * "edit", le rattachement (competition, groupe, bracket) de "base".
 */
export function toMatchPayload(base, edit) {
  return {
    competitionId: base.competitionId,
    groupId: base.groupId,
    roundLabel: edit.roundLabel,
    date: edit.date,
    time: edit.time || null,
    bestOf: edit.bestOf,
    team1Id: edit.team1Id,
    team2Id: edit.team2Id,
    score1: edit.score1,
    score2: edit.score2,
    phase: edit.phase,
    bracketSide: base.bracketSide,
    nextMatchId: base.nextMatchId,
    nextMatchSlot: base.nextMatchSlot,
    loserNextMatchId: base.loserNextMatchId,
    loserNextMatchSlot: base.loserNextMatchSlot
  }
}
